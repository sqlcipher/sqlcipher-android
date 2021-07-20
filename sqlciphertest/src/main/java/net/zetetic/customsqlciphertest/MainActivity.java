package net.zetetic.customsqlciphertest;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import org.json.JSONObject;
import org.sqlite.customsqlitetest.R;

import net.zetetic.database.DatabaseErrorHandler;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteStatement;
import net.zetetic.database.sqlcipher.SQLiteDatabaseCorruptException;
import net.zetetic.database.sqlcipher.SQLiteOpenHelper;

import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;


class DoNotDeleteErrorHandler implements DatabaseErrorHandler {
    private static final String TAG = "DoNotDeleteErrorHandler";
    public void onCorruption(SQLiteDatabase dbObj) {
        Log.e(TAG, "Corruption reported by sqlite on database: " + dbObj.getPath());
    }
}

public class MainActivity extends AppCompatActivity {

    private TextView myTV;          /* Text view widget */
    private int myNTest;            /* Number of tests attempted */
    private int myNErr;             /* Number of tests failed */

    File DB_PATH;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        myTV = (TextView)findViewById(R.id.tv_widget);
    }

    public void report_version(){
        SQLiteDatabase db = null;
        SQLiteStatement st;
        String res;

        db = SQLiteDatabase.openOrCreateDatabase(":memory:", null);
        st = db.compileStatement("SELECT sqlite_version()");
        res = st.simpleQueryForString();

        myTV.append("SQLite version " + res + "\n\n");
        db.close();
    }

    public void test_warning(String name, String warning){
        myTV.append("WARNING:" + name + ": " + warning + "\n");
    }

    public void test_result(String name, String res, String expected, long t0){
        long tot = (System.nanoTime() - t0) / 1000000;
        myTV.append(name + "... ");
        myNTest++;

        if( res.equals(expected) ){
            myTV.append("ok (" + tot + "ms)\n");
        } else {
            myNErr++;
            myTV.append("FAILED\n");
            myTV.append("   res=     \"" + res + "\"\n");
            myTV.append("   expected=\"" + expected + "\"\n");
        }
    }

    /*
    ** Test if the database at DB_PATH is encrypted or not. The db
    ** is assumed to be encrypted if the first 6 bytes are anything
    ** other than "SQLite".
    **
    ** If the test reveals that the db is encrypted, return the string
    ** "encrypted". Otherwise, "unencrypted".
    */
    public String db_is_encrypted() throws Exception {
        FileInputStream in = new FileInputStream(DB_PATH);

        byte[] buffer = new byte[6];
        in.read(buffer, 0, 6);

        String res = "encrypted";
        if( Arrays.equals(buffer, (new String("SQLite")).getBytes()) ){
            res = "unencrypted";
        }
        return res;
    }

    /*
    ** Test that a database connection may be accessed from a second thread.
    */
    public void thread_test_1(){
        SQLiteDatabase.deleteDatabase(DB_PATH);
        final SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(DB_PATH, null);

        String db_path2 = DB_PATH.toString() + "2";

        db.execSQL("CREATE TABLE t1(x, y)");
        db.execSQL("INSERT INTO t1 VALUES (1, 2), (3, 4)");

        Thread t = new Thread( new Runnable() {
            public void run() {
                final long t0 = System.nanoTime();
                SQLiteStatement st = db.compileStatement("SELECT sum(x+y) FROM t1");
                String res = st.simpleQueryForString();
                test_result("thread_test_1", res, "10", t0);
            }
        });

        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
        }
        db.close();
    }

    /*
    ** Test that a database connection may be accessed from a second thread.
    */
    public void thread_test_2(){
        final long t0 = System.nanoTime();
        SQLiteDatabase.deleteDatabase(DB_PATH);
        final SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(DB_PATH, null);

        db.execSQL("CREATE TABLE t1(x, y)");
        db.execSQL("INSERT INTO t1 VALUES (1, 2), (3, 4)");

        db.enableWriteAheadLogging();
        db.beginTransactionNonExclusive();
        db.execSQL("INSERT INTO t1 VALUES (5, 6)");

        Thread t = new Thread( new Runnable() {
            public void run() {
                SQLiteStatement st = db.compileStatement("SELECT sum(x+y) FROM t1");
                String res = st.simpleQueryForString();
            }
        });

        t.start();
        String res = "concurrent";

        int i;
        for(i=0; i<20 && t.isAlive(); i++){
            try { Thread.sleep(100); } catch(InterruptedException e) {}
        }
        if( t.isAlive() ){ res = "blocked"; }

        db.endTransaction();
        try { t.join(); } catch(InterruptedException e) {}
        test_result("thread_test_2", res, "concurrent", t0);
        db.close();
    }

    /*
    ** Use a Cursor to loop through the results of a SELECT query.
    */
    public void csr_test_2() throws Exception {
        final long t0 = System.nanoTime();
        SQLiteDatabase.deleteDatabase(DB_PATH);
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(DB_PATH, null);
        String res = "";
        String expect = "";
        int i;
        int nRow = 0;

        db.execSQL("CREATE TABLE t1(x)");
        db.execSQL("BEGIN");
        for(i=0; i<1000; i++){
            db.execSQL("INSERT INTO t1 VALUES ('one'), ('two'), ('three')");
            expect += ".one.two.three";
        }
        db.execSQL("COMMIT");
        Cursor c = db.rawQuery("SELECT x FROM t1", null);
        if( c!=null ){
            boolean bRes;
            for(bRes=c.moveToFirst(); bRes; bRes=c.moveToNext()){
                String x = c.getString(0);
                res = res + "." + x;
            }
        }else{
            test_warning("csr_test_1", "c==NULL");
        }
        test_result("csr_test_2.1", res, expect, t0);

        final long t1 = System.nanoTime();
        db.execSQL("BEGIN");
        for(i=0; i<1000; i++){
            db.execSQL("INSERT INTO t1 VALUES (X'123456'), (X'789ABC'), (X'DEF012')");
            db.execSQL("INSERT INTO t1 VALUES (45), (46), (47)");
            db.execSQL("INSERT INTO t1 VALUES (8.1), (8.2), (8.3)");
            db.execSQL("INSERT INTO t1 VALUES (NULL), (NULL), (NULL)");
        }
        db.execSQL("COMMIT");

        c = db.rawQuery("SELECT x FROM t1", null);
        if( c!=null ){
            boolean bRes;
            for(bRes=c.moveToFirst(); bRes; bRes=c.moveToNext()) nRow++;
        }else{
            test_warning("csr_test_1", "c==NULL");
        }
        test_result("csr_test_2.2", "" + nRow, "15000", t1);

        db.close();
    }

    public String string_from_t1_x(SQLiteDatabase db){
        String res = "";

        Cursor c = db.rawQuery("SELECT x FROM t1", null);
        boolean bRes;
        for(bRes=c.moveToFirst(); bRes; bRes=c.moveToNext()){
            String x = c.getString(0);
            res = res + "." + x;
        }

        return res;
    }
    public void csr_test_1() throws Exception {
        final long t0 = System.nanoTime();
        SQLiteDatabase.deleteDatabase(DB_PATH);
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(DB_PATH, null);
        String res = "";

        db.execSQL("CREATE TABLE t1(x)");
        db.execSQL("INSERT INTO t1 VALUES ('one'), ('two'), ('three')");

        res = string_from_t1_x(db);
        test_result("csr_test_1.1", res, ".one.two.three", t0);
        final long t1 = System.nanoTime();

        db.close();
        test_result("csr_test_1.2", db_is_encrypted(), "unencrypted", t1);
    }

    public void stmt_jrnl_test_1() throws Exception {
        final long t0 = System.nanoTime();
        SQLiteDatabase.deleteDatabase(DB_PATH);
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(DB_PATH, null);
        String res = "";

        db.execSQL("CREATE TABLE t1(x, y UNIQUE)");
        db.execSQL("BEGIN");
        db.execSQL("INSERT INTO t1 VALUES(1, 1), (2, 2), (3, 3)");
        db.execSQL("UPDATE t1 SET y=y+3");
        db.execSQL("COMMIT");
        db.close();
        test_result("stmt_jrnl_test_1.1", "did not crash", "did not crash", t0);
    }


    public void supp_char_test_1() throws Exception {
        final long t0 = System.nanoTime();
        SQLiteDatabase.deleteDatabase(DB_PATH);
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(DB_PATH, null);
        String res = "";
        String smiley = new String( Character.toChars(0x10000) );

        db.execSQL("CREATE TABLE t1(x)");
        db.execSQL("INSERT INTO t1 VALUES ('a" + smiley + "b')");

        res = string_from_t1_x(db);

        test_result("supp_char_test1." + smiley, res, ".a" + smiley + "b", t0);

        db.close();
    }

    /*
    ** If this is a SEE build, check that encrypted databases work.
    */
    public void see_test_1() throws Exception {
        final long t0 = System.nanoTime();
        if( !SQLiteDatabase.hasCodec() ) return;

        SQLiteDatabase.deleteDatabase(DB_PATH);
        String res = "";

        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(DB_PATH, null);
        db.execSQL("PRAGMA key = 'secretkey'");

        db.execSQL("CREATE TABLE t1(x)");
        db.execSQL("INSERT INTO t1 VALUES ('one'), ('two'), ('three')");

        res = string_from_t1_x(db);
        test_result("see_test_1.1", res, ".one.two.three", t0);
        final long t1 = System.nanoTime();
        db.close();

        test_result("see_test_1.2", db_is_encrypted(), "encrypted", t1);
        final long t2 = System.nanoTime();

        db = SQLiteDatabase.openOrCreateDatabase(DB_PATH, null);
        db.execSQL("PRAGMA key = 'secretkey'");
        res = string_from_t1_x(db);
        test_result("see_test_1.3", res, ".one.two.three", t2);
        final long t3 = System.nanoTime();
        db.close();

        res = "unencrypted";
        try {
            db = SQLiteDatabase.openOrCreateDatabase(DB_PATH.getPath(), null);
            string_from_t1_x(db);
        } catch ( SQLiteDatabaseCorruptException e ){
            res = "encrypted";
        } finally {
            db.close();
        }
        test_result("see_test_1.4", res, "encrypted", t3);
        final long t4 = System.nanoTime();

        res = "unencrypted";
        try {
            db = SQLiteDatabase.openOrCreateDatabase(DB_PATH.getPath(), null);
            db.execSQL("PRAGMA key = 'otherkey'");
            string_from_t1_x(db);
        } catch ( SQLiteDatabaseCorruptException e ){
            res = "encrypted";
        } finally {
            db.close();
        }
        test_result("see_test_1.5", res, "encrypted", t4);
    }
    class MyHelper extends SQLiteOpenHelper {
        public MyHelper(Context ctx){
            super(ctx, DB_PATH.getPath(), null, 1);
        }
        public void onConfigure(SQLiteDatabase db){
            db.execSQL("PRAGMA key = 'secret'");
        }
        public void onCreate(SQLiteDatabase db){
            db.execSQL("CREATE TABLE t1(x)");
        }
        public void onUpgrade(SQLiteDatabase db, int iOld, int iNew){
        }
    }

    /*
    ** Check that SQLiteOpenHelper works.
    */
    public void helper_test_1() throws Exception {
        final long t0 = System.nanoTime();
        SQLiteDatabase.deleteDatabase(DB_PATH);

        MyHelper helper = new MyHelper(this);
        SQLiteDatabase db = helper.getWritableDatabase();
        db.execSQL("INSERT INTO t1 VALUES ('x'), ('y'), ('z')");

        String res = string_from_t1_x(db);
        test_result("helper.1", res, ".x.y.z", t0);

        helper.close();
    }

    /*
    ** If this is a SEE build, check that SQLiteOpenHelper still works.
    */
    public void see_test_2() throws Exception {
        final long t0 = System.nanoTime();
        if( !SQLiteDatabase.hasCodec() ) return;
        SQLiteDatabase.deleteDatabase(DB_PATH);

        MyHelper helper = new MyHelper(this);
        SQLiteDatabase db = helper.getWritableDatabase();
        db.execSQL("INSERT INTO t1 VALUES ('x'), ('y'), ('z')");

        String res = string_from_t1_x(db);
        test_result("see_test_2.1", res, ".x.y.z", t0);
        final long t1 = System.nanoTime();
        test_result("see_test_2.2", db_is_encrypted(), "encrypted", t1);
        final long t2 = System.nanoTime();

        helper.close();
        helper = new MyHelper(this);
        db = helper.getReadableDatabase();
        test_result("see_test_2.3", res, ".x.y.z", t2);
        final long t3 = System.nanoTime();

        db = helper.getWritableDatabase();
        test_result("see_test_2.4", res, ".x.y.z", t3);
        final long t4 = System.nanoTime();

        test_result("see_test_2.5", db_is_encrypted(), "encrypted", t4);
        db.close();
    }

    private static boolean mLibIsLoaded = false;
    private static void loadLibrary() {
        if (!mLibIsLoaded) {
            System.loadLibrary("sqlcipher");
            mLibIsLoaded = true;
        }
    }

    public void run_the_tests(View view){
        myTV.setText("");
        view.post(new Runnable() {
            @Override
            public void run() {
                run_the_tests_really();
            }
        });
    }

    public void run_the_tests_really(){
        loadLibrary();
        DB_PATH = getApplicationContext().getDatabasePath("test.db");
        DB_PATH.mkdirs();

        myTV.setText("");
        myNErr = 0;
        myNTest = 0;

        try {
            report_version();
            run_ds_test();
            helper_test_1();
            supp_char_test_1();
            csr_test_1();
            csr_test_2();
            thread_test_1();
            thread_test_2();
            see_test_1();
            see_test_2();
            stmt_jrnl_test_1();
            json_test_1();

            myTV.append("\n" + myNErr + " errors from " + myNTest + " tests\n");
        } catch(Exception e) {
            myTV.append("Exception: " + e.toString() + "\n");
            myTV.append(android.util.Log.getStackTraceString(e) + "\n");
        }
    }

    private void printPragma(SQLiteDatabase db) {
      Cursor cursor = db.rawQuery("SELECT * FROM pragma_compile_options;", null);
      cursor.moveToFirst();
      do {
        Log.e("ds", cursor.getString(0));
      } while(cursor.moveToNext());
    }

    private void run_ds_test() throws Exception {
      File file = getApplicationContext().getDatabasePath("dsstest.db");
      file.delete();
      file.createNewFile();

      Uri.Builder builder = new Uri.Builder();
      builder.scheme("file");
      builder.path(file.toString());
      builder.appendQueryParameter("zv","zlib");
      builder.appendQueryParameter("password","datascan");
      Uri uri = builder.build();
      Log.e("ds", "uri is " + uri.toString());

      Log.e("ds", "file uri is " + new File(uri.toString()).getPath());
      SQLiteDatabase sqLiteDatabase = SQLiteDatabase.openOrCreateDatabase(new File(uri.toString()), null);
      printPragma(sqLiteDatabase);
        myTV.append("ds_test ok\n");
        sqLiteDatabase.close();
    }


    public void json_test_1() throws Exception {
        SQLiteDatabase.deleteDatabase(DB_PATH);
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(DB_PATH, null);
        final long t0 = System.nanoTime();
        db.beginTransaction();
        String res = "";

        db.execSQL("CREATE TABLE t1(x, y)");
        JSONObject json = new JSONObject();
        json.put("Foo", 1);
        json.put("Bar", "Gum");
        db.execSQL("INSERT INTO t1 VALUES (json('" + json.toString() + "'), 1)");
        final String r1 = json.toString();
        json.put("Foo", 2);
        json.put("Bar", "Goo");
        final String r2 = json.toString();
        db.execSQL("INSERT INTO t1 VALUES (json('" + json.toString() + "'), 2)");
        json.put("Foo", 11);
        json.put("Bar", "Zoo");
        db.execSQL("INSERT INTO t1 VALUES (json('" + json.toString() + "'), 11)");

        SQLiteStatement s = db.compileStatement("Select json_extract(x, '$.Foo') from t1 where y = 1");
        res = s.simpleQueryForString();
        db.setTransactionSuccessful();
        db.endTransaction();
        test_result("json_test_1.1", res, "1", t0);

        db.beginTransaction();
        final long t1 = System.nanoTime();
        s.close();

        s = db.compileStatement("Select json_extract(x, '$.Bar') from t1 where y = 1");
        res = s.simpleQueryForString();
        db.setTransactionSuccessful();
        db.endTransaction();
        test_result("json_test_1.2", res, "Gum", t1);
        db.beginTransaction();
        final long t2 = System.nanoTime();
        s.close();

        db.execSQL("Create Unique Index t1_foo on t1(json_extract(x, '$.Foo'))");
        db.execSQL("Create Unique Index t1_bar on t1(json_extract(x, '$.Bar'))");

        s = db.compileStatement("Select x from t1 where json_extract(x, '$.Foo') > 1 order by json_extract(x, '$.Foo') limit 1");
        res = s.simpleQueryForString();
        db.setTransactionSuccessful();
        db.endTransaction();
        test_result("json_test_1.3", res, r2, t2);

        s.close();

        db.close();
    }
}
