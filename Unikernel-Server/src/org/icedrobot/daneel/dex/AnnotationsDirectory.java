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

import org.icedrobot.daneel.dex.EncodedValue.AnnotationValue;
import org.icedrobot.daneel.dex.EncodedValue.AnnotationVisitable;
import org.icedrobot.daneel.util.BufferUtil;

/**
 * A parser class capable of parsing {@code annotations_directory_item}
 * structures as part of DEX files. Keep package-private to hide internal API.
 */
class AnnotationsDirectory {

    /**
     * Parses a {@code annotations_directory_item} structure in a DEX file at
     * the buffer's current position.
     * 
     * @param buffer The byte buffer to read from.
     * @param dex The DEX file currently being parsed.
     * @return An object representing the parsed data.
     */
    public static AnnotationsDirectory parse(ByteBuffer buffer, DexFile dex) {
        return new AnnotationsDirectory(buffer, dex);
    }

    private final Annotation[] classAnnotations;

    private final Map<FieldId, Annotation[]> fieldAnnotations;

    private final Map<MethodId, Annotation[]> methodAnnotations;

    private final Map<MethodId, Annotation[][]> parameterAnnotations;

    private AnnotationsDirectory(ByteBuffer buffer, DexFile dex) {
        int classAnnotationsOff = buffer.getInt();
        int fieldsSize = buffer.getInt();
        int methodsSize = buffer.getInt();
        int parametersSize = buffer.getInt();

        // Parse annotation_set_item structure for class annotations.
        if (classAnnotationsOff != 0) {
            ByteBuffer buf = dex.getDataBuffer(classAnnotationsOff);
            classAnnotations = parseAnnotations(buf, dex);
        } else
            classAnnotations = null;

        // Parse annotation_set_item structures in field_annotations array.
        fieldAnnotations = new HashMap<FieldId, Annotation[]>(fieldsSize);
        for (int i = 0; i < fieldsSize; i++) {
            int fieldIdx = buffer.getInt();
            int annotationsOff = buffer.getInt();
            ByteBuffer buf = dex.getDataBuffer(annotationsOff);
            Annotation[] annotations = parseAnnotations(buf, dex);
            fieldAnnotations.put(dex.getFieldId(fieldIdx), annotations);
        }

        // Parse annotation_set_item structures in method_annotations array.
        methodAnnotations = new HashMap<MethodId, Annotation[]>(methodsSize);
        for (int i = 0; i < methodsSize; i++) {
            int methodIdx = buffer.getInt();
            int annotationsOff = buffer.getInt();
            ByteBuffer buf = dex.getDataBuffer(annotationsOff);
            Annotation[] annotations = parseAnnotations(buf, dex);
            methodAnnotations.put(dex.getMethodId(methodIdx), annotations);
        }

        // Parse annotation_set_ref_list and contained annotation_set_item
        // structures in parameter_annotations array.
        parameterAnnotations = new HashMap<MethodId, Annotation[][]>();
        for (int i = 0; i < parametersSize; i++) {
            int methodIdx = buffer.getInt();
            int annotationsOff = buffer.getInt();
            ByteBuffer buf = dex.getDataBuffer(annotationsOff);
            int size = buf.getInt();
            int[] list = BufferUtil.getInts(buf, size);
            Annotation[][] annotationSet = new Annotation[size][];
            for (int j = 0; j < size; j++) {
                buf = dex.getDataBuffer(list[j]);
                annotationSet[j] = parseAnnotations(buf, dex);
            }
            parameterAnnotations.put(dex.getMethodId(methodIdx), annotationSet);
        }
    }

    /**
     * Allows the given visitor to visit all class annotations present in this
     * directory.
     * 
     * @param visitor The given DEX class visitor object.
     */
    public void acceptClassAnnotations(DexClassVisitor visitor) {
        if (classAnnotations != null)
            for (Annotation annotation : classAnnotations) {
                int visibility = annotation.visibility;
                String type = annotation.annotationValue.type;
                acceptAnnotation(visitor.visitAnnotation(visibility, type),
                        annotation);
            }
    }

