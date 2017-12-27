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

import org.icedrobot.daneel.util.BufferUtil;

/**
 * A parser class capable of parsing {@code class_data_item} structures as part
 * of DEX files. Keep package-private to hide internal API.
 */
class ClassData {

    /**
     * Parses a {@code class_data_item} structure in a DEX file at the buffer's
     * current position.
     * 
     * @param buffer The byte buffer to read from.
     * @param dex The DEX file currently being parsed.
     * @param classDef The class definition this data belongs to.
     * @return An object representing the parsed data.
     */
    public static ClassData parse(ByteBuffer buffer, DexFile dex,
            ClassDef classDef) {
        return new ClassData(buffer, dex, classDef);
    }

    private final DexFile dex;

    private final ClassDef classDef;

    private int staticFieldsSize;

    private int instanceFieldsSize;

    private int directMethodsSize;

    private int virtualMethodsSize;

    private FieldId[] staticFieldsIds;
    private int[] staticFieldsFlags;

    private FieldId[] instanceFieldsIds;
    private int[] instanceFieldsFlags;

    private final MethodId[] directMethodsIds;
    private int[] directMethodsFlags;
    private final int[] directMethodsCodeOff;

    private final MethodId[] virtualMethodsIds;
    private int[] virtualMethodsFlags;
    private final int[] virtualMethodsCodeOff;

    private ClassData(ByteBuffer buffer, DexFile dex, ClassDef classDef) {
        this.dex = dex;
        this.classDef = classDef;
        staticFieldsSize = BufferUtil.getULEB128(buffer);
        instanceFieldsSize = BufferUtil.getULEB128(buffer);
        directMethodsSize = BufferUtil.getULEB128(buffer);
        virtualMethodsSize = BufferUtil.getULEB128(buffer);

        // Parse encoded_field structures in static_fields array and resolve
        // field id indices.
        staticFieldsIds = new FieldId[staticFieldsSize];
        staticFieldsFlags = new int[staticFieldsSize];
        int staticFieldsIdx = 0;
        for (int i = 0; i < staticFieldsSize; i++) {
            staticFieldsIdx += BufferUtil.getULEB128(buffer);
            staticFieldsIds[i] = dex.getFieldId(staticFieldsIdx);
            staticFieldsFlags[i] = BufferUtil.getULEB128(buffer);
        }

        // Parse encoded_field structures in instance_fields array and resolve
        // field id indices.
        instanceFieldsIds = new FieldId[instanceFieldsSize];
        instanceFieldsFlags = new int[instanceFieldsSize];
        int instanceFieldsIdx = 0;
        for (int i = 0; i < instanceFieldsSize; i++) {
            instanceFieldsIdx += BufferUtil.getULEB128(buffer);
            instanceFieldsIds[i] = dex.getFieldId(instanceFieldsIdx);
            instanceFieldsFlags[i] = BufferUtil.getULEB128(buffer);
        }

        // Parse encoded_method structures in direct_methods array.
        directMethodsIds = new MethodId[directMethodsSize];
        directMethodsFlags = new int[directMethodsSize];
        directMethodsCodeOff = new int[directMethodsSize];
        int directMethodsIdx = 0;
        for (int i = 0; i < directMethodsSize; i++) {
            directMethodsIdx += BufferUtil.getULEB128(buffer);
            directMethodsIds[i] = dex.getMethodId(directMethodsIdx);
            directMethodsFlags[i] = BufferUtil.getULEB128(buffer);;
            directMethodsCodeOff[i] = BufferUtil.getULEB128(buffer);;
        }

        // Parse encoded_method structures in virtual_methods array.
        virtualMethodsIds = new MethodId[virtualMethodsSize];
        virtualMethodsFlags = new int[virtualMethodsSize];
        virtualMethodsCodeOff = new int[virtualMethodsSize];
        int virtualMethodsIdx = 0;
        for (int i = 0; i < virtualMethodsSize; i++) {
            virtualMethodsIdx += BufferUtil.getULEB128(buffer);
            virtualMethodsIds[i] = dex.getMethodId(virtualMethodsIdx);
            virtualMethodsFlags[i] = BufferUtil.getULEB128(buffer);;
            virtualMethodsCodeOff[i] = BufferUtil.getULEB128(buffer);;
        }
    }

