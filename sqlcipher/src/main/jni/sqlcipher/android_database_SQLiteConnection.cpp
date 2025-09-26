/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
** Modified to support SQLite extensions by the SQLite developers: 
** sqlite-dev@sqlite.org.
*/

#define LOG_TAG "SQLiteConnection"

#include <jni.h>
#include <JNIHelp.h>
#include "ALog-priv.h"

#include <sys/mman.h>
#include <cstring>
#include <cinttypes>
#include <unistd.h>
#include <cassert>

#include <sqlite3.h>

#include "android_database_SQLiteCommon.h"
#include "CursorWindow.h"
#include <string>

// Set to 1 to use UTF16 storage for localized indexes.
#define UTF16_STORAGE 0

#undef LOG_TAG
#define LOG_TAG SQLITE_LOG_TAG

namespace android {

/* Busy timeout in milliseconds.
 * If another connection (possibly in another process) has the database locked for
 * longer than this amount of time then SQLite will generate a SQLITE_BUSY error.
 * The SQLITE_BUSY error is then raised as a SQLiteDatabaseLockedException.
 *
 * In ordinary usage, busy timeouts are quite rare.  Most databases only ever
 * have a single open connection at a time unless they are using WAL.  When using
 * WAL, a timeout could occur if one connection is busy performing an auto-checkpoint
 * operation.  The busy timeout needs to be long enough to tolerate slow I/O write
 * operations but not so long as to cause the application to hang indefinitely if
 * there is a problem acquiring a database lock.
 */
static const int BUSY_TIMEOUT_MS = 2500;

/* The original code uses AndroidRuntime::getJNIEnv() to obtain a 
** pointer to the VM. This is not available in the NDK, so instead
** the following global variable is set as part of this module's
** JNI_OnLoad method.  */
static JavaVM *gpJavaVM = 0;

static struct {
    jfieldID name;
    jfieldID numArgs;
    jmethodID dispatchCallback;
} gSQLiteCustomFunctionClassInfo;

static struct {
    jclass clazz;
} gStringClassInfo;

struct SQLiteConnection {
    // Open flags.
    // Must be kept in sync with the constants defined in SQLiteDatabase.java.
    enum {
        OPEN_READWRITE          = 0x00000000,
        OPEN_READONLY           = 0x00000001,
        OPEN_READ_MASK          = 0x00000001,
        NO_LOCALIZED_COLLATORS  = 0x00000010,
        CREATE_IF_NECESSARY     = 0x10000000,
    };

    sqlite3* const db;
    const int openFlags;
    std::string path;
    std::string label;

    volatile bool canceled;

    SQLiteConnection(sqlite3* db, int openFlags, const std::string& path, const std::string& label) :
        db(db), openFlags(openFlags), path(path), label(label), canceled(false) { }
};

// Called each time a statement begins execution, when tracing is enabled.
static void sqliteTraceCallback(void *data, const char *sql) {
    auto* connection = static_cast<SQLiteConnection*>(data);
    ALOGV("%s: \"%s\"\n",
          connection->label.c_str(), sql);
}

// Called each time a statement finishes execution, when profiling is enabled.
static void sqliteProfileCallback(void *data, const char *sql, sqlite3_uint64 tm) {
    auto* connection = static_cast<SQLiteConnection*>(data);
    ALOGV("%s: \"%s\" took %0.3f ms\n",
          connection->label.c_str(), sql, tm * 0.000001f);
}

// Called after each SQLite VM instruction when cancelation is enabled.
static int sqliteProgressHandlerCallback(void* data) {
    auto* connection = static_cast<SQLiteConnection*>(data);
    return connection->canceled;
}

/*
** This function is a collation sequence callback equivalent to the built-in
** BINARY sequence. 
**
** Stock Android uses a modified version of sqlite3.c that calls out to a module
** named "sqlite3_android" to add extra built-in collations and functions to
** all database handles. Specifically, collation sequence "LOCALIZED". For now,
** this module does not include sqlite3_android (since it is difficult to build
** with the NDK only). Instead, this function is registered as "LOCALIZED" for all
** new database handles. 
*/
static int coll_localized(
  void *not_used,
  int nKey1, const void *pKey1,
  int nKey2, const void *pKey2
){
  int rc, n;
  n = nKey1<nKey2 ? nKey1 : nKey2;
  rc = memcmp(pKey1, pKey2, n);
  if( rc==0 ){
    rc = nKey1 - nKey2;
  }
  return rc;
}

static jint nativeKey(JNIEnv* env, jclass clazz, jlong connectionPtr, jbyteArray keyArray) {
    int rc = SQLITE_ERROR;
    jsize size = 0;
    jbyte *key = nullptr;
    auto* connection = reinterpret_cast<SQLiteConnection*>(connectionPtr);
    if(connection) {
        ALOGV("Keying connection %p", connection->db);
        key = env->GetByteArrayElements(keyArray, nullptr);
        size = env->GetArrayLength(keyArray);
        rc = sqlite3_key(connection->db, key, size);
    }
    if(key) {
        env->ReleaseByteArrayElements(keyArray, key, JNI_ABORT);
    }
    if (rc != SQLITE_OK) {
        ALOGE("sqlite3_key(%p) failed: %d", connection->db, rc);
        throw_sqlite3_exception_errcode(env, rc, "Could not key db.");
    }
    return rc;
}

