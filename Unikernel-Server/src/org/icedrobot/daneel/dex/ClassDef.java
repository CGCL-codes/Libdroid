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

/**
 * A parser class capable of parsing {@code class_def_item} structures as part
 * of DEX files. Keep package-private to hide internal API.
 */
class ClassDef {

    /**
     * Parses a {@code class_def_item} structure in a DEX file at the buffer's
     * current position.
     * 
     * @param buffer The byte buffer to read from.
     * @param dex The DEX file currently being parsed.
     * @return An object representing the parsed data.
     */
    static ClassDef parse(ByteBuffer buffer, DexFile dex) {
        return new ClassDef(buffer, dex);
    }

    private final DexFile dex;

    private String className;

    private int accessFlags;

    private String superclass;

    private String[] interfaces;

    private String sourceFile;

    private final AnnotationsDirectory annotations;

    private final int classDataOff;

    private final int staticValuesOff;

    private ClassDef(ByteBuffer buffer, DexFile dex) {
        this.dex = dex;
        int classIdx = buffer.getInt();
        accessFlags = buffer.getInt();
        int superclassIdx = buffer.getInt();
        int interfacesOff = buffer.getInt();
        int sourceFileIdx = buffer.getInt();
        int annotationsOff = buffer.getInt();
        classDataOff = buffer.getInt();
        staticValuesOff = buffer.getInt();

        // Resolve string and type indices.
        className = dex.getTypeDescriptor(classIdx);
        if (superclassIdx != DexFile.NO_INDEX)
            superclass = dex.getTypeDescriptor(superclassIdx);
        if (sourceFileIdx != DexFile.NO_INDEX)
            sourceFile = dex.getString(sourceFileIdx);

        // Parse associated type_list structure and resolve type indices.
        if (interfacesOff != 0) {
            ByteBuffer buf = dex.getDataBuffer(interfacesOff);
            interfaces = new String[buf.getInt()];
            for (int i = 0; i < interfaces.length; i++)
                interfaces[i] = dex.getTypeDescriptor(buf.getShort());
        }

        // Parse associated annotations_directory_item structure.
        if (annotationsOff != 0) {
            ByteBuffer buf = dex.getDataBuffer(annotationsOff);
            annotations = AnnotationsDirectory.parse(buf, dex);
        } else
            annotations = null;
    }

    public String getClassName() {
        return className;
    }

    public int getAccessFlags() {
        return accessFlags;
    }

    public String getSuperclass() {
        return superclass;
    }

    public String[] getInterfaces() {
        return interfaces;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public AnnotationsDirectory getAnnotations() {
        return annotations;
    }

    /**
     * Returns the class data associated with this class definition. The
     * underlying implementation uses a lazy parsing approach.
     * 
     * @return The class data object or {@code null} if there is none.
     */
    public ClassData getClassData() {
        if (classDataOff == 0)
            return null;

        /*
         * XXX Should we at some point decide to use caching for data object,
         * that is how it is done. Note that the method doesn't need to be
         * synchronized, we are aware of the race-condition but can safely
         * ignore it in this case.

        // Retrieve previously parsed and cached object.
        ClassData classData = (cachedClassData == null) ? null
                : cachedClassData.get();

        // In case there is no previously cached object (or it was collected),
        // we parse the data in a lazy fashion and cache it.
        if (classData == null) {
            ByteBuffer buf = dex.getDataBuffer(classDataOff);
            classData = ClassData.parse(buf, dex, this);
            cachedClassData = new SoftReference<ClassData>(classData);
        }

        * ... but we do not use caching at the moment.
        */

        // Parse associated class_data_item structure.
        ByteBuffer buf = dex.getDataBuffer(classDataOff);
        ClassData classData = ClassData.parse(buf, dex, this);

        // Return the non-null object.
        return classData;
    }

    /** Cache for parsed class data object, allowed to be collected. */
    //private SoftReference<ClassData> cachedClassData;

    /**
     * Returns the list of initial values for static fields. The underlying
     * implementation uses a lazy parsing approach.
     * 
     * @return The array holding static field values or an empty array in case
     *         there are none, never returns {@code null}.
     */
    public Object[] getStaticValues() {
        if (staticValuesOff == 0)
            return new Object[0];

        // Parse encoded_array_item and contained encoded_value structures.
        ByteBuffer buf = dex.getDataBuffer(staticValuesOff);
        Object[] staticValues = EncodedValue.parseArray(buf, dex);

        // Return the non-null object.
        return staticValues;
    }

    /**
     * Allows the given visitor to visit this class definition.
     * 
     * @param visitor The given DEX class visitor object.
     * @param skip Flags indicating which information to skip while visiting.
     */
    public void accept(DexClassVisitor visitor, int skip) {
        visitor.visit(accessFlags, className, superclass, interfaces);
        if (sourceFile != null)
            visitor.visitSource(sourceFile);
        if (annotations != null && (skip & DexReader.SKIP_ANNOTATIONS) == 0)
            getAnnotations().acceptClassAnnotations(visitor);
        if (classDataOff != 0)
            getClassData().accept(visitor, skip);
        visitor.visitEnd();
    }
}