    /**
     * Returns the code object for a {@code code_item} at the given data offset.
     * The underlying implementation uses a lazy parsing approach.
     * 
     * @param codeOff The given offset into the data area.
     * @param method The method identifier of the method the code belongs to.
     * @param flags The access flags of the method the code belongs to.
     * @return The code object or {@code null} if there is none.
     */
    public Code getCode(int codeOff, MethodId method, int flags) {
        if (codeOff == 0)
            return null;

        // Parse associated code_item structure.
        ByteBuffer buf = dex.getDataBuffer(codeOff);
        Code code = Code.parse(buf, dex, method, flags);

        // Return the non-null object.
        return code;
    }

    /**
     * Allows the given visitor to visit this class data object.
     * 
     * @param visitor The given DEX class visitor object.
     * @param skip Flags indicating which information to skip while visiting.
     */
    public void accept(DexClassVisitor visitor, int skip) {

        // Visit all static fields.
        Object[] staticValues = classDef.getStaticValues();
        for (int i = 0; i < staticFieldsSize; i++) {
            int access = staticFieldsFlags[i];
            FieldId field = staticFieldsIds[i];
            Object value = (i < staticValues.length) ? staticValues[i] : null;
            acceptField(visitor, access, field, value, skip);
        }

        // Visit all instance fields.
        for (int i = 0; i < instanceFieldsSize; i++) {
            int access = instanceFieldsFlags[i];
            FieldId field = instanceFieldsIds[i];
            acceptField(visitor, access, field, null, skip);
        }

        // Visit all direct methods.
        for (int i = 0; i < directMethodsSize; i++) {
            int access = directMethodsFlags[i];
            MethodId method = directMethodsIds[i];
            Code code = ((skip & DexReader.SKIP_CODE) == 0) ? getCode(directMethodsCodeOff[i], method, access) : null;
            acceptMethod(visitor, access, method, code, skip);
        }

        // Visit all virtual methods.
        for (int i = 0; i < virtualMethodsSize; i++) {
            int access = virtualMethodsFlags[i];
            MethodId method = virtualMethodsIds[i];
            Code code = ((skip & DexReader.SKIP_CODE) == 0) ? getCode(virtualMethodsCodeOff[i], method, access) : null;
            acceptMethod(visitor, access, method, code, skip);
        }
    }

    /**
     * Helper method to visit a field.
     */
    private void acceptField(DexClassVisitor visitor, int access,
            FieldId field, Object value, int skip) {
        DexFieldVisitor dfv = visitor.visitField(access, field.getName(), field
                .getTypeDescriptor(), value);
        if (dfv == null)
            return;
        if ((skip & DexReader.SKIP_ANNOTATIONS) == 0) {
            AnnotationsDirectory annotations = classDef.getAnnotations();
            if (annotations != null)
                annotations.acceptFieldAnnotations(dfv, field);
        }
        dfv.visitEnd();
    }

    /**
     * Helper method to visit a method.
     */
    private void acceptMethod(DexClassVisitor visitor, int access,
            MethodId method, Code code, int skip) {
        ProtoId proto = method.getProtoId();
        DexMethodVisitor dmv = visitor
                .visitMethod(access, method.getName(), proto.getShorty(),
                        proto.getReturnType(), proto.getParameters());
        if (dmv == null)
            return;
        if ((skip & DexReader.SKIP_ANNOTATIONS) == 0) {
            AnnotationsDirectory annotations = classDef.getAnnotations();
            if (annotations != null)
                annotations.acceptMethodAnnotations(dmv, method);
        }
        if (code != null)
            code.accept(dmv, skip);
        dmv.visitEnd();
    }
}