    static jint nativeReKey(JNIEnv* env, jclass clazz, jlong connectionPtr, jbyteArray keyArray) {
        int rc = SQLITE_ERROR;
        jsize size = 0;
        jbyte *key = nullptr;
        auto* connection = reinterpret_cast<SQLiteConnection*>(connectionPtr);
        if(connection) {
            ALOGV("ReKeying connection %p", connection->db);
            key = env->GetByteArrayElements(keyArray, nullptr);
            size = env->GetArrayLength(keyArray);
            rc = sqlite3_rekey(connection->db, key, size);
        }
        if(key) {
            env->ReleaseByteArrayElements(keyArray, key, JNI_ABORT);
        }
        if (rc != SQLITE_OK) {
            ALOGE("sqlite3_rekey(%p) failed: %d", connection->db, rc);
            throw_sqlite3_exception_errcode(env, rc, "Could not rekey db.");
        }
        return rc;
    }

static jlong nativeOpen(JNIEnv* env, jclass clazz, jstring pathStr, jint openFlags,
        jstring labelStr, jboolean enableTrace, jboolean enableProfile) {
    int sqliteFlags;
    if (openFlags & SQLiteConnection::CREATE_IF_NECESSARY) {
        sqliteFlags = SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE;
    } else if (openFlags & SQLiteConnection::OPEN_READONLY) {
        sqliteFlags = SQLITE_OPEN_READONLY;
    } else {
        sqliteFlags = SQLITE_OPEN_READWRITE;
    }

    const char* pathChars = env->GetStringUTFChars(pathStr, NULL);
    std::string path(pathChars);
    env->ReleaseStringUTFChars(pathStr, pathChars);

    const char* labelChars = env->GetStringUTFChars(labelStr, NULL);
    std::string label(labelChars);
    env->ReleaseStringUTFChars(labelStr, labelChars);

    sqlite3* db;
    int err = sqlite3_open_v2(path.c_str(), &db, sqliteFlags, NULL);
    if (err != SQLITE_OK) {
        throw_sqlite3_exception_errcode(env, err, "Could not open database");
        return 0;
    }
    err = sqlite3_create_collation(db, "localized", SQLITE_UTF8, 0, coll_localized);
    if (err != SQLITE_OK) {
        throw_sqlite3_exception_errcode(env, err, "Could not register collation");
        sqlite3_close_v2(db);
        return 0;
    }

    // Check that the database is really read/write when that is what we asked for.
    if ((sqliteFlags & SQLITE_OPEN_READWRITE) && sqlite3_db_readonly(db, NULL)) {
        throw_sqlite3_exception(env, db, "Could not open the database in read/write mode.");
        sqlite3_close_v2(db);
        return 0;
    }

    // Set the default busy handler to retry automatically before returning SQLITE_BUSY.
    err = sqlite3_busy_timeout(db, BUSY_TIMEOUT_MS);
    if (err != SQLITE_OK) {
        throw_sqlite3_exception(env, db, "Could not set busy timeout");
        sqlite3_close_v2(db);
        return 0;
    }

    // Enable extension loading
    sqlite3_enable_load_extension(db, 1);

    // Create wrapper object.
    auto* connection = new SQLiteConnection(db, openFlags, path, label);

    // Enable tracing and profiling if requested.
    if (enableTrace) {
        sqlite3_trace(db, &sqliteTraceCallback, connection);
    }
    if (enableProfile) {
        sqlite3_profile(db, &sqliteProfileCallback, connection);
    }

    ALOGV("Opened connection %p with label '%s'", db, label.c_str());
    return reinterpret_cast<jlong>(connection);
}

static void nativeClose(JNIEnv* env, jclass clazz, jlong connectionPtr) {
    auto* connection = reinterpret_cast<SQLiteConnection*>(connectionPtr);

    if (connection) {
        ALOGV("Closing connection %p", connection->db);
        int err = sqlite3_close_v2(connection->db);
        if (err != SQLITE_OK) {
            // This can happen if sub-objects aren't closed first.  Make sure the caller knows.
            ALOGE("sqlite3_close_v2(%p) failed: %d", connection->db, err);
            throw_sqlite3_exception(env, connection->db, "Count not close db.");
            return;
        }

        delete connection;
    }
}

// Called each time a custom function is evaluated.
static void sqliteCustomFunctionCallback(sqlite3_context *context,
        int argc, sqlite3_value **argv) {

    JNIEnv* env = 0;
    gpJavaVM->GetEnv((void**)&env, JNI_VERSION_1_4);

    // Get the callback function object.
    // Create a new local reference to it in case the callback tries to do something
    // dumb like unregister the function (thereby destroying the global ref) while it is running.
    auto functionObjGlobal = reinterpret_cast<jobject>(sqlite3_user_data(context));
    jobject functionObj = env->NewLocalRef(functionObjGlobal);

    jobjectArray argsArray = env->NewObjectArray(argc, gStringClassInfo.clazz, NULL);
    if (argsArray) {
        for (int i = 0; i < argc; i++) {
            const auto* arg = static_cast<const jchar*>(sqlite3_value_text16(argv[i]));
            if (!arg) {
                ALOGW("NULL argument in custom_function_callback.  This should not happen.");
            } else {
                size_t argLen = sqlite3_value_bytes16(argv[i]) / sizeof(jchar);
                jstring argStr = env->NewString(arg, argLen);
                if (!argStr) {
                    goto error; // out of memory error
                }
                env->SetObjectArrayElement(argsArray, i, argStr);
                env->DeleteLocalRef(argStr);
            }
        }

        // TODO: Support functions that return values.
        env->CallVoidMethod(functionObj,
                gSQLiteCustomFunctionClassInfo.dispatchCallback, argsArray);

error:
        env->DeleteLocalRef(argsArray);
    }

    env->DeleteLocalRef(functionObj);

    if (env->ExceptionCheck()) {
        ALOGE("An exception was thrown by custom SQLite function.");
        /* LOGE_EX(env); */
        env->ExceptionClear();
    }
}

// Called when a custom function is destroyed.
static void sqliteCustomFunctionDestructor(void* data) {
    auto functionObjGlobal = reinterpret_cast<jobject>(data);
    JNIEnv* env = 0;
    gpJavaVM->GetEnv((void**)&env, JNI_VERSION_1_4);
    env->DeleteGlobalRef(functionObjGlobal);
}

static void nativeRegisterCustomFunction(JNIEnv* env, jclass clazz, jlong connectionPtr,
        jobject functionObj) {
    auto* connection = reinterpret_cast<SQLiteConnection*>(connectionPtr);

    auto nameStr = jstring(env->GetObjectField(
            functionObj, gSQLiteCustomFunctionClassInfo.name));
    jint numArgs = env->GetIntField(functionObj, gSQLiteCustomFunctionClassInfo.numArgs);

    jobject functionObjGlobal = env->NewGlobalRef(functionObj);

    const char* name = env->GetStringUTFChars(nameStr, NULL);
    int err = sqlite3_create_function_v2(connection->db, name, numArgs, SQLITE_UTF16,
            reinterpret_cast<void*>(functionObjGlobal),
            &sqliteCustomFunctionCallback, NULL, NULL, &sqliteCustomFunctionDestructor);
    env->ReleaseStringUTFChars(nameStr, name);

    if (err != SQLITE_OK) {
        ALOGE("sqlite3_create_function returned %d", err);
        env->DeleteGlobalRef(functionObjGlobal);
        throw_sqlite3_exception(env, connection->db);
        return;
    }
}

static void nativeRegisterLocalizedCollators(JNIEnv* env, jclass clazz, jlong connectionPtr,
        jstring localeStr) {
  /* Localized collators are not supported. */
}

static jlong nativePrepareStatement(JNIEnv* env, jclass clazz, jlong connectionPtr,
        jstring sqlString) {
    auto* connection = reinterpret_cast<SQLiteConnection*>(connectionPtr);

    jsize sqlLength = env->GetStringLength(sqlString);
    const jchar* sql = env->GetStringCritical(sqlString, NULL);
    sqlite3_stmt* statement;
    int err = sqlite3_prepare16_v2(connection->db,
            sql, sqlLength * sizeof(jchar), &statement, NULL);
    env->ReleaseStringCritical(sqlString, sql);

    if (err != SQLITE_OK) {
        // Error messages like 'near ")": syntax error' are not
        // always helpful enough, so construct an error string that
        // includes the query itself.
        const char *query = env->GetStringUTFChars(sqlString, NULL);
        char *message = (char*) malloc(strlen(query) + 50);
        if (message) {
            strcpy(message, ", while compiling: "); // less than 50 chars
            strcat(message, query);
        }
        env->ReleaseStringUTFChars(sqlString, query);
        throw_sqlite3_exception(env, connection->db, message);
        free(message);
        return 0;
    }

    ALOGV("Prepared statement %p on connection %p", statement, connection->db);
    return reinterpret_cast<jlong>(statement);
}

static void nativeFinalizeStatement(JNIEnv* env, jclass clazz, jlong connectionPtr,
        jlong statementPtr) {
    auto* connection = reinterpret_cast<SQLiteConnection*>(connectionPtr);
    auto* statement = reinterpret_cast<sqlite3_stmt*>(statementPtr);

    // We ignore the result of sqlite3_finalize because it is really telling us about
    // whether any errors occurred while executing the statement.  The statement itself
    // is always finalized regardless.
    ALOGV("Finalized statement %p on connection %p", statement, connection->db);
    sqlite3_finalize(statement);
}

static jint nativeGetParameterCount(JNIEnv* env, jclass clazz, jlong connectionPtr,
        jlong statementPtr) {
    auto* statement = reinterpret_cast<sqlite3_stmt*>(statementPtr);
    return sqlite3_bind_parameter_count(statement);
}

static jboolean nativeIsReadOnly(JNIEnv* env, jclass clazz, jlong connectionPtr,
        jlong statementPtr) {
    auto* statement = reinterpret_cast<sqlite3_stmt*>(statementPtr);
    return sqlite3_stmt_readonly(statement) != 0;
}

static jint nativeGetColumnCount(JNIEnv* env, jclass clazz, jlong connectionPtr,
        jlong statementPtr) {
    auto* statement = reinterpret_cast<sqlite3_stmt*>(statementPtr);
    return sqlite3_column_count(statement);
}

static jstring nativeGetColumnName(JNIEnv* env, jclass clazz, jlong connectionPtr,
        jlong statementPtr, jint index) {
    auto* statement = reinterpret_cast<sqlite3_stmt*>(statementPtr);
    const auto* name = static_cast<const jchar*>(sqlite3_column_name16(statement, index));
    if (name) {
        size_t length = 0;
        while (name[length]) {
            length += 1;
        }
        return env->NewString(name, length);
    }
    return nullptr;
}

static void nativeBindNull(JNIEnv* env, jclass clazz, jlong connectionPtr,
        jlong statementPtr, jint index) {
    auto* connection = reinterpret_cast<SQLiteConnection*>(connectionPtr);
    auto* statement = reinterpret_cast<sqlite3_stmt*>(statementPtr);
    int err = sqlite3_bind_null(statement, index);
    if (err != SQLITE_OK) {
        throw_sqlite3_exception(env, connection->db, NULL);
    }
}

static void nativeBindLong(JNIEnv* env, jclass clazz, jlong connectionPtr,
        jlong statementPtr, jint index, jlong value) {
    auto* connection = reinterpret_cast<SQLiteConnection*>(connectionPtr);
    auto* statement = reinterpret_cast<sqlite3_stmt*>(statementPtr);
    int err = sqlite3_bind_int64(statement, index, value);
    if (err != SQLITE_OK) {
        throw_sqlite3_exception(env, connection->db, NULL);
    }
}

static void nativeBindDouble(JNIEnv* env, jclass clazz, jlong connectionPtr,
        jlong statementPtr, jint index, jdouble value) {
    auto* connection = reinterpret_cast<SQLiteConnection*>(connectionPtr);
    auto* statement = reinterpret_cast<sqlite3_stmt*>(statementPtr);
    int err = sqlite3_bind_double(statement, index, value);
    if (err != SQLITE_OK) {
        throw_sqlite3_exception(env, connection->db, NULL);
    }
}

static void nativeBindString(JNIEnv* env, jclass clazz, jlong connectionPtr,
        jlong statementPtr, jint index, jstring valueString) {
    auto* connection = reinterpret_cast<SQLiteConnection*>(connectionPtr);
    auto* statement = reinterpret_cast<sqlite3_stmt*>(statementPtr);
    jsize valueLength = env->GetStringLength(valueString);
    const jchar* value = env->GetStringCritical(valueString, NULL);
    int err = sqlite3_bind_text16(statement, index, value, valueLength * sizeof(jchar),
            SQLITE_TRANSIENT);
    env->ReleaseStringCritical(valueString, value);
    if (err != SQLITE_OK) {
        throw_sqlite3_exception(env, connection->db, NULL);
    }
}

static void nativeBindBlob(JNIEnv* env, jclass clazz, jlong connectionPtr,
        jlong statementPtr, jint index, jbyteArray valueArray) {
    auto* connection = reinterpret_cast<SQLiteConnection*>(connectionPtr);
    auto* statement = reinterpret_cast<sqlite3_stmt*>(statementPtr);
    jsize valueLength = env->GetArrayLength(valueArray);
    auto* value = static_cast<jbyte*>(env->GetPrimitiveArrayCritical(valueArray, NULL));
    int err = sqlite3_bind_blob(statement, index, value, valueLength, SQLITE_TRANSIENT);
    env->ReleasePrimitiveArrayCritical(valueArray, value, JNI_ABORT);
    if (err != SQLITE_OK) {
        throw_sqlite3_exception(env, connection->db, NULL);
    }
}

static void nativeResetStatementAndClearBindings(JNIEnv* env, jclass clazz, jlong connectionPtr,
        jlong statementPtr) {
    auto* connection = reinterpret_cast<SQLiteConnection*>(connectionPtr);
    auto* statement = reinterpret_cast<sqlite3_stmt*>(statementPtr);
    int err = sqlite3_reset(statement);
    if (err == SQLITE_OK) {
        err = sqlite3_clear_bindings(statement);
    }
    if (err != SQLITE_OK) {
        throw_sqlite3_exception(env, connection->db, NULL);
    }
}

static int executeNonQueryRaw(JNIEnv* env, SQLiteConnection* connection, sqlite3_stmt* statement) {
    int err;
    do {
      err = sqlite3_step(statement);
    } while(err == SQLITE_ROW);
    return err;
}

static void nativeExecuteRaw(JNIEnv* env, jclass clazz, jlong connectionPtr,
                              jlong statementPtr) {
    auto* connection = reinterpret_cast<SQLiteConnection*>(connectionPtr);
    auto* statement = reinterpret_cast<sqlite3_stmt*>(statementPtr);
    executeNonQueryRaw(env, connection, statement);
}

static int executeNonQuery(JNIEnv* env, SQLiteConnection* connection, sqlite3_stmt* statement) {
    int err = sqlite3_step(statement);
    if (err == SQLITE_ROW) {
        throw_sqlite3_exception(env,
                "Queries can be performed using SQLiteDatabase query or rawQuery methods only.");
    } else if (err != SQLITE_DONE) {
        throw_sqlite3_exception(env, connection->db);
    }
    return err;
}

static void nativeExecute(JNIEnv* env, jclass clazz, jlong connectionPtr,
        jlong statementPtr) {
    auto* connection = reinterpret_cast<SQLiteConnection*>(connectionPtr);
    auto* statement = reinterpret_cast<sqlite3_stmt*>(statementPtr);
    executeNonQuery(env, connection, statement);
}

static jint nativeExecuteForChangedRowCount(JNIEnv* env, jclass clazz,
        jlong connectionPtr, jlong statementPtr) {
    auto* connection = reinterpret_cast<SQLiteConnection*>(connectionPtr);
    auto* statement = reinterpret_cast<sqlite3_stmt*>(statementPtr);
    int err = executeNonQuery(env, connection, statement);
    return err == SQLITE_DONE ? sqlite3_changes(connection->db) : -1;
}

static jlong nativeExecuteForLastInsertedRowId(JNIEnv* env, jclass clazz,
        jlong connectionPtr, jlong statementPtr) {
    auto* connection = reinterpret_cast<SQLiteConnection*>(connectionPtr);
    auto* statement = reinterpret_cast<sqlite3_stmt*>(statementPtr);
    int err = executeNonQuery(env, connection, statement);
    return err == SQLITE_DONE && sqlite3_changes(connection->db) > 0
            ? sqlite3_last_insert_rowid(connection->db) : -1;
}

static int executeOneRowQuery(JNIEnv* env, SQLiteConnection* connection, sqlite3_stmt* statement) {
    int err = sqlite3_step(statement);
    if (err != SQLITE_ROW) {
        throw_sqlite3_exception(env, connection->db);
    }
    return err;
}

static jlong nativeExecuteForLong(JNIEnv* env, jclass clazz,
        jlong connectionPtr, jlong statementPtr) {
    auto* connection = reinterpret_cast<SQLiteConnection*>(connectionPtr);
    auto* statement = reinterpret_cast<sqlite3_stmt*>(statementPtr);
    int err = executeOneRowQuery(env, connection, statement);
    if (err == SQLITE_ROW && sqlite3_column_count(statement) >= 1) {
        return sqlite3_column_int64(statement, 0);
    }
    return -1;
}

static jstring nativeExecuteForString(JNIEnv* env, jclass clazz,
        jlong connectionPtr, jlong statementPtr) {
    auto* connection = reinterpret_cast<SQLiteConnection*>(connectionPtr);
    auto* statement = reinterpret_cast<sqlite3_stmt*>(statementPtr);
    int err = executeOneRowQuery(env, connection, statement);
    if (err == SQLITE_ROW && sqlite3_column_count(statement) >= 1) {
        const jchar* text = static_cast<const jchar*>(sqlite3_column_text16(statement, 0));
        if (text) {
            size_t length = sqlite3_column_bytes16(statement, 0) / sizeof(jchar);
            return env->NewString(text, length);
        }
    }
    return nullptr;
}

static int createAshmemRegionWithData(JNIEnv* env, const void* data, size_t length) {
    jniThrowIOException(env, -1);
    return -1;
}

static jint nativeExecuteForBlobFileDescriptor(JNIEnv* env, jclass clazz,
        jlong connectionPtr, jlong statementPtr) {
    auto* connection = reinterpret_cast<SQLiteConnection*>(connectionPtr);
    auto* statement = reinterpret_cast<sqlite3_stmt*>(statementPtr);
    int err = executeOneRowQuery(env, connection, statement);
    if (err == SQLITE_ROW && sqlite3_column_count(statement) >= 1) {
        const void* blob = sqlite3_column_blob(statement, 0);
        if (blob) {
            int length = sqlite3_column_bytes(statement, 0);
            if (length >= 0) {
                return createAshmemRegionWithData(env, blob, length);
            }
        }
    }
    return -1;
}

enum CopyRowResult {
    CPR_OK,
    CPR_FULL,
    CPR_ERROR,
};

static CopyRowResult copyRow(JNIEnv* env, CursorWindow* window,
                             sqlite3_stmt* statement, int numColumns, int startPos, int addedRows) {
    // Allocate a new field directory for the row.
    status_t status = window->allocRow();
    if (status) {
        ALOGD("Failed allocating fieldDir at startPos %d row %d, error=%d",
              startPos, addedRows, status);
        return CPR_FULL;
    }

    // Pack the row into the window.
    CopyRowResult result = CPR_OK;
    for (int i = 0; i < numColumns; i++) {
        int type = sqlite3_column_type(statement, i);
        if (type == SQLITE_TEXT) {
            // TEXT data
            const char* text = reinterpret_cast<const char*>(
                    sqlite3_column_text(statement, i));
            // SQLite does not include the NULL terminator in size, but does
            // ensure all strings are NULL terminated, so increase size by
            // one to make sure we store the terminator.
            size_t sizeIncludingNull = sqlite3_column_bytes(statement, i) + 1;
            status = window->putString(addedRows, i, text, sizeIncludingNull);
            if (status) {
                ALOGD("Failed allocating %zu bytes for text at %d,%d, error=%d",
                      sizeIncludingNull, startPos + addedRows, i, status);
                result = CPR_FULL;
                break;
            }
            ALOGD("%d,%d is TEXT with %zu bytes",
                  startPos + addedRows, i, sizeIncludingNull);
        } else if (type == SQLITE_INTEGER) {
            // INTEGER data
            int64_t value = sqlite3_column_int64(statement, i);
            status = window->putLong(addedRows, i, value);
            if (status) {
                ALOGD("Failed allocating space for a long in column %d, error=%d",
                      i, status);
                result = CPR_FULL;
                break;
            }
            ALOGD("%d,%d is INTEGER 0x%016" PRIx64, startPos + addedRows, i, value);
        } else if (type == SQLITE_FLOAT) {
            // FLOAT data
            double value = sqlite3_column_double(statement, i);
            status = window->putDouble(addedRows, i, value);
            if (status) {
                ALOGD("Failed allocating space for a double in column %d, error=%d",
                      i, status);
                result = CPR_FULL;
                break;
            }
            ALOGD("%d,%d is FLOAT %lf", startPos + addedRows, i, value);
        } else if (type == SQLITE_BLOB) {
            // BLOB data
            const void* blob = sqlite3_column_blob(statement, i);
            size_t size = sqlite3_column_bytes(statement, i);
            status = window->putBlob(addedRows, i, blob, size);
            if (status) {
                ALOGD("Failed allocating %zu bytes for blob at %d,%d, error=%d",
                      size, startPos + addedRows, i, status);
                result = CPR_FULL;
                break;
            }
            ALOGD("%d,%d is Blob with %zu bytes",
                  startPos + addedRows, i, size);
        } else if (type == SQLITE_NULL) {
            // NULL field
            status = window->putNull(addedRows, i);
            if (status) {
                ALOGD("Failed allocating space for a null in column %d, error=%d",
                      i, status);
                result = CPR_FULL;
                break;
            }
            ALOGD("%d,%d is NULL", startPos + addedRows, i);
        } else {
            // Unknown data
            ALOGE("Unknown column type when filling database window");
            throw_sqlite3_exception(env, "Unknown column type when filling window");
            result = CPR_ERROR;
            break;
        }
    }

    // Free the last row if if was not successfully copied.
    if (result != CPR_OK) {
        window->freeLastRow();
    }
    return result;
}

static jlong nativeExecuteForCursorWindow(
        JNIEnv* env,
        jclass clazz,
        jlong connectionPtr,
        jlong statementPtr,
        jlong windowPtr,
        jint startPos,
        jint requiredPos,
        jboolean countAllRows){
    auto* connection = reinterpret_cast<SQLiteConnection*>(connectionPtr);
    auto* statement = reinterpret_cast<sqlite3_stmt*>(statementPtr);
    auto* window = reinterpret_cast<CursorWindow*>(windowPtr);
    status_t status = window->clear();
    if (status) {
        throw_sqlite3_exception(env, connection->db, "Failed to clear the cursor window");
        return 0;
    }
    int numColumns = sqlite3_column_count(statement);
    status = window->setNumColumns(numColumns);
    if (status) {
        throw_sqlite3_exception(env, connection->db, "Failed to set the cursor window column count");
        return 0;
    }
    int retryCount = 0;
    int totalRows = 0;
    int addedRows = 0;
    bool windowFull = false;
    bool gotException = false;
    while (!gotException && (!windowFull || countAllRows)) {
        int err = sqlite3_step(statement);
        if (err == SQLITE_ROW) {
            ALOGD("Stepped statement %p to row %d", statement, totalRows);
            retryCount = 0;
            totalRows += 1;

            // Skip the row if the window is full or we haven't reached the start position yet.
            if (startPos >= totalRows || windowFull) {
                continue;
            }

            CopyRowResult cpr = copyRow(env, window, statement, numColumns, startPos, addedRows);
            if (cpr == CPR_FULL && addedRows && startPos + addedRows <= requiredPos) {
                // We filled the window before we got to the one row that we really wanted.
                // Clear the window and start filling it again from here.
                // TODO: Would be nicer if we could progressively replace earlier rows.
                window->clear();
                window->setNumColumns(numColumns);
                startPos += addedRows;
                addedRows = 0;
                cpr = copyRow(env, window, statement, numColumns, startPos, addedRows);
                if(cpr == CPR_FULL){
                    throw_sqlite3_exception(env, "Row too big to fit in CursorWindow");
                }
            }

            if (cpr == CPR_OK) {
                addedRows += 1;
            } else if (cpr == CPR_FULL) {
                windowFull = true;
            } else {
                gotException = true;
            }
        } else if (err == SQLITE_DONE) {
            // All rows processed, bail
            ALOGD("Processed all rows");
            break;
        } else if (err == SQLITE_LOCKED || err == SQLITE_BUSY) {
            // The table is locked, retry
            ALOGD("Database locked, retrying");
            if (retryCount > 50) {
                ALOGE("Bailing on database busy retry");
                throw_sqlite3_exception(env, connection->db, "retrycount exceeded");
                gotException = true;
            } else {
                // Sleep to give the thread holding the lock a chance to finish
                usleep(1000);
                retryCount++;
            }
        } else {
            throw_sqlite3_exception(env, connection->db);
            gotException = true;
        }
    }

    ALOGD("Resetting statement %p after fetching %d rows and adding %d rows"
          "to the window in %zu bytes",
          statement, totalRows, addedRows, window->size() - window->freeSpace());
    sqlite3_reset(statement);

    // Report the total number of rows on request.
    if (startPos > totalRows) {
        ALOGE("startPos %d > actual rows %d", startPos, totalRows);
    }
    jlong result = jlong(startPos) << 32 | jlong(totalRows);
    return result;
}

static jint nativeGetDbLookaside(JNIEnv* env, jobject clazz, jlong connectionPtr) {
    auto* connection = reinterpret_cast<SQLiteConnection*>(connectionPtr);
    int cur = -1;
    int unused;
    sqlite3_db_status(connection->db, SQLITE_DBSTATUS_LOOKASIDE_USED, &cur, &unused, 0);
    return cur;
}

static void nativeCancel(JNIEnv* env, jobject clazz, jlong connectionPtr) {
    auto* connection = reinterpret_cast<SQLiteConnection*>(connectionPtr);
    connection->canceled = true;
}

static void nativeResetCancel(JNIEnv* env, jobject clazz, jlong connectionPtr,
        jboolean cancelable) {
    auto* connection = reinterpret_cast<SQLiteConnection*>(connectionPtr);
    connection->canceled = false;
    if (cancelable) {
        sqlite3_progress_handler(connection->db, 4, sqliteProgressHandlerCallback,
                connection);
    } else {
        sqlite3_progress_handler(connection->db, 0, NULL, NULL);
    }
}

static jboolean nativeHasCodec(JNIEnv* env, jobject clazz){
#ifdef SQLITE_HAS_CODEC
  return true;
#else
  return false;
#endif
}


static JNINativeMethod sMethods[] =
{
    /* name, signature, funcPtr */
    {"nativeKey", "(J[B)I",
            (void*)nativeKey },
    {"nativeReKey", "(J[B)I",
            (void*)nativeReKey },
    {"nativeOpen", "(Ljava/lang/String;ILjava/lang/String;ZZ)J",
            (void*)nativeOpen },
    { "nativeClose", "(J)V",
            (void*)nativeClose },
    { "nativeRegisterCustomFunction", "(JLnet/zetetic/database/sqlcipher/SQLiteCustomFunction;)V",
            (void*)nativeRegisterCustomFunction },
    { "nativeRegisterLocalizedCollators", "(JLjava/lang/String;)V",
            (void*)nativeRegisterLocalizedCollators },
    { "nativePrepareStatement", "(JLjava/lang/String;)J",
            (void*)nativePrepareStatement },
    { "nativeFinalizeStatement", "(JJ)V",
            (void*)nativeFinalizeStatement },
    { "nativeGetParameterCount", "(JJ)I",
            (void*)nativeGetParameterCount },
    { "nativeIsReadOnly", "(JJ)Z",
            (void*)nativeIsReadOnly },
    { "nativeGetColumnCount", "(JJ)I",
            (void*)nativeGetColumnCount },
    { "nativeGetColumnName", "(JJI)Ljava/lang/String;",
            (void*)nativeGetColumnName },
    { "nativeBindNull", "(JJI)V",
            (void*)nativeBindNull },
    { "nativeBindLong", "(JJIJ)V",
            (void*)nativeBindLong },
    { "nativeBindDouble", "(JJID)V",
            (void*)nativeBindDouble },
    { "nativeBindString", "(JJILjava/lang/String;)V",
            (void*)nativeBindString },
    { "nativeBindBlob", "(JJI[B)V",
            (void*)nativeBindBlob },
    { "nativeResetStatementAndClearBindings", "(JJ)V",
            (void*)nativeResetStatementAndClearBindings },
    { "nativeExecuteRaw", "(JJ)V",
            (void*)nativeExecuteRaw },
    { "nativeExecute", "(JJ)V",
            (void*)nativeExecute },
    { "nativeExecuteForLong", "(JJ)J",
            (void*)nativeExecuteForLong },
    { "nativeExecuteForString", "(JJ)Ljava/lang/String;",
            (void*)nativeExecuteForString },
    { "nativeExecuteForBlobFileDescriptor", "(JJ)I",
            (void*)nativeExecuteForBlobFileDescriptor },
    { "nativeExecuteForChangedRowCount", "(JJ)I",
            (void*)nativeExecuteForChangedRowCount },
    { "nativeExecuteForLastInsertedRowId", "(JJ)J",
            (void*)nativeExecuteForLastInsertedRowId },
    { "nativeExecuteForCursorWindow", "(JJJIIZ)J",
            (void*)nativeExecuteForCursorWindow },
    { "nativeGetDbLookaside", "(J)I",
            (void*)nativeGetDbLookaside },
    { "nativeCancel", "(J)V",
            (void*)nativeCancel },
    { "nativeResetCancel", "(JZ)V",
            (void*)nativeResetCancel },

    { "nativeHasCodec", "()Z", (void*)nativeHasCodec },
};

int register_android_database_SQLiteConnection(JNIEnv *env)
{
    jclass clazz;
    FIND_CLASS(clazz, "net/zetetic/database/sqlcipher/SQLiteCustomFunction");

    GET_FIELD_ID(gSQLiteCustomFunctionClassInfo.name, clazz,
            "name", "Ljava/lang/String;");
    GET_FIELD_ID(gSQLiteCustomFunctionClassInfo.numArgs, clazz,
            "numArgs", "I");
    GET_METHOD_ID(gSQLiteCustomFunctionClassInfo.dispatchCallback,
            clazz, "dispatchCallback", "([Ljava/lang/String;)V");

    FIND_CLASS(clazz, "java/lang/String");
    gStringClassInfo.clazz = jclass(env->NewGlobalRef(clazz));

    return jniRegisterNativeMethods(env, 
        "net/zetetic/database/sqlcipher/SQLiteConnection",
        sMethods, NELEM(sMethods)
    );
}

extern int register_android_database_SQLiteGlobal(JNIEnv *env);
extern int register_android_database_SQLiteDebug(JNIEnv *env);
extern int register_android_database_CursorWindow(JNIEnv *env);

} // namespace android

