/*
 * Copyright (c) 2008-2021, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.internal.serialization.impl.compact;

import com.hazelcast.nio.serialization.FieldKind;
import com.hazelcast.nio.serialization.compact.CompactWriter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.TreeMap;

/**
 * A CompactWriter that constructs a schema from Compact
 * serializable objects.
 */
public final class SchemaWriter implements CompactWriter {

    private final TreeMap<String, FieldDescriptor> fieldDefinitionMap = new TreeMap<>(Comparator.naturalOrder());
    private final String className;

    public SchemaWriter(String className) {
        this.className = className;
    }

    /**
     * Builds the schema from the written fields and returns it.
     */
    public Schema build() {
        return new Schema(className, fieldDefinitionMap);
    }

    /**
     * Adds a field to the schema.
     */
    public void addField(FieldDescriptor fieldDefinition) {
        fieldDefinitionMap.put(fieldDefinition.getFieldName(), fieldDefinition);
    }

    @Override
    public void writeInt(@Nonnull String fieldName, int value) {
        addField(new FieldDescriptor(fieldName, FieldKind.INT));
    }

    @Override
    public void writeLong(@Nonnull String fieldName, long value) {
        addField(new FieldDescriptor(fieldName, FieldKind.LONG));
    }

    @Override
    public void writeString(@Nonnull String fieldName, String str) {
        addField(new FieldDescriptor(fieldName, FieldKind.STRING));
    }

    @Override
    public void writeBoolean(@Nonnull String fieldName, boolean value) {
        addField(new FieldDescriptor(fieldName, FieldKind.BOOLEAN));
    }

    @Override
    public void writeByte(@Nonnull String fieldName, byte value) {
        addField(new FieldDescriptor(fieldName, FieldKind.BYTE));
    }

    @Override
    public void writeChar(@Nonnull String fieldName, char value) {
        addField(new FieldDescriptor(fieldName, FieldKind.CHAR));
    }

    @Override
    public void writeDouble(@Nonnull String fieldName, double value) {
        addField(new FieldDescriptor(fieldName, FieldKind.DOUBLE));
    }

    @Override
    public void writeFloat(@Nonnull String fieldName, float value) {
        addField(new FieldDescriptor(fieldName, FieldKind.FLOAT));
    }

    @Override
    public void writeShort(@Nonnull String fieldName, short value) {
        addField(new FieldDescriptor(fieldName, FieldKind.SHORT));
    }

    @Override
    public void writeObject(@Nonnull String fieldName, @Nullable Object object) {
        addField(new FieldDescriptor(fieldName, FieldKind.COMPACT));
    }

    @Override
    public void writeDecimal(@Nonnull String fieldName, @Nullable BigDecimal value) {
        addField(new FieldDescriptor(fieldName, FieldKind.DECIMAL));
    }

    @Override
    public void writeTime(@Nonnull String fieldName, @Nonnull LocalTime value) {
        addField(new FieldDescriptor(fieldName, FieldKind.TIME));
    }

    @Override
    public void writeDate(@Nonnull String fieldName, @Nonnull LocalDate value) {
        addField(new FieldDescriptor(fieldName, FieldKind.DATE));
    }

    @Override
    public void writeTimestamp(@Nonnull String fieldName, @Nonnull LocalDateTime value) {
        addField(new FieldDescriptor(fieldName, FieldKind.TIMESTAMP));
    }

    @Override
    public void writeTimestampWithTimezone(@Nonnull String fieldName, @Nonnull OffsetDateTime value) {
        addField(new FieldDescriptor(fieldName, FieldKind.TIMESTAMP_WITH_TIMEZONE));
    }

    @Override
    public void writeByteArray(@Nonnull String fieldName, @Nullable byte[] values) {
        addField(new FieldDescriptor(fieldName, FieldKind.BYTE_ARRAY));
    }

    @Override
    public void writeBooleanArray(@Nonnull String fieldName, @Nullable boolean[] values) {
        addField(new FieldDescriptor(fieldName, FieldKind.BOOLEAN_ARRAY));
    }

    @Override
    public void writeCharArray(@Nonnull String fieldName, @Nullable char[] values) {
        addField(new FieldDescriptor(fieldName, FieldKind.CHAR_ARRAY));
    }

    @Override
    public void writeIntArray(@Nonnull String fieldName, @Nullable int[] values) {
        addField(new FieldDescriptor(fieldName, FieldKind.INT_ARRAY));
    }

    @Override
    public void writeLongArray(@Nonnull String fieldName, @Nullable long[] values) {
        addField(new FieldDescriptor(fieldName, FieldKind.LONG_ARRAY));
    }

    @Override
    public void writeDoubleArray(@Nonnull String fieldName, @Nullable double[] values) {
        addField(new FieldDescriptor(fieldName, FieldKind.DOUBLE_ARRAY));
    }

    @Override
    public void writeFloatArray(@Nonnull String fieldName, @Nullable float[] values) {
        addField(new FieldDescriptor(fieldName, FieldKind.FLOAT_ARRAY));
    }

    @Override
    public void writeShortArray(@Nonnull String fieldName, @Nullable short[] values) {
        addField(new FieldDescriptor(fieldName, FieldKind.SHORT_ARRAY));
    }

    @Override
    public void writeStringArray(@Nonnull String fieldName, @Nullable String[] values) {
        addField(new FieldDescriptor(fieldName, FieldKind.STRING_ARRAY));
    }

    @Override
    public void writeDecimalArray(@Nonnull String fieldName, @Nullable BigDecimal[] values) {
        addField(new FieldDescriptor(fieldName, FieldKind.DECIMAL_ARRAY));
    }

    @Override
    public void writeTimeArray(@Nonnull String fieldName, @Nullable LocalTime[] values) {
        addField(new FieldDescriptor(fieldName, FieldKind.TIME_ARRAY));
    }

    @Override
    public void writeDateArray(@Nonnull String fieldName, @Nullable LocalDate[] values) {
        addField(new FieldDescriptor(fieldName, FieldKind.DATE_ARRAY));
    }

    @Override
    public void writeTimestampArray(@Nonnull String fieldName, @Nullable LocalDateTime[] values) {
        addField(new FieldDescriptor(fieldName, FieldKind.TIMESTAMP_ARRAY));
    }

    @Override
    public void writeTimestampWithTimezoneArray(@Nonnull String fieldName, @Nullable OffsetDateTime[] values) {
        addField(new FieldDescriptor(fieldName, FieldKind.TIMESTAMP_WITH_TIMEZONE_ARRAY));
    }

    @Override
    public void writeObjectArray(@Nonnull String fieldName, @Nullable Object[] values) {
        addField(new FieldDescriptor(fieldName, FieldKind.COMPACT_ARRAY));
    }
}
