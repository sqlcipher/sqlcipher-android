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

#define LOG_TAG "SQLiteGlobal"

#include <jni.h>
#include <JNIHelp.h>

#include <sqlite3.h>

#include "android_database_SQLiteCommon.h"
#include "ALog-priv.h"

namespace android {

// Limit heap to 8MB for now.  This is 4 times the maximum cursor window
// size, as has been used by the original code in SQLiteDatabase for
// a long time.
static const int SOFT_HEAP_LIMIT = 8 * 1024 * 1024;

#undef LOG_TAG
#define LOG_TAG SQLITE_LOG_TAG

// Called each time a message is logged.
static void sqliteLogCallback(void* data, int err, const char* msg) {
    bool verboseLog = !!data;
    int errType = err & 255;
    if (errType == 0 || errType == SQLITE_CONSTRAINT || errType == SQLITE_SCHEMA
            || errType == SQLITE_NOTICE || err == SQLITE_WARNING_AUTOINDEX) {
        if (verboseLog) {
            ALOGV("(%d) %s\n", err, msg);
        }
    } else if (errType == SQLITE_WARNING) {
        ALOGW("(%d) %s\n", err, msg);
    } else {
        ALOGE("(%d) %s\n", err, msg);
    }
}

// Sets the global SQLite configuration.
// This must be called before any other SQLite functions are called.
static void sqliteInitialize() {
    // Enable multi-threaded mode.  In this mode, SQLite is safe to use by multiple
    // threads as long as no two threads use the same database connection at the same
    // time (which we guarantee in the SQLite database wrappers).
    sqlite3_config(SQLITE_CONFIG_MULTITHREAD);

    // Redirect SQLite log messages to the Android log.
    bool verboseLog = false;
    sqlite3_config(SQLITE_CONFIG_LOG, &sqliteLogCallback, verboseLog ? (void*)1 : NULL);

    // The soft heap limit prevents the page cache allocations from growing
    // beyond the given limit, no matter what the max page cache sizes are
    // set to. The limit does not, as of 3.5.0, affect any other allocations.
    sqlite3_soft_heap_limit(SOFT_HEAP_LIMIT);

    // Initialize SQLite.
    sqlite3_initialize();
}

static jint nativeReleaseMemory(JNIEnv* env, jclass clazz) {
    return sqlite3_release_memory(SOFT_HEAP_LIMIT);
}

static const JNINativeMethod sMethods[] =
{
    /* name, signature, funcPtr */
    { "nativeReleaseMemory", "()I", (void*)nativeReleaseMemory },
};

int register_android_database_SQLiteGlobal(JNIEnv *env)
{
    sqliteInitialize();

    return jniRegisterNativeMethods(env, "net/zetetic/database/sqlcipher/SQLiteGlobal",
            sMethods, NELEM(sMethods));
}

} // namespace android