void setEnvarToCacheDirectory(JNIEnv* env, const char *envar) {
    jclass activity = NULL, context = NULL, file = NULL;
    jmethodID getCurrentApp = NULL, getCacheDir = NULL, getAbsolutePath = NULL;
    jobject app = NULL, cache = NULL;
    jstring path = NULL;
    const char *pathUtf8 = NULL, *tmpdir = getenv(envar);

    /* check if SQLCIPHER_TMP is already set externally by the application (i.e. an override), and return immediately if it is */
    if(tmpdir && strlen(tmpdir) > 0) {
      return;
    }

    /* call ActivityThread.currentApplication().getCacheDir().getAbsolutePath() and set it to SQLCIPHER_TMP*/
    if (
       (activity = env->FindClass("android/app/ActivityThread"))
       && (context = env->FindClass("android/content/Context"))
       && (file = env->FindClass("java/io/File"))
       && (getCurrentApp = env->GetStaticMethodID(activity, "currentApplication", "()Landroid/app/Application;"))
       && (getCacheDir = env->GetMethodID(context, "getCacheDir", "()Ljava/io/File;"))
       && (getAbsolutePath = env->GetMethodID(file, "getAbsolutePath", "()Ljava/lang/String;"))
       && (app = env->CallStaticObjectMethod(activity, getCurrentApp))
       && (cache = env->CallObjectMethod(app, getCacheDir))
       && (path = (jstring) env->CallObjectMethod(cache, getAbsolutePath))
       && (pathUtf8 = env->GetStringUTFChars(path, NULL))
    ) {
      setenv(envar, pathUtf8, 1);
    } else {
      ALOGE("%s unable to obtain cache directory from JNIEnv", __func__);
    }

    /* cleanup */
    if(pathUtf8) env->ReleaseStringUTFChars(path, pathUtf8);
    if(path) env->DeleteLocalRef(path);
    if(cache) env->DeleteLocalRef(cache);
    if(app) env->DeleteLocalRef(app);
    if(file) env->DeleteLocalRef(file);
    if(context) env->DeleteLocalRef(context);
    if(activity) env->DeleteLocalRef(activity);
}


extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
  JNIEnv *env = 0;

  android::gpJavaVM = vm;
  vm->GetEnv((void**)&env, JNI_VERSION_1_4);
  setEnvarToCacheDirectory(env, "SQLCIPHER_TMP");
  android::register_android_database_SQLiteConnection(env);
  android::register_android_database_SQLiteDebug(env);
  android::register_android_database_SQLiteGlobal(env);
  android::register_android_database_CursorWindow(env);


  return JNI_VERSION_1_4;
}



