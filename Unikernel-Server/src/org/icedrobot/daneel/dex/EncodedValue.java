/*
 * Daneel - Dalvik to Java bytecode compiler
 * Copyright (C) 2011  IcedRobot team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * This file is subject to the "Classpath" exception:
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library.  Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent
 * modules, and to copy and distribute the resulting executable under terms
 * of your choice, provided that you also meet, for each linked independent
 * module, the terms and conditions of the license of that module.  An
 * independent module is a module which is not derived from or based on
 * this library.  If you modify this library, you may extend this exception
 * to your version of the library, but you are not obligated to do so.  If
 * you do not wish to do so, delete this exception statement from your
 * version.
 */

package org.icedrobot.daneel.dex;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.icedrobot.daneel.util.BufferUtil;

/**
 * A parser class capable of parsing {@code encoded_value} structures as part of
 * DEX files. Keep package-private to hide internal API.
 */
class EncodedValue {

    public static final int VALUE_BYTE = 0x00;
    public static final int VALUE_SHORT = 0x02;
    public static final int VALUE_CHAR = 0x03;
    public static final int VALUE_INT = 0x04;
    public static final int VALUE_LONG = 0x06;
    public static final int VALUE_FLOAT = 0x10;
    public static final int VALUE_DOUBLE = 0x11;
    public static final int VALUE_STRING = 0x17;
    public static final int VALUE_TYPE = 0x18;
    public static final int VALUE_FIELD = 0x19;
    public static final int VALUE_METHOD = 0x1a;
    public static final int VALUE_ENUM = 0x1b;
    public static final int VALUE_ARRAY = 0x1c;
    public static final int VALUE_ANNOTATION = 0x1d;
    public static final int VALUE_NULL = 0x1e;
    public static final int VALUE_BOOLEAN = 0x1f;

    /**
     * Parses a {@code encoded_value} structure in a DEX file at the buffer's
     * current position.
     * 
     * @param buffer The byte buffer to read from.
     * @param dex The DEX file currently being parsed.
     * @return An object representing the parsed data.
     */
    public static Object parse(ByteBuffer buffer, DexFile dex) {
        int format = buffer.get() & 0xFF;
        int type = format & 0x1f;
        int arg = format >> 5;

        // Switch over all possible format types.
        switch (type) {
        case VALUE_BYTE:
            assertValueArg(arg, 0);
            return Byte.valueOf(buffer.get());
        case VALUE_SHORT:
            assertValueArg(arg, 1);
            return Short.valueOf((short) readS64(buffer, arg));
        case VALUE_CHAR:
            assertValueArg(arg, 1);
            return Character.valueOf((char) readU64(buffer, arg));
        case VALUE_INT:
            assertValueArg(arg, 3);
            return Integer.valueOf((int) readS64(buffer, arg));
        case VALUE_LONG:
            assertValueArg(arg, 7);
            return Long.valueOf(readS64(buffer, arg));
        case VALUE_FLOAT:
            assertValueArg(arg, 3);
            return Float.intBitsToFloat((int) readL64(buffer, arg, 32));
        case VALUE_DOUBLE:
            assertValueArg(arg, 7);
            return Double.longBitsToDouble(readL64(buffer, arg, 64));
        case VALUE_STRING:
            assertValueArg(arg, 3);
            return dex.getString((int) readU64(buffer, arg));
        case VALUE_TYPE:
            assertValueArg(arg, 3);
            final String t = dex.getTypeDescriptor((int) readU64(buffer, arg));
            return new AnnotationVisitable() {
                public void accept(DexAnnotationVisitor visitor, String name) {
                    visitor.visitType(name, t);
                }
            };
        case VALUE_FIELD:
            assertValueArg(arg, 3);
            final FieldId f = dex.getFieldId((int) readU64(buffer, arg));
            return new AnnotationVisitable() {
                public void accept(DexAnnotationVisitor visitor, String name) {
                    visitor.visitField(name, f.getClassName(), f.getName(), f
                            .getTypeDescriptor());
                }
            };
        case VALUE_METHOD:
            assertValueArg(arg, 3);
            final MethodId m = dex.getMethodId((int) readU64(buffer, arg));
            return new AnnotationVisitable() {
                public void accept(DexAnnotationVisitor visitor, String name) {
                    visitor.visitMethod(name, m.getClassName(), m.getName(), m
                            .getMethodDesc());
                }
            };
        case VALUE_ENUM:
            assertValueArg(arg, 3);
            final FieldId e = dex.getFieldId((int) readU64(buffer, arg));
            return new AnnotationVisitable() {
                public void accept(DexAnnotationVisitor visitor, String name) {
                    visitor.visitEnum(name, e.getClassName(), e.getName());
                }
            };
        case VALUE_ARRAY:
            assertValueArg(arg, 0);
            return parseArray(buffer, dex);
        case VALUE_ANNOTATION:
        	assertValueArg(arg, 0);
            return null;
        case VALUE_NULL:
            assertValueArg(arg, 0);
            return null;
        case VALUE_BOOLEAN:
            assertValueArg(arg, 1);
            return Boolean.valueOf(arg == 1);
        default:
            //throw new DexParseException("Unknown encoded value type: " + String.format("0x%02x", type));
        	 assertValueArg(arg, 0);
            return null;
        }
    }

