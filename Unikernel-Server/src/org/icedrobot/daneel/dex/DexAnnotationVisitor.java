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

/**
 * A visitor for annotations contained in DEX files.
 */
public interface DexAnnotationVisitor {

    /** Annotation should be visible at compile time only. */
    public static final int VISIBILITY_BUILD   = 0x00;

    /** Annotation should be visible at runtime. */
    public static final int VISIBILITY_RUNTIME = 0x01;

    /** Annotation should be visible to the VM only. */
    public static final int VISIBILITY_SYSTEM  = 0x02;

    /**
     * Visits a primitive annotation parameter. The primitive value is passed as
     * a boxed value. The {@code null} reference is passed as {@code null}
     * value.
     * 
     * @param name The annotation parameter name.
     * @param value The boxed primitive value or a {@code null} reference.
     */
    void visitPrimitive(String name, Object value);

    /**
     * Visits a type-reference (i.e. class or interface) annotation parameter.
     * The type is passed as a type descriptor.
     * 
     * @param name The annotation parameter name.
     * @param typeDesc The type's type descriptor.
     */
    void visitType(String name, String typeDesc);

    /**
     * Visits a field-reference annotation parameter. The field is passed as a
     * combination of the field owner, name and type.
     * 
     * @param name The annotation parameter name.
     * @param fieldOwner The field's owner as a type descriptor.
     * @param fieldName The field's name.
     * @param fieldDesc The field's type descriptor.
     */
    void visitField(String name, String fieldOwner, String fieldName,
            String fieldDesc);

    /**
     * Visits a method-reference annotation parameter. The field is passed as a
     * combination of the method owner, name and type.
     * 
     * @param name The annotation parameter name.
     * @param methodOwner The method's owner as a type descriptor.
     * @param methodName The method's name.
     * @param methodDesc The method's method descriptor.
     */
    void visitMethod(String name, String methodOwner, String methodName,
            String methodDesc);

    /**
     * Visits an enumeration-reference annotation parameter. The enumeration
     * value is passed as a combination of the enumeration class and name.
     * 
     * @param name The annotation parameter name.
     * @param enumOwner The enumeration's class as a type descriptor.
     * @param enumName The enumeration's constant name.
     */
    void visitEnum(String name, String enumOwner, String enumName);

    /**
     * Visits a sub-annotation annotation parameter. In case this visitor is
     * interested in further details about the annotation parameter it should
     * return a new visitor object, otherwise it should return {@code null}.
     * 
     * @param name The annotation parameter name.
     * @param type The sub-annotation's type as a type descriptor.
     * @return A visitor object for the parameter or {@code null} if this
     *         visitor is not interested in details about the parameter.
     */
    DexAnnotationVisitor visitAnnotation(String name, String type);

    /**
     * Visits an array annotation parameter. In case this visitor is interested
     * in further details about the annotation parameter it should return a new
     * visitor object, otherwise it should return {@code null}.
     * <p>
     * This nifty little trick to reduce interface complexity by reusing
     * annotation visitors for arrays was kindly borrowed from ASM's interface.
     * 
     * @param name The annotation parameter name.
     * @param size The size of the array to be visited.
     * @return A visitor object for the parameter or {@code null} if this
     *         visitor is not interested in details about the parameter.
     */
    DexAnnotationVisitor visitArray(String name, int size);

    /**
     * Visits the end of the annotation.
     */
    void visitEnd();
}
