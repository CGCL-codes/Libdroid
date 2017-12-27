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

package org.icedrobot.daneel.util;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * Utility methods extending the base functionality of the standard
 * {@link java.nio.ByteBuffer} class by means of static methods.
 */
public class BufferUtil {

    /**
     * Reads an array of integers from the buffer's current position.
     * 
     * @param buffer The byte buffer to read from.
     * @param size The number of values to read.
     * @return An array containing the read values.
     */
    public static int[] getInts(ByteBuffer buffer, int size) {
        int[] ints = new int[size];
        for (int i = 0; i < size; i++)
            ints[i] = buffer.getInt();
        return ints;
    }

    /**
     * Reads a "Little-Endian Base 128" variable-length encoded value at the
     * buffer's current position. A maximum of five bytes is read because only
     * 32-bit values are encoded this way. For details about this encoding see
     * the "Dalvik Executable Format" specification.
     * 
     * @param buffer The byte buffer to read from.
     * @return The decoded unsigned 32-bit value.
     */
    public static int getULEB128(ByteBuffer buffer) {
        int result = 0;
        for (int i = 0; i < 5; i++) {
            byte b = buffer.get();
            result |= ((b & 0x7f) << (7 * i));
            if ((b & 0x80) == 0)
                break;
        }
        return result;
    }

    /**
     * Reads a "Little-Endian Base 128" variable-length encoded value at the
     * buffer's current position. A maximum of five bytes is read because only
     * 32-bit values are encoded this way. For details about this encoding see
     * the "Dalvik Executable Format" specification.
     * 
     * @param buffer The byte buffer to read from.
     * @return The decoded signed 32-bit value.
     */
    public static int getSLEB128(ByteBuffer buffer) {
        int result = 0;
        for (int i = 0; i < 5; i++) {
            byte b = buffer.get();
            result |= ((b & 0x7f) << (7 * i));
            if ((b & 0x80) == 0) {
                int s = 32 - (7 * (i + 1));
                result = (result << s) >> s;
                break;
            }
        }
        return result;
    }

    /**
     * Reads string encoded using the "Modified UTF-8" encoding at the buffer's
     * current position. The number of bytes read from the buffer is implied by
     * the position of the terminating 0 byte.
     * 
     * @param buffer The byte buffer to read from.
     * @return The decoded string value.
     */
    public static String getMUTF8(ByteBuffer buffer) {
        byte b;
        ByteArrayOutputStream tmp = new ByteArrayOutputStream();
        while ((b = buffer.get()) != 0)
            tmp.write(b);
        return new String(tmp.toByteArray(), UTF8);
    }

    private static final Charset UTF8 = Charset.forName("UTF-8");
}
