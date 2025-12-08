# SQLCipher for Android

SQLCipher for Android provides a library replacement for `android.database.sqlite` on the Android platform for use on [SQLCipher](https://github.com/sqlcipher/sqlcipher) databases. This library is based on the upstream [Android Bindings](https://www.sqlite.org/android/doc/trunk/www/index.wiki) project and aims to be a long-term replacement for the original [SQLCipher for Android](https://github.com/sqlcipher/android-database-sqlcipher) library.

### Compatibility

SQLCipher for Android supports Android API 21 and up on `armeabi-v7a`, `x86`, `x86_64`, and `arm64-v8a` architectures.

### Contributions

We welcome contributions, to contribute to SQLCipher for Android, a [contributor agreement](https://www.zetetic.net/contributions/) needs to be submitted. All submissions should be based on the `master` branch.


### Application Integration

Add a local reference to the local library and dependency:

```groovy
implementation files('libs/sqlcipher-android-4.12.0-release.aar')
implementation 'androidx.sqlite:sqlite:2.2.0'
```

or source a Community edition build from Maven Central:

```groovy
implementation 'net.zetetic:sqlcipher-android:4.12.0@aar'
implementation 'androidx.sqlite:sqlite:2.2.0'
```

```java
import net.zetetic.database.sqlcipher.SQLiteDatabase;

System.loadLibrary("sqlcipher");
SQLiteDatabase database = SQLiteDatabase.openOrCreateDatabase(databaseFile, password, null, null, null);
```

### Pre/Post Key Operations

To perform operations on the database instance immediately before or after the keying operation is performed, provide a `SQLiteDatabaseHook` instance when creating your database connection:

```java
SQLiteDatabaseHook hook = new SQLiteDatabaseHook() {
      public void preKey(SQLiteConnection connection) { }
      public void postKey(SQLiteConnection connection) { }
    };
```

### API Usage

There are two main options for using SQLCipher for Android within an Application:

1. [Using the SQLCipher for Android classes](#sqlcipher-for-android-classes)
2. [Using SQLCipher for Android in conjunction with the Android Room API](#sqlcipher-for-android-room-integration)

In both cases, prior to using any portion of the SQLCipher for Android library, the native SQLCipher core library must be loaded into the running application process. The SQLCipher core library is bundled within the AAR of SQLCipher for Android, however, the developer must load this library explicitly. An example below:

```java
System.loadLibrary("sqlcipher");
```

#### SQLCipher for Android classes

SQLCipher for Android provides two classes for opening and access database files. The `SQLiteDatabase` provides static methods for opening/creating database files and general data access.
Additionally, applications may choose to subclass the `SQLiteOpenHelper` class which provides mechanisms for performing database migrations, as well as general data access.

#### SQLCipher for Android Room Integration

SQLCipher for Android may also integrate with the Room API via the `SupportOpenHelperFactory`, an example is given below:

```java
System.loadLibrary("sqlcipher");
String password = "Password1!";
File databaseFile = context.getDatabasePath("demo.db");
SupportOpenHelperFactory factory = new SupportOpenHelperFactory(password.getBytes(StandardCharsets.UTF_8));
db = Room.databaseBuilder(context, AppDatabase.class, databaseFile.getAbsolutePath())
        .openHelperFactory(factory).build();
```

### Logging

Logging may occur in 3 distinct areas within this library:

1. Within the Java client library
2. Within the JNI interop layer
3. Within SQLCipher core

##### Java Client Logging

By default, logging within the Java client library is routed to Logcat. If you wish to disable this logging entirely, you may utilize
the [`NoopTarget`](sqlcipher/src/main/java/net/zetetic/database/NoopTarget.java) instead:

```java
Logger.setTarget(new NoopTarget());
```

You can instead provide a custom logging target by registering a different target that implements the [`LogTarget`](sqlcipher/src/main/java/net/zetetic/database/LogTarget.java) interface.

##### JNI Interop Layer

There are two different compile-specific options available to alter the logging output from the JNI layer. To remove `INFO`, `DEBUG`, and `VERBOSE` log messages from the JNI layer, include `-DNDEBUG` with CFLAGS; this will allow `WARN` and `ERROR` logs to output to logcat. Alternatively, to exclude all log output from JNI, build the library using `-DSQLCIPHER_OMIT_LOG`.

##### SQLCipher core

To manage the logging produced from SQLCipher core, please review the runtime configurations: [`PRAGMA cipher_log`](https://www.zetetic.net/sqlcipher/sqlcipher-api/#cipher_log),
[`PRAGMA cipher_log_level`](https://www.zetetic.net/sqlcipher/sqlcipher-api/#cipher_log_level), and [`PRAGMA cipher_log_source`](https://www.zetetic.net/sqlcipher/sqlcipher-api/#cipher_log_source).

### Building 

## Android NDK

Currently, SQLCipher for Android uses NDK version "25.2.9519653".

## External dependencies

This repository is not batteries-included. Specifically, you will need to build `libcrypto.a`, the static library from OpenSSL using the NDK for the [supported platforms](#compatibility), and bundle the top-level `include` folder from OpenSSL. Additionally, you will need to build a SQLCipher amalgamation. These files will need to be placed in the following locations:

```
<project-root>/sqlcipher/src/main/jni/sqlcipher/android-libs/armeabi-v7a/libcrypto.a
<project-root>/sqlcipher/src/main/jni/sqlcipher/android-libs/x86/libcrypto.a
<project-root>/sqlcipher/src/main/jni/sqlcipher/android-libs/x86_64/libcrypto.a
<project-root>/sqlcipher/src/main/jni/sqlcipher/android-libs/arm64-v8a/libcrypto.a
<project-root>/sqlcipher/src/main/jni/sqlcipher/android-libs/include/
<project-root>/sqlcipher/src/main/jni/sqlcipher/sqlite3.c
<project-root>/sqlcipher/src/main/jni/sqlcipher/sqlite3.h
```

To build the AAR package, either build directly within Android Studio, or from the command line:

```
./gradlew assembleRelease
```

