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

package org.icedrobot.daneel.rewriter;

import org.objectweb.asm.Opcodes;

/**
 * A virtual register of the abstract interpreter.
 * 
 * The field type is a 32bits value encoded as follow:
 *  0-7          8-15           16-30      31
 *  type kind    dimension      garbage    isUntyped
 *  
 * Some Dalvik opcodes don't have variant for int and float
 * (resp. long and double). Only one opcode is use and corresponding
 * register is viewed as plain a 32bits value (resp. 64bits value).
 * The JVM opcodes always specifies different variant of a same opcode
 * for types that have the same size in bits.
 * 
 * So the rewriter as to perform an analysis of the use of this kind of register
 * to infer if it's by example a 32bits int or a 32bits float.
 * This register are typed U32 (resp. U64) for Untyped 32bits value (64bits value).
 * 
 * NO_TYPE represents the type of a register that is not yet initialized.
 * It is categorized as untyped because it seems more simple from
 * the code point of view.
 * 
 * This class is mostly non mutable, but {@link Patchable} is mutable to allow
 * to change the type of an U32 (resp. U64) to its infered type,
 * see {@link #getType()} for more info.
 * 
 * A patchable is a callback that will be called when the type of an untyped register
 * is infered. This allow to patch the instructions stream to use the JVM opcode
 * corresponding to the inferred type.
 * As an invariant only U32 and U64 can have a non null {@link Patchable}.
 * 
 */
public class Register {
    // the order is the same order as java bytecode IALOAD/IASTORE order
    // (see #getJavaOpcode)
    public static final int INT_TYPE = 0; // signed int
    public static final int LONG_TYPE = 1; // signed long
    public static final int FLOAT_TYPE = 2; // 32 bits float
    public static final int DOUBLE_TYPE = 3; // 64 bits float
    public static final int OBJECT_TYPE = 4; // object reference
    public static final int BYTE_TYPE = 5; // byte or boolean
    public static final int CHAR_TYPE = 6; // char
    public static final int SHORT_TYPE = 7; // short
    public static final int BOOLEAN_TYPE = 8; // boolean
    public static final int VOID_TYPE = 9; // void, added to simplify management

    public static final int U32_TYPE = Integer.MIN_VALUE | 1; // untyped 32bits constant
    public static final int U64_TYPE = Integer.MIN_VALUE | 2; // untyped 64bits constant

    public static final int NO_TYPE = Integer.MIN_VALUE; // unassigned register

    private static final int UNTYPED_MASK = Integer.MIN_VALUE;

    static final Register UNINITIALIZED = new Register(NO_TYPE, null);
    
    private final int type;

    private final Patchable patchable;

    Register(int type, Patchable patchable) {
        this.type = type;
        this.patchable = patchable;
    }

    /** Returns the type of the current register.
     *  The type of an untyped register is not constant and
     *  may be changed to its infered type.
     *   
     * @return the type of the current register.
     */
    public int getType() {
        if (patchable != null) {
            int patchableType = patchable.getType();
            return (patchableType != NO_TYPE)? patchableType: type;
        }
        return type;
    }
    
    /** Returns the callback associated with this register.
     *  As an invariant only register typed U32 and U64 can have a non null {@link Patchable}.
     * @return the callback assocaited with this register or null otherwise.
     */
    public Patchable getPatchable() {
        return patchable;
    }
    
    /**
     * Simulate a load from the current register. If the type of the current
     * register is {@link #isUntyped(int) untyped} the corresponding
     * {@link Patchable patchable} will be called.
     * 
     * @param expectedType the expected type of the register
     * @return if the current register is untyped, a new register with the
     *         expected type otherwise the current register.
     */
    public Register load(int expectedType) {
        if (isUntyped(expectedType)) {
            throw new IllegalArgumentException("invalid type");
        }

        if (patchable != null) {
            patchable.doPatch(expectedType);
            return new Register(expectedType, null);
        }
        return this;
    }

    /** Merge two registers and compute the type of the resulting register.
     * 
     *  The algorithme works as follow:
     *  <ul>
     *   <li>If one of the registers is uninitialized ({@link #NO_TYPE}),
     *       the type of the resulting register will be the type of the other.
     *   <li>If one of the registers is {@link #isUntyped(int) untyped},
     *       the type of the resulting register will be the type of the other.
     *       If the two register are untyped, the new register will do
     *       the union of the two patchables.
     *   <li>Otherwise, if the intruction flow is not ill-formed, the
     *       types of the two registers should be the same.
     *  </ul>
     * 
     * @param register the second register (this is the first one).
     * @return a new register with a type corresponding 
     */
    public Register merge(Register register) {
        int thisType = getType();
        int registerType = register.getType();

        if (thisType == NO_TYPE) {
            return register;
        }
        if (registerType == NO_TYPE) {
            return this;
        }

        boolean thisIsUntyped = isUntyped(thisType);
        boolean registerIsUntyped = isUntyped(registerType);

        if (thisIsUntyped) {
            if (registerIsUntyped) {
                // maybe there are not compatible, but this means that
                // the code is malformed
                return new Register(thisType, Patchable.union(patchable, register.patchable));
            }
            if (patchable != null) { // otherwise, it's an uninitialized value
                patchable.doPatch(registerType);
            }
            return new Register(registerType, null);
        }

        // the two register aren't untyped, we don't check is they are compatible
        // because they can be incompatible if the register is no more used
        // This should not be the common case because dx remove unused registers
        if (!registerIsUntyped) {
            return this;
        }

        if (register.patchable != null) {
            register.patchable.doPatch(thisType);
        }
        return new Register(thisType, null);
    }

