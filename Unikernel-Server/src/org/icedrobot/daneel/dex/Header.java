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

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A parser class capable of parsing {@code header_item} structures as part of
 * DEX files. Keep package-private to hide internal API.
 */
class Header {

    public enum Endianess {
        ENDIAN_CONSTANT, REVERSE_ENDIAN_CONSTANT
    }

    public static Header parse(ByteBuffer headerBuffer) {
        return new Header(headerBuffer);
    }

    private int checksum;

    private byte[] signature;

    private int fileLength;

    private int headerLength;

    private Endianess endianTag;

    private int linkSize;

    private int linkOff;

    private int mapOff;

    private int stringIdsSize;

    private int stringIdsOff;

    private int typeIdsSize;

    private int typeIdsOff;

    private int protoIdsSize;

    private int protoIdsOff;

    private int fieldIdsSize;

    private int fieldIdsOff;

    private int methodIdsSize;

    private int methodIdsOff;

    private int classDefsSize;

    private int classDefsOff;

    private int dataSize;

    private int dataOff;

    private Header(ByteBuffer buffer) {
        buffer = buffer.order(ByteOrder.LITTLE_ENDIAN);
        try {
            parseMagicByte(buffer);
            parseChecksum(buffer);
            parseSignature(buffer);
            parseFileLength(buffer);
            parseHeaderLength(buffer);
            parseEndianess(buffer);
            linkSize = buffer.getInt();
            linkOff = buffer.getInt();
            mapOff = buffer.getInt();
            stringIdsSize = buffer.getInt();
            stringIdsOff = buffer.getInt();
            typeIdsSize = buffer.getInt();
            typeIdsOff = buffer.getInt();
            protoIdsSize = buffer.getInt();
            protoIdsOff = buffer.getInt();
            fieldIdsSize = buffer.getInt();
            fieldIdsOff = buffer.getInt();
            methodIdsSize = buffer.getInt();
            methodIdsOff = buffer.getInt();
            classDefsSize = buffer.getInt();
            classDefsOff = buffer.getInt();
            dataSize = buffer.getInt();
            dataOff = buffer.getInt();
        } catch (BufferUnderflowException ex) {
            throw new DexParseException(ex);
        }
    }

    public int getChecksum() {
        return checksum;
    }

    public byte[] getSignature() {
        return signature;
    }

    public int getFileLength() {
        return fileLength;
    }

    public int getHeaderLength() {
        return headerLength;
    }

    public Endianess getEndianTag() {
        return endianTag;
    }

    public int getLinkSize() {
        return linkSize;
    }

    public int getLinkOff() {
        return linkOff;
    }

    public int getMapOff() {
        return mapOff;
    }

    public int getStringIdsSize() {
        return stringIdsSize;
    }

    public int getStringIdsOff() {
        return stringIdsOff;
    }

    public int getTypeIdsSize() {
        return typeIdsSize;
    }

    public int getTypeIdsOff() {
        return typeIdsOff;
    }

    public int getProtoIdsSize() {
        return protoIdsSize;
    }

    public int getProtoIdsOff() {
        return protoIdsOff;
    }

    public int getFieldIdsSize() {
        return fieldIdsSize;
    }

    public int getFieldIdsOff() {
        return fieldIdsOff;
    }

    public int getMethodIdsSize() {
        return methodIdsSize;
    }

    public int getMethodIdsOff() {
        return methodIdsOff;
    }

    public int getClassDefsSize() {
        return classDefsSize;
    }

    public int getClassDefsOff() {
        return classDefsOff;
    }

    public int getDataSize() {
        return dataSize;
    }

    public int getDataOff() {
        return dataOff;
    }

    private void parseMagicByte(ByteBuffer buffer) {
        assertMagicByte(buffer.get(), (byte) 0x64);
        assertMagicByte(buffer.get(), (byte) 0x65);
        assertMagicByte(buffer.get(), (byte) 0x78);
        assertMagicByte(buffer.get(), (byte) 0x0a);
        assertMagicByte(buffer.get(), (byte) 0x30);
        assertMagicByte(buffer.get(), (byte) 0x33);
        assertMagicByte(buffer.get(), (byte) 0x35);
        assertMagicByte(buffer.get(), (byte) 0x00);
    }

    private void assertMagicByte(byte actual, byte expected) {
        if (actual != expected) {
            throw new DexParseException();
        }
    }

    private void parseChecksum(ByteBuffer buffer) {
        checksum = buffer.getInt();
    }

    private void parseSignature(ByteBuffer buffer) {
        signature = new byte[20];
        buffer.get(signature, 0, 20);
    }

    private void parseFileLength(ByteBuffer buffer) {
        fileLength = buffer.getInt();
    }

    private void parseHeaderLength(ByteBuffer buffer) {
        headerLength = buffer.getInt();
    }

    private void parseEndianess(ByteBuffer buffer) {
        int endian = buffer.getInt();
        if (endian == 0x12345678) {
            endianTag = Endianess.ENDIAN_CONSTANT;
        } else if (endian == 0x78563412) {
            endianTag = Endianess.REVERSE_ENDIAN_CONSTANT;
        } else {
            throw new DexParseException("Invalid endian tag");
        }
    }

    public String toString() {
        StringBuilder string = new StringBuilder();
        string.append("Dex Header");
        string.append("\n  checksum: ");
        string.append(getChecksum());
        string.append("\n  signature: ");
        for (byte sigByte : getSignature()) {
            string.append(Integer.toHexString(sigByte));
            string.append(' ');
        }
        string.append("\n  file size: ");
        string.append(getFileLength());
        string.append("\n  header size: ");
        string.append(getHeaderLength());
        string.append("\n  endian tag: ");
        string.append(getEndianTag());
        string.append("\n  link size: ");
        string.append(getLinkSize());
        string.append("\n  link offset: ");
        string.append(getLinkOff());
        string.append("\n  map offset: ");
        string.append(getMapOff());
        string.append("\n  string ids size: ");
        string.append(getStringIdsSize());
        string.append("\n  string ids offset: ");
        string.append(getStringIdsOff());
        string.append("\n  type ids size: ");
        string.append(getTypeIdsSize());
        string.append("\n  type ids offset: ");
        string.append(getTypeIdsOff());
        string.append("\n  proto ids size: ");
        string.append(getProtoIdsSize());
        string.append("\n  proto ids offset: ");
        string.append(getProtoIdsOff());
        string.append("\n  field ids size: ");
        string.append(getFieldIdsSize());
        string.append("\n  field ids offset: ");
        string.append(getFieldIdsOff());
        string.append("\n  method ids size: ");
        string.append(getMethodIdsSize());
        string.append("\n  method ids offset: ");
        string.append(getMethodIdsOff());
        string.append("\n  class defs size: ");
        string.append(getClassDefsSize());
        string.append("\n  class defs offset: ");
        string.append(getClassDefsOff());
        string.append("\n  data size: ");
        string.append(getDataSize());
        string.append("\n  data offset: ");
        string.append(getDataOff());

        return string.toString();
    }
}
