package net.zetetic.database;

import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import net.zetetic.database.sqlcipher.SQLiteConnection;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteOpenHelper;

import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;


class MyHelper extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "mydb.db";

    public MyHelper(Context ctx){
        super(ctx, ctx.getDatabasePath(DATABASE_NAME).getAbsolutePath(), "secret", null, 1, 1, null, null, false);
    }
    public void onConfigure(SQLiteDatabase db){
        db.enableWriteAheadLogging();
    }
    public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE t1(x)");
    }
    public void onUpgrade(SQLiteDatabase db, int iOld, int iNew){
    }
}


/**
 * Created by dan on 5/3/17.
 */
@RunWith(AndroidJUnit4.class)
public class SQLCipherWALTestScenario {
    private Context mContext;

    /*
    ** Test if the database at path is encrypted or not. The db
    ** is assumed to be encrypted if the first 6 bytes are anything
    ** other than "SQLite".
    **
    ** If the test reveals that the db is encrypted, return the string
    ** "encrypted". Otherwise, "unencrypted".
    */
    public String db_is_encrypted(String path) throws Exception {
      FileInputStream in = new FileInputStream(mContext.getDatabasePath(path));

      byte[] buffer = new byte[6];
      in.read(buffer, 0, 6);

      String res = "encrypted";
      if( Arrays.equals(buffer, (new String("SQLite")).getBytes()) ){
        res = "unencrypted";
      }
      return res;
    }

    @Before
    public void setup() throws Exception {

        System.loadLibrary("sqlcipher");

        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        // delete any existing database
        File databaseFile = mContext.getDatabasePath(MyHelper.DATABASE_NAME);
        databaseFile.mkdirs();
        if (databaseFile.exists()) {
            databaseFile.delete();
        }
    }

    @Test
    public void testEncryptedWalMode() throws Exception {
        // create database
        final MyHelper helper = new MyHelper(mContext);
        helper.getWritableDatabase();

        // verify that WAL journal mode is set
        final Cursor pragmaCursor = helper.getWritableDatabase().rawQuery("PRAGMA journal_mode", null);
        pragmaCursor.moveToFirst();
        Assert.assertEquals("wal", pragmaCursor.getString(pragmaCursor.getColumnIndex("journal_mode")));
        pragmaCursor.close();

        // start long running transaction
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                helper.getWritableDatabase().beginTransactionNonExclusive();

                // simulate long insert
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                helper.getWritableDatabase().setTransactionSuccessful();
                helper.getWritableDatabase().endTransaction();
            }
        });

        // wait a short time until the long transaction starts
        Thread.sleep(300);

        long startTime = System.currentTimeMillis();

        //try to read something from the database while the slow transaction is running
        helper.getWritableDatabase().execSQL("SELECT * FROM t1");

        //verify that the operation didn't wait until the 3000ms long operation finished
        if (System.currentTimeMillis() - startTime > 3000) {
            throw new Exception("WAL mode isn't working corectly - read operation was blocked");
        }

        if( SQLiteConnection.hasCodec() ){
          Assert.assertEquals("encrypted", db_is_encrypted(MyHelper.DATABASE_NAME));
        } else {
          Assert.assertEquals("unencrypted", db_is_encrypted(MyHelper.DATABASE_NAME));
        }
    }
}
