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
 * A visitor for classes contained in DEX files.
 */
public interface DexClassVisitor {

    /**
     * Visits the header of the class.
     * 
     * @param access The access flags of the class.
     * @param name The name of the class as a type descriptor.
     * @param supername The name of the superclass as a type descriptor or
     *            {@code null} if the class has no superclass (i.e. only for
     *            definition of {@code java.lang.Object}).
     * @param interfaces The names of implemented interfaces as type
     *            descriptors.
     */
    void visit(int access, String name, String supername, String[] interfaces);

    /**
     * Visits the source of the class.
     * 
     * @param source The name of the file containing the original source for (at
     *            least most of) this class.
     */
    void visitSource(String source);

    /**
     * Visits an annotation of the class. In case this visitor is interested in
     * further details about the annotation it should return a new visitor
     * object, otherwise it should return {@code null}.
     * 
     * @param visibility The annotation's visibility flags.
     * @param type The annotation's type as a type descriptor.
     * @return A visitor object for the annotation or {@code null} if this
     *         visitor is not interested in details about the annotation.
     */
    DexAnnotationVisitor visitAnnotation(int visibility, String type);

    /**
     * Visits a field of the class. In case this visitor is interested in
     * further details about the field (i.e. annotations) it should return a new
     * visitor object, otherwise it should return {@code null}.
     * 
     * @param access The field's access flags.
     * @param name The field's name.
     * @param type The field's type as a type descriptor.
     * @param value The field's initial value. This parameter can be a boxed
     *            primitive value, a {@code String} object or {@code null} if
     *            the field does not have an initial value. Is always
     *            {@code null} for instance fields.
     * @return A visitor object for the field or {@code null} if this visitor is
     *         not interested in details about the field.
     */
    DexFieldVisitor visitField(int access, String name, String type,
            Object value);

    /**
     * Visits a method of the class. In case this visitor is interested in
     * further details about the method it should return a new visitor object,
     * otherwise it should return {@code null}.
     * 
     * @param access The method's access flags.
     * @param name The method's name.
     * @param shorty The method's prototype as a short form descriptor.
     * @param returnType The method's return type as a type descriptor.
     * @param parameterTypes The method's parameter types as type descriptors.
     * @return A visitor object for the method or {@code null} if this visitor
     *         is not interested in details about the method.
     */
    DexMethodVisitor visitMethod(int access, String name, String shorty,
            String returnType, String[] parameterTypes);

    /**
     * Visits the end of the class.
     */
    void visitEnd();
}
