# SQLCipher for Android

SQLCipher for Android provides a library replacement for `android.database.sqlite` on the Android platform for use on [SQLCipher](https://github.com/sqlcipher/sqlcipher) databases. This library is based on the upstream [Android Bindings](https://www.sqlite.org/android/doc/trunk/www/index.wiki) project and aims to be a long-term replacement for the original [SQLCipher for Android](https://github.com/sqlcipher/android-database-sqlcipher) library.

***N.B.*** This library is currently distributed in source-only format at this time, and requires [other external dependencies](#external-dependencies) to build. Community edition AAR artifacts will be distributed eventually.

### Compatibility

SQLCipher for Android supports Android API 16 and up on `armeabi-v7a`, `x86`, `x86_64`, and `arm64_v8a` architectures.

### Contributions

We welcome contributions, to contribute to SQLCipher for Android, a [contributor agreement](https://www.zetetic.net/contributions/) needs to be submitted. All submissions should be based on the `master` branch.


### Application Integration

Add a local reference to the local library and dependency:

```
implementation files('libs/sqlcipher-android-4.5.2-release.aar')
implementation 'androidx.sqlite:sqlite:2.2.0'
```

or source a Community edition build from Maven Central:

```
implementation 'net.zetetic:sqlcipher-android:4.5.2@aar'
implementation 'androidx.sqlite:sqlite:2.2.0'
```

```
import net.zetetic.database.sqlcipher.SQLiteDatabase;

System.loadLibrary("sqlcipher");
SQLiteDatabase database = SQLiteDatabase.openOrCreateDatabase(databaseFile, password, null, null, null);
```

### Pre/Post Key Operations

To perform operations on the database instance immediately before or after the keying operation is performed, provide a `SQLiteDatabaseHook` instance when creating your database connection:

```
SQLiteDatabaseHook hook = new SQLiteDatabaseHook() {
      public void preKey(SQLiteConnection connection) { }
      public void postKey(SQLiteConnection connection) { }
    };
```

### Building 

## Android NDK

Currently, SQLCipher for Android uses NDK version "23.0.7599858".

## <a name="external-dependencies" />External dependencies:

This repository is not batteries-included. Specifically, you will need to build `libcrypto.a`, the static library from OpenSSL using the NDK for the [supported platforms](#compatibility), and bundle the top-level `include` folder from OpenSSL. Additionally, you will need to build a SQLCipher amalgamation. These files will need to be placed in the following locations:

```
<project-root>/sqlcipher/src/main/jni/sqlcipher/android-libs/armeabi-v7a/libcrypto.a
<project-root>/sqlcipher/src/main/jni/sqlcipher/android-libs/x86/libcrypto.a
<project-root>/sqlcipher/src/main/jni/sqlcipher/android-libs/x86_64/libcrypto.a
<project-root>/sqlcipher/src/main/jni/sqlcipher/android-libs/arm64_v8a/libcrypto.a
<project-root>/sqlcipher/src/main/jni/sqlcipher/android-libs/include/
<project-root>/sqlcipher/src/main/jni/sqlcipher/sqlite3.c
<project-root>/sqlcipher/src/main/jni/sqlcipher/sqlite3.h
```

To build the AAR package, either build directly within Android Studio, or from the command line:

```
./gradlew assembleRelease
```

