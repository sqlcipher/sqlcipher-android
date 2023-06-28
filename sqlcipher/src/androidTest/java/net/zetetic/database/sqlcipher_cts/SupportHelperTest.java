package net.zetetic.database.sqlcipher_cts;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import net.zetetic.database.sqlcipher.SupportHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SupportHelperTest {

    private static final String DATABASE_NAME = "DB-Test.db";
    private static final int CREATION_INDEX = 0;
    private static final int UPGRADE_INDEX = 1;

    @Before
    public void setup() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        System.loadLibrary("sqlcipher");
        for (String databaseName : context.databaseList()) {
            context.deleteDatabase(databaseName);
        }
    }

    @Test
    public void shouldCreateDatabaseNormallyWithInitialVersion() {
        FakeCallback callbackWrapper = new FakeCallback(1);

        SupportSQLiteOpenHelper.Configuration configuration = createConfiguration(callbackWrapper);
        SupportHelper helper = new SupportHelper(configuration, null, null, true);

        helper.getWritableDatabase();
        helper.close();

        assertEquals(1, callbackWrapper.callbackCount[CREATION_INDEX]);
        assertEquals(0, callbackWrapper.callbackCount[UPGRADE_INDEX]);
    }

    @Test
    public void shouldRunUpgradeFromVersion1ToVersion2WhenMinSupportedVersionIsProvided() {
        FakeCallback initialCallback = new FakeCallback(1);

        SupportHelper initialHelper = new SupportHelper(createConfiguration(initialCallback), null, null, true);

        initialHelper.getWritableDatabase();
        initialHelper.close();

        assertEquals(1, initialCallback.callbackCount[CREATION_INDEX]);
        assertEquals(0, initialCallback.callbackCount[UPGRADE_INDEX]);

        FakeCallback callbackWrapper = new FakeCallback(2);

        // minSupportedVersion = 1
        SupportHelper helper = new SupportHelper(createConfiguration(callbackWrapper), null, null, true, 1);

        helper.getWritableDatabase();
        helper.close();

        assertEquals(0, callbackWrapper.callbackCount[CREATION_INDEX]);
        assertEquals(1, callbackWrapper.callbackCount[UPGRADE_INDEX]);
    }

    @Test
    public void shouldRunUpgradeFromVersion1ToVersion2() {
        FakeCallback initialCallback = new FakeCallback(1);

        SupportHelper initialHelper = new SupportHelper(createConfiguration(initialCallback), null, null, true);

        initialHelper.getWritableDatabase();
        initialHelper.close();

        assertEquals(1, initialCallback.callbackCount[CREATION_INDEX]);
        assertEquals(0, initialCallback.callbackCount[UPGRADE_INDEX]);

        FakeCallback callbackWrapper = new FakeCallback(2);

        SupportHelper helper = new SupportHelper(createConfiguration(callbackWrapper), null, null, true);

        helper.getWritableDatabase();
        helper.close();

        assertEquals(0, callbackWrapper.callbackCount[CREATION_INDEX]);
        assertEquals(1, callbackWrapper.callbackCount[UPGRADE_INDEX]);
    }

    private SupportSQLiteOpenHelper.Configuration createConfiguration(SupportSQLiteOpenHelper.Callback callback) {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        return SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DATABASE_NAME)
                .callback(callback)
                .build();
    }

    private static class FakeCallback extends SupportSQLiteOpenHelper.Callback {
        public final int[] callbackCount = {0, 0};

        public FakeCallback(int version) {
            super(version);
        }

        SupportSQLiteOpenHelper.Callback callback = new SupportSQLiteOpenHelper.Callback(version) {
            @Override
            public void onCreate(@NonNull SupportSQLiteDatabase db) {
                callbackCount[CREATION_INDEX] += 1;
            }

            @Override
            public void onUpgrade(@NonNull SupportSQLiteDatabase db, int oldVersion, int newVersion) {
                callbackCount[UPGRADE_INDEX] += 1;
            }
        };

        @Override
        @CallSuper
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            callback.onCreate(db);
        }

        @Override
        @CallSuper
        public void onUpgrade(@NonNull SupportSQLiteDatabase db, int oldVersion, int newVersion) {
            callback.onUpgrade(db, oldVersion, newVersion);
        }
    }
}
