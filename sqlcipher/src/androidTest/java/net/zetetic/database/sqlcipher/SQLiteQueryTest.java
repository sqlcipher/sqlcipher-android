/*
 * Copyright (C) 2009 The Android Open Source Project
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

package net.zetetic.database.sqlcipher;


import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import android.os.CancellationSignal;

import androidx.sqlite.db.SupportSQLiteProgram;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class SQLiteQueryTest extends AndroidSQLCipherTestCase {

    private RecordingProgram program;

    @Before
    public void setup(){
         program = new RecordingProgram();
    }

    private SQLiteQuery newQuery(String sql) {
        try {
            var ctor = SQLiteQuery.class.getDeclaredConstructor(
                    SQLiteDatabase.class,
                    String.class,
                    CancellationSignal.class);
            ctor.setAccessible(true);
            return ctor.newInstance(database, sql, new CancellationSignal());
        } catch (Exception ex){
            throw new RuntimeException(ex);
        }
    }

    @Test
    public void shouldReturnExactStatementWithGetSql() {
        var sql = "SELECT ?, ?";
        try (SQLiteQuery query = newQuery(sql)) {
            assertEquals(sql, query.getSql());
        }
    }

    @Test
    public void shouldMatchPlaceholderCountFromGetArgCount() {
        try (SQLiteQuery query = newQuery("SELECT ?, ?, ?")) {
            assertEquals(3, query.getArgCount());
        }
    }

    @Test
    public void shouldReturnZeroFromGetArgCountWhenNoPlaceholders() {
        try (SQLiteQuery query = newQuery("SELECT 1")) {
            assertEquals(0, query.getArgCount());
        }
    }

    @Test
    public void shouldBindLongValue() {
        try (SQLiteQuery query = newQuery("SELECT ?")) {
            query.bindLong(1, 42L);
            query.bindTo(program);
            assertEquals(1, program.calls.size());
            program.calls.get(0).assertLong(1, 42L);
        }
    }

    @Test
    public void shouldBindDoubleValue() {
        try (SQLiteQuery query = newQuery("SELECT ?")) {
            query.bindDouble(1, 3.14159);
            query.bindTo(program);
            assertEquals(1, program.calls.size());
            program.calls.get(0).assertDouble(1, 3.14159);
        }
    }

    @Test
    public void shouldBindStringValue() {
        try (SQLiteQuery query = newQuery("SELECT ?")) {
            query.bindString(1, "hello world");
            query.bindTo(program);
            assertEquals(1, program.calls.size());
            program.calls.get(0).assertString(1, "hello world");
        }
    }

    @Test
    public void shouldBindBlobValue() {
        var blob = new byte[] { 1, 2, 3, 4, 5 };
        try (SQLiteQuery query = newQuery("SELECT ?")) {
            query.bindBlob(1, blob);
            query.bindTo(program);
            assertEquals(1, program.calls.size());
            program.calls.get(0).assertBlob(1, blob);
        }
    }

    @Test
    public void shouldBindsNullWhenExplicitlyBound() {
        try (SQLiteQuery query = newQuery("SELECT ?")) {
            query.bindNull(1);
            query.bindTo(program);
            assertEquals(1, program.calls.size());
            program.calls.get(0).assertNull(1);
        }
    }

    @Test
    public void shouldBindNullWhenArgLeftUnbound() {
        try (SQLiteQuery query = newQuery("SELECT ?")) {
            query.bindTo(program);
            assertEquals(1, program.calls.size());
            program.calls.get(0).assertNull(1);
        }
    }

    @Test
    public void shouldBindsMultipleArgsInOrderWithMixedTypes() {
        var blob = new byte[] { 9, 8, 7 };
        try (SQLiteQuery query = newQuery("SELECT ?, ?, ?, ?, ?")) {
            query.bindLong(1, 100L);
            query.bindString(2, "middle");
            query.bindDouble(3, 2.5);
            query.bindNull(4);
            query.bindBlob(5, blob);
            query.bindTo(program);
            assertEquals(5, program.calls.size());
            program.calls.get(0).assertLong(1, 100L);
            program.calls.get(1).assertString(2, "middle");
            program.calls.get(2).assertDouble(3, 2.5);
            program.calls.get(3).assertNull(4);
            program.calls.get(4).assertBlob(5, blob);
        }
    }

    @Test
    public void shouldPerformNoOpBindWhenStatementHasNoParameters() {
        try (SQLiteQuery query = newQuery("SELECT 1")) {
            query.bindTo(program);
            assertEquals(0, program.calls.size());
        }
    }

    @Test
    public void shouldBindAllArgsThenBindToTranslatesEachSupportedType() {
        var blob = new byte[] { 0x0A, 0x0B };
        try (SQLiteQuery query = newQuery("SELECT ?, ?, ?, ?, ?, ?")) {
            query.bindAllArgs(true, (byte) 7, (short) 8, 9, blob, "text");
            query.bindTo(program);
            assertEquals(6, program.calls.size());
            program.calls.get(0).assertLong(1, 1L);
            program.calls.get(1).assertLong(2, 7L);
            program.calls.get(2).assertLong(3, 8L);
            program.calls.get(3).assertLong(4, 9L);
            program.calls.get(4).assertBlob(5, blob);
            program.calls.get(5).assertString(6, "text");
        }
    }

    private static final class RecordingProgram implements SupportSQLiteProgram {
        final List<Call> calls = new ArrayList<>();

        @Override
        public void bindNull(int index) {
            calls.add(Call.ofNull(index));
        }

        @Override
        public void bindLong(int index, long value) {
            calls.add(Call.ofLong(index, value));
        }

        @Override
        public void bindDouble(int index, double value) {
            calls.add(Call.ofDouble(index, value));
        }

        @Override
        public void bindString(int index, String value) {
            calls.add(Call.ofString(index, value));
        }

        @Override
        public void bindBlob(int index, byte[] value) {
            calls.add(Call.ofBlob(index, value));
        }

        @Override
        public void clearBindings() {
            calls.clear();
        }

        @Override
        public void close() {}
    }

    private static final class Call {
        enum Kind {
            NULL,
            LONG,
            DOUBLE,
            STRING,
            BLOB
        }

        final Kind kind;
        final int index;
        final long longValue;
        final double doubleValue;
        final String stringValue;
        final byte[] blobValue;

        private Call(
                Kind kind,
                int index,
                long longValue,
                double doubleValue,
                String stringValue,
                byte[] blobValue) {
            this.kind = kind;
            this.index = index;
            this.longValue = longValue;
            this.doubleValue = doubleValue;
            this.stringValue = stringValue;
            this.blobValue = blobValue;
        }

        static Call ofNull(int index) {
            return new Call(Kind.NULL, index, 0, 0, null, null);
        }

        static Call ofLong(int index, long value) {
            return new Call(Kind.LONG, index, value, 0, null, null);
        }

        static Call ofDouble(int index, double value) {
            return new Call(Kind.DOUBLE, index, 0, value, null, null);
        }

        static Call ofString(int index, String value) {
            return new Call(Kind.STRING, index, 0, 0, value, null);
        }

        static Call ofBlob(int index, byte[] value) {
            return new Call(Kind.BLOB, index, 0, 0, null, value);
        }

        void assertNull(int expectedIndex) {
            assertEquals(Kind.NULL, kind);
            assertEquals(expectedIndex, index);
        }

        void assertLong(int expectedIndex, long expectedValue) {
            assertEquals(Kind.LONG, kind);
            assertEquals(expectedIndex, index);
            assertEquals(expectedValue, longValue);
        }

        void assertDouble(int expectedIndex, double expectedValue) {
            assertEquals(Kind.DOUBLE, kind);
            assertEquals(expectedIndex, index);
            assertEquals(expectedValue, doubleValue, 0.0);
        }

        void assertString(int expectedIndex, String expectedValue) {
            assertEquals(Kind.STRING, kind);
            assertEquals(expectedIndex, index);
            assertEquals(expectedValue, stringValue);
        }

        void assertBlob(int expectedIndex, byte[] expectedValue) {
            assertEquals(Kind.BLOB, kind);
            assertEquals(expectedIndex, index);
            assertArrayEquals(expectedValue, blobValue);
        }
    }
}