    /**
     * Allows the given visitor to visit all field for a given field present in
     * this directory.
     * 
     * @param visitor The given DEX field visitor object.
     * @param field The given field identifier.
     */
    public void acceptFieldAnnotations(DexFieldVisitor visitor, FieldId field) {
        Annotation[] annotations = fieldAnnotations.get(field);
        if (annotations != null)
            for (Annotation annotation : annotations) {
                int visibility = annotation.visibility;
                String type = annotation.annotationValue.type;
                acceptAnnotation(visitor.visitAnnotation(visibility, type),
                        annotation);
            }
    }

    /**
     * Allows the given visitor to visit all method and method parameter
     * annotations for a given method present in this directory.
     * 
     * @param visitor The given DEX method visitor object.
     * @param method The given method identifier.
     */
    public void acceptMethodAnnotations(DexMethodVisitor visitor,
            MethodId method) {
        Annotation[] annotations = methodAnnotations.get(method);
        if (annotations != null)
            for (Annotation annotation : annotations) {
                int visibility = annotation.visibility;
                String type = annotation.annotationValue.type;
                acceptAnnotation(visitor.visitAnnotation(visibility, type),
                        annotation);
            }
        Annotation[][] annotationSet = parameterAnnotations.get(method);
        if (annotationSet != null)
            for (int i = 0; i < annotationSet.length; i++)
                for (Annotation annotation : annotationSet[i]) {
                    int visibility = annotation.visibility;
                    String type = annotation.annotationValue.type;
                    acceptAnnotation(visitor.visitParameterAnnotation(i,
                            visibility, type), annotation);
                }
    }

    /**
     * Allows the given visitor to visit the given annotation.
     * 
     * @param visitor The given DEX annotation visitor or {@code null}.
     * @param annotation The given annotation to visit.
     */
    private static void acceptAnnotation(DexAnnotationVisitor visitor,
            Annotation annotation) {
        if (visitor == null)
            return;
        Map<String, Object> params = annotation.annotationValue.elements;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
            acceptAnnotationParam(visitor, name, value);
        }
        visitor.visitEnd();
    }

    /**
     * Allows the given visitor to visit the given annotation parameter.
     * 
     * @param visitor The given DEX annotation visitor.
     * @param name The annotation parameter name.
     * @param value The annotation parameter value.
     */
    private static void acceptAnnotationParam(DexAnnotationVisitor visitor,
            String name, Object value) {
        if (value instanceof AnnotationVisitable) {
            AnnotationVisitable visitable = (AnnotationVisitable) value;
            visitable.accept(visitor, name);
            return;
        }
        if (value instanceof Object[]) {
            Object[] array = (Object[]) value;
            DexAnnotationVisitor v = visitor.visitArray(name, array.length);
            if (v == null)
                return;
            for (Object element : array)
                acceptAnnotationParam(v, null, element);
            v.visitEnd();
            return;
        }
        visitor.visitPrimitive(name, value);
    }

    /**
     * Helper method decoding an {@code annotation_set_item} array.
     * 
     * @param buffer The byte buffer to read from.
     * @param dex The DEX file currently being parsed.
     * @return An array containing all the encoded annotation items.
     */
    private static Annotation[] parseAnnotations(ByteBuffer buffer, DexFile dex) {
        int size = buffer.getInt();
        Annotation[] annotations = new Annotation[size];
        for (int i = 0; i < size; i++) {
            int annotationOff = buffer.getInt();
            ByteBuffer buf = dex.getDataBuffer(annotationOff);
            annotations[i] = new Annotation(buf, dex);
        }
        return annotations;
    }

    /**
     * An internal representation of an annotation as encoded in the {@code
     * annotation_item} structure.
     */
    static class Annotation {
        final int visibility;
        final AnnotationValue annotationValue;

        public Annotation(ByteBuffer buffer, DexFile dex) {
            visibility = buffer.get();
            annotationValue = EncodedValue.parseAnnotation(buffer, dex);
        }
    };
}