    /** Returns true is the register is untyped.
     *  {@link #U32_TYPE}, {@link #U64_TYPE} and {@link #NO_TYPE}
     *  are untyped.
     * @param type register type to test
     * @return true if the register type is untyped.
     */
    public static boolean isUntyped(int type) {
        return (type & UNTYPED_MASK) != 0;
    }
    
    /** Create an array of type.
     * @param componentType the component type of the array (which can be an array itself)
     * @param dimension the dimension of the array. As in Java or Dalvik, the resulting
     *                  array dimension is limited to 255.
     * @return the corresponding array type.
     */
    public static int makeArray(int componentType, int dimension) {
        if (isUntyped(componentType)) {
            throw new IllegalArgumentException("invalid type");
        }
        return ((((componentType >> 8) & 0xFF) + dimension) << 8)
                | (componentType & 0xFF);
    }
    
    /** Returns true if the type is an array type.
     * @param type the type that can be an array type.
     * @return true if the type is an array type.
     */
    public static boolean isArray(int type) {
        return ((type >> 8) & 0xFF) != 0;
    }
    
    /** Returns the array dimension of the type that must be an array.
     * @param type an array type
     * @return the array dimension of the array type.
     * @throws IllegalArgumentException is the type is not an array type.
     * @see #isArray(int)
     */
    public static int getArrayDimension(int type) {
        if (!isArray(type)) {
            throw new IllegalArgumentException("type is not an array");
        }
        return (type >> 8) & 0xFF;
    }
    
    /** Returns the component type of the type that must be an array.
     *  The component type has the same element type as the array type
     *  and a dimension decremented by one.
     * @param type an array type.
     * @return the component type of the array.
     * @throws IllegalArgumentException is the type is not an array type.
     */
    public static int getComponentType(int type) {
        if (!isArray(type)) {
            throw new IllegalArgumentException("type is not an array");
        }
        return ((((type >> 8) & 0xFF) -1 ) << 8) | (type & 0xFF);
    }

    /** Return the type (typed) used to represent an untyped type
     *  before patching occurs.
     *  For {@link #U32_TYPE} the corresponding type is {@link #INT_TYPE},
     *  for {@link #U64_TYPE} the corresponding type is {@link #LONG_TYPE},
     *   
     * @param type an untyped type
     * @return the corresponding type.
     */
    public static int asDefaultTypedType(int type) {
        if (!isUntyped(type)) {
            throw new IllegalArgumentException("invalid type");
        }
        return (type == U64_TYPE) ? LONG_TYPE: INT_TYPE;
    }

    /** Returns the JVM opcode from a type and a base opcode.
     * @param type a type
     * @param opcode a base opcode among ILOAD, ISTORE,
     *        IADD, ISUB, IMUL, IDIV, IREM, INEG, ISHL,
     *        ISHR, IUSHR, IAND, IOR, IXOR, IRETURN,
     *        IALOAD, IASTORE.
     * @return the variant opcode corresponding to the type.
     */
    public static int getJavaOpcode(int type, int opcode) {
        if (isUntyped(type)) {
            throw new IllegalArgumentException("invalid type");
        }

        if (isArray(type)) {
            type = OBJECT_TYPE;
        }
        
        if (opcode == Opcodes.IALOAD || opcode == Opcodes.IASTORE) {
            return opcode + ((type != BOOLEAN_TYPE) ? type : BYTE_TYPE);
        }

        return opcode + ((type > 4) ? 0 : type); // type > 4, integer numeric
                                                 // promotion
    }

    @Override
    public String toString() {
        return toString(type);
    }
    
    /** Returns a string representation of the register type.
     * @param type a register type
     * @return a string representation of the register type.
     */
    private static String toString(int type) {
        switch (type) {
        case OBJECT_TYPE:
            return "object";
        case INT_TYPE:
            return "int";
        case FLOAT_TYPE:
            return "float";
        case LONG_TYPE:
            return "long";
        case DOUBLE_TYPE:
            return "double";
        case BOOLEAN_TYPE:
            return "bool";
        case BYTE_TYPE:
            return "byte";
        case SHORT_TYPE:
            return "short";
        case CHAR_TYPE:
            return "char";
        case U32_TYPE:
            return "u32";
        case U64_TYPE:
            return "u64";
        case NO_TYPE:
            return "uninit";
        }
        if (!isArray(type)) {
            throw new AssertionError();
        }
        
        int dimension = getArrayDimension(type);
        StringBuilder builder = new StringBuilder(dimension + 6);
        for(int i=0; i<dimension; i++) {
            builder.append('[');
        }
        return builder.append(toString(type & 0xFF)).toString();
    }
}
