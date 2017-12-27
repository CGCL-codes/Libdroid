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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.LinkedHashMap;
import java.util.Map;

import org.icedrobot.daneel.util.BufferUtil;

/**
 * This class reads the contents of a DEX file. It is the main entry point into
 * our parser implementation and optimized towards {@link java.nio.ByteBuffer}
 * data sources.
 */
public class DexFile implements DexReader {

    /** Constant used to indicate that an index value is absent. */
    public static final int NO_INDEX = 0xffffffff;

    
    /**
     * Create a DEX file from a buffer of bytes.
     * 
     * @param buffer a buffer of bytes containing classes encoded using the DEX format.
     * @return a DEX buffer
     */
    public static DexFile parse(ByteBuffer buffer) {
        return new DexFile(buffer);
    }

    /**
     * Create a DEX file from a file by reading its content.
     * The file should be encoded using the DEX file format.
     *  
     * @param file a file.
     * @return a DEX file
     * @throws IOException if the file can't be read.
     */
    public static DexFile parse(File file) throws IOException {
        @SuppressWarnings("resource")
		ByteBuffer buffer = (new RandomAccessFile(file, "r")).getChannel().map(
                FileChannel.MapMode.READ_ONLY, 0, file.length());
        return new DexFile(buffer);
    }

    /**
     * Create a DEX file from an input stream and a size.
     * The input stream should be encoded using the DEX file format.
     *  
     * @param inputStream an input stream
     * @param size the size of the content of the input stream.
     * @return a DEX file
     * @throws IOException if the content of the input stream can't be read.
     */
    public static DexFile parse(InputStream inputStream, long size) throws IOException {
        if (size > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Oversized DEX file detected.");
        
        ByteBuffer buffer = ByteBuffer.allocate((int)size);
        ReadableByteChannel channel = Channels.newChannel(inputStream);
        while (buffer.hasRemaining()) {
            channel.read(buffer);
        }
        buffer.clear();
        return new DexFile(buffer);
    }
    
    public static DexFile parse(byte[] bytes) {
        return new DexFile(ByteBuffer.wrap(bytes));
    }

    private Header header;

    private String[] strings;

    private String[] typeDescriptors;

    private final ProtoId[] protoIds;

    private FieldId[] fieldIds;

    private final MethodId[] methodIds;

    private final Map<String, ClassDef> classDefs;

    private final ByteBuffer dataBuffer;

    private DexFile(ByteBuffer buffer) {
        ByteBuffer buf = buffer.duplicate();
        header = Header.parse(buf);

        // Slice off a buffer for the data area.
        buf.position(header.getDataOff());
        dataBuffer = buf.slice();
        dataBuffer.limit(header.getDataSize());

        // Parse string_id_item and string_data_item structures.
        strings = new String[header.getStringIdsSize()];
        buf.position(header.getStringIdsOff());
        int[] stringsDataOff = BufferUtil.getInts(buf, strings.length);
        for (int i = 0; i < strings.length; i++) {
            buf.position(stringsDataOff[i]);
            int utf16Size = BufferUtil.getULEB128(buf);
            strings[i] = BufferUtil.getMUTF8(buf);
            if (strings[i].length() != utf16Size)
                throw new DexParseException("Mismatch in string lengths.");
        }

        // Parse type_id_item structures.
        typeDescriptors = new String[header.getTypeIdsSize()];
        buf.position(header.getTypeIdsOff());
        for (int i = 0; i < typeDescriptors.length; i++)
            typeDescriptors[i] = getString(buf.getInt());

        // Parse proto_id_item structures.
        protoIds = new ProtoId[header.getProtoIdsSize()];
        buf.position(header.getProtoIdsOff());
        for (int i = 0; i < protoIds.length; i++)
            protoIds[i] = ProtoId.parse(buf, this);

        // Parse field_id_item structures.
        fieldIds = new FieldId[header.getFieldIdsSize()];
        buf.position(header.getFieldIdsOff());
        for (int i = 0; i < fieldIds.length; i++)
            fieldIds[i] = FieldId.parse(buf, this);

        // Parse method_id_item structures.
        methodIds = new MethodId[header.getMethodIdsSize()];
        buf.position(header.getMethodIdsOff());
        for (int i = 0; i < methodIds.length; i++)
            methodIds[i] = MethodId.parse(buf, this);

        // Parse class_def_item structures.
        int classDefsSize = header.getClassDefsSize();
        classDefs = new LinkedHashMap<String, ClassDef>(classDefsSize);
        buf.position(header.getClassDefsOff());
        for (int i = 0; i < classDefsSize; i++) {
            ClassDef classDef = ClassDef.parse(buf, this);
            if (classDefs.put(classDef.getClassName(), classDef) != null)
                throw new DexParseException("Redefinition of class: "
                        + classDef.getClassName());
        }
    }

    Header getHeader() {
        return header;
    }

    /**
     * Resolves the given index value to a string value. The index is implicitly
     * range-checked. Keep package-private to hide internal API.
     * 
     * @param idx The index referring to a string value inside this DEX file.
     * @return The resolved string value.
     */
    String getString(int idx) {
        return strings[idx];
    }

    /**
     * Resolves the given index value to a type descriptor. The index is
     * implicitly range-checked. Keep package-private to hide internal API.
     * 
     * @param idx The index referring to a type descriptor inside this DEX file.
     * @return The resolved type descriptor as string.
     */
    String getTypeDescriptor(int idx) {
        return typeDescriptors[idx];
    }

    /**
     * Resolves the given index value to a prototype id object. The index is
     * implicitly range-checked. Keep package-private to hide internal API.
     * 
     * @param idx The index referring to a prototype id inside this DEX file.
     * @return The resolved prototype id object.
     */
    ProtoId getProtoId(int idx) {
        return protoIds[idx];
    }

    /**
     * Resolves the given index value to a field id object. The index is
     * implicitly range-checked. Keep package-private to hide internal API.
     * 
     * @param idx The index referring to a field id inside this DEX file.
     * @return The resolved field id object.
     */
    FieldId getFieldId(int idx) {
        return fieldIds[idx];
    }

    /**
     * Resolves the given index value to a method id object. The index is
     * implicitly range-checked. Keep package-private to hide internal API.
     * 
     * @param idx The index referring to a method id inside this DEX file.
     * @return The resolved method id object.
     */
    MethodId getMethodId(int idx) {
        return methodIds[idx];
    }

    /**
     * Provides a buffer for the data area of this DEX file. The buffer is
     * positioned at the given offset and limited to the data area, all attempts
     * to read outside that area will fail. Keep package-private to hide
     * internal API.
     * 
     * @param off The offset from the start of the file into the data area.
     * @return The positioned and limited byte buffer.
     * @throws DexParseException In case the offset is outside of data area.
     */
    ByteBuffer getDataBuffer(int off) {
        int dataOff = header.getDataOff();
        if (off < dataOff || off >= dataOff + dataBuffer.limit())
            throw new DexParseException("Offset is outside of data area.");
        ByteBuffer buffer = dataBuffer.duplicate();
        buffer.order(ByteOrder.LITTLE_ENDIAN).position(off - dataOff);
        return buffer;
    }

    @Override
    public String toString() {
        return header.toString();
    }

    @Override
    public void accept(DexFileVisitor visitor, int skip) {
        for (ClassDef classDef : classDefs.values()) {
            DexClassVisitor dcv = visitor.visitClass(classDef.getClassName());
            if (dcv != null)
                classDef.accept(dcv, skip);
        }
        visitor.visitEnd();
    }

    @Override
    public void accept(String className, DexClassVisitor visitor, int skip)
            throws ClassNotFoundException {
        ClassDef classDef = classDefs.get(className);
        if (classDef == null)
            throw new ClassNotFoundException("Class not defined in DEX file: "
                    + className);
        classDef.accept(visitor, skip);
    }
}