    /**
     * Parses a {@code encoded_array} structure in a DEX file at the buffer's
     * current position.
     * 
     * @param buffer The byte buffer to read from.
     * @param dex The DEX file currently being parsed.
     * @return An object representing the parsed data.
     */
    public static Object[] parseArray(ByteBuffer buffer, DexFile dex) {
        int size = BufferUtil.getULEB128(buffer);
        Object[] values = new Object[size];
        for (int i = 0; i < size; i++)
            values[i] = parse(buffer, dex);
        return values;
    }

    /**
     * Parses a {@code encoded_annotation} structure in a DEX file at the
     * buffer's current position.
     * 
     * @param buffer The byte buffer to read from.
     * @param dex The DEX file currently being parsed.
     * @return An object representing the parsed data.
     */
    public static AnnotationValue parseAnnotation(ByteBuffer buffer, DexFile dex) {
        int typeIdx = BufferUtil.getULEB128(buffer);
        int size = BufferUtil.getULEB128(buffer);
        String type = dex.getTypeDescriptor(typeIdx);
        Map<String, Object> elements = new HashMap<String, Object>(size);
        for (int i = 0; i < size; i++) {
            int nameIdx = BufferUtil.getULEB128(buffer);
            elements.put(dex.getString(nameIdx), parse(buffer, dex));
        }
        return new AnnotationValue(type, elements);
    }

    /**
     * Helper method for sanity checking the {@code value_arg} value.
     * 
     * @param arg The given {@code value_arg} value.
     * @param max The maximum value allowed in this instance.
     * @throws DexParseException In case the value exceeds its boundaries.
     */
    private static void assertValueArg(int arg, int max) {
        if (arg < 0 || arg > max)
            throw new DexParseException("Encoded value argument out of range: "
                    + arg);
    }

    /**
     * Helper method decoding a signed (sign-extended) {@code value} array.
     * 
     * @param buffer The buffer positioned at the array.
     * @param size The number of bytes in the array minus 1.
     * @return The decoded value.
     */
    private static long readS64(ByteBuffer buffer, int size) {
        long result = 0;
        for (int i = 0; i < size; i++)
            result |= ((long) buffer.get() & 0xff) << (i * 8);
        result |= ((long) buffer.get()) << (size * 8);
        return result;
    }

    /**
     * Helper method decoding an unsigned (zero-extended) {@code value} array.
     * 
     * @param buffer The buffer positioned at the {@code value} array.
     * @param size The number of bytes in the array minus 1.
     * @return The decoded value.
     */
    private static long readU64(ByteBuffer buffer, int size) {
        long result = 0;
        for (int i = 0; i <= size; i++)
            result |= ((long) buffer.get() & 0xff) << (i * 8);
        return result;
    }

    /**
     * Helper method decoding a left-aligned (zero-extended to the right)
     * {@code value} array. Used to encode floating point values.
     * 
     * @param buffer The buffer positioned at the {@code value} array.
     * @param size The number of bytes in the array minus 1.
     * @param bits The expected number of bits in the decoded value.
     * @return The decoded value.
     */
    private static long readL64(ByteBuffer buffer, int size, int bits) {
        long result = readU64(buffer, size);
        result <<= (bits - (size + 1) * 8);
        return result;
    }

    /**
     * An internal interface to allow boxing of certain encoded values
     * representing reflective references. Allows those boxed types to be
     * visited by an annotation visitor.
     */
    static interface AnnotationVisitable {
        public void accept(DexAnnotationVisitor visitor, String name);
    };

    /**
     * An internal representation of an annotation as encoded in the {@code
     * encoded_annotation} structure.
     */
    static class AnnotationValue {
        final String type;
        final Map<String, Object> elements;

        public AnnotationValue(String type, Map<String, Object> elements) {
            this.type = type;
            this.elements = elements;
        }

        @Override
        public String toString() {
            return '@' + type + '(' + elements + ')';
        }
    };
}
