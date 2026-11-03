# Cancellation inside native COMMIT

This test-only probe gates the SQLite progress callback after `sqlite3_step(COMMIT)` starts. A second thread cancels the signal, then the callback returns SQLite's cancellation status. The test checks the callback observed cancellation, the managed transaction is inactive, the failed row is absent, and a subsequent transaction commits. The gate changes the test build's progress interval from 4 instructions to 1 because a short COMMIT can complete before the default interval. It does not alter the release artifact or claim that every COMMIT is interruptible.

Use a disposable checkout of this commit with initialized submodules and an Android emulator. From the repository root:

```sh
patch -p1 < verification/native-commit-cancellation/native-probe.patch
cp verification/native-commit-cancellation/NativeCommit{Probe,CancellationTest}.java sqlcipher/src/androidTest/java/net/zetetic/database/sqlcipher/
CCACHE=none ./gradlew :sqlcipher:connectedDebugAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=net.zetetic.database.sqlcipher.NativeCommitCancellationTest'
```

This injects test-only native code. Do not publish or use an artifact built from the instrumented checkout. The normal release and tests do not include the probe.

## Production progress interval

The `default-interval/` variant instead gates SQLite's commit hook while `sqlite3_step(COMMIT)` is running. It leaves the production progress interval at four VM instructions. Apply that variant in a separate disposable checkout and copy its test plus the shared `NativeCommitProbe.java`:

```sh
patch -p1 < verification/native-commit-cancellation/default-interval/native-probe.patch
cp verification/native-commit-cancellation/NativeCommitProbe.java sqlcipher/src/androidTest/java/net/zetetic/database/sqlcipher/
cp verification/native-commit-cancellation/default-interval/NativeCommitCancellationTest.java sqlcipher/src/androidTest/java/net/zetetic/database/sqlcipher/
CCACHE=none ./gradlew :sqlcipher:connectedDebugAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=net.zetetic.database.sqlcipher.NativeCommitCancellationTest'
```

The hook blocks the disposable test database's commit until the second thread cancels. With both upstream and 4.17.0 on API 36 arm64, the observed result was a successful COMMIT and one durable row: this short COMMIT completed before the default progress callback checked cancellation. The test also accepts an `OperationCanceledException`, in which case it asserts the failed row is absent. Both branches require a subsequent successful transaction. The hook and JNI probe are test-only and replace the SQLite commit hook on the disposable test connection.
