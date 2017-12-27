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
 * A visitor for methods contained in DEX files.
 */
public interface DexMethodVisitor {

    /**
     * Visits an annotation of the method. In case this visitor is interested in
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
     * Visits an annotation of a parameter to the method. In case this visitor
     * is interested in further details about the annotation it should return a
     * new visitor object, otherwise it should return {@code null}.
     * 
     * @param parameter The parameter index.
     * @param visibility The annotation's visibility flags.
     * @param type The annotation's type as a type descriptor.
     * @return A visitor object for the annotation or {@code null} if this
     *         visitor is not interested in details about the annotation.
     */
    DexAnnotationVisitor visitParameterAnnotation(int parameter,
            int visibility, String type);

    /**
     * Starts visiting the method's code. The code is visited by subsequent
     * calls to {@code visitLabel()} and {@code visitInstr*()} methods.
     * 
     * @param registers The number of registers used by the code.
     * @param ins The number of words of incoming arguments to the code.
     * @param outs The number of words of outgoing argument space required by
     *            the code for method invocation.
     */
    void visitCode(int registers, int ins, int outs);

    /**
     * Visits a label. The given label refers to the instruction immediately
     * following it. Labels can be used as branch targets or in try-catch
     * blocks.
     * 
     * @param label The target of a branch.
     */
    void visitLabel(Label label);

    /**
     * Visits a line number entry as stored in debug information. Note that
     * methods can contain several source entities, that's why the source file
     * name is passed as well.
     * 
     * @param source The name of the file containing the original source or
     *        {@code null} in case value is unknown.
     * @param line The line number referring to the source file, is never less
     *        than {@code 1}.
     * @param start The start of instructions corresponding to the line number.
     */
    void visitLineNumber(String source, int line, Label start);

    /**
     * Visits a local variable scope as stored in debug information. Note that
     * the same variable might have different scopes, in which case this method
     * is called more than once for the same variable.
     * 
     * @param name The name of the local variable or {@code null} in case value
     *        is unknown.
     * @param desc The type of the local variable as type descriptor or {@code
     *        null} in case value is unknown.
     * @param start The start of the local variable scope.
     * @param end Then end (exclusive) of the local variable scope.
     * @param reg The register which holds the local variable value.
     */
    void visitLocalVariable(String name, String desc, Label start, Label end,
            int reg);

    /**
     * Visits an instruction with a {@code 10x} format id.
     * 
     * @param opcode An opcode among NOP, RETURN_VOID.
     */
    void visitInstr(Opcode opcode);

    /**
     * Visits an instruction with a {@code 11x} format id.
     * 
     * @param opcode An opcode among MOVE_RESULT, MOVE_RESULT_WIDE,
     *            MOVE_RESULT_OBJECT, MOVE_EXCEPTION, RETURN, MONITOR_ENTER,
     *            MONITOR_EXIT, RETURN_WIDE, RETURN_OBJECT, THROW.
     * @param srcOrDst Source or destination register or register pair.
     */
    void visitInstrOp(Opcode opcode, int srcOrDst);

    /**
     * Visits an instruction with a {@code 12x|22x|32x} format id.
     * 
     * @param opcode An opcode among MOVE, MOVE_FROM16, MOVE_16, MOVE_WIDE,
     *            MOVE_WIDE_FROM16, MOVE_WIDE_16, MOVE_OBJECT,
     *            MOVE_OBJECT_FROM16, MOVE_OBJECT_16, NEG_INT, NOT_INT,
     *            NEG_LONG, NOT_LONG, NEG_FLOAT, NEG_DOUBLE, INT_TO_LONG,
     *            INT_TO_FLOAT, INT_TO_DOUBLE, LONG_TO_INT, LONG_TO_FLOAT,
     *            LONG_TO_DOUBLE, FLOAT_TO_INT, FLOAT_TO_LONG, DOUBLE_TO_INT,
     *            DOUBLE_TO_LONG, DOUBLE_TO_FLOAT, INT_TO_BYTE, INT_TO_CHAR,
     *            INT_TO_SHORT, ARRAY_LENGTH.
     * @param vdest Destination register or register pair.
     * @param vsrc Source register or register pair.
     */
    void visitInstrUnaryOp(Opcode opcode, int vdest, int vsrc);

    /**
     * Visits an instruction with a {@code 23x} format id.
     * 
     * @param opcode An opcode among CMPL_FLOAT, CMPG_FLOAT, CMPL_DOUBLE,
     *            CMPG_DOUBLE, CMP_LONG, ADD_INT, SUB_INT, MUL_INT, DIV_INT,
     *            REM_INT, AND_INT, OR_INT, XOR_INT, SHL_INT, SHR_INT, USHR_INT,
     *            ADD_LONG, SUB_LONG, MUL_LONG, DIV_LONG, REM_LONG, AND_LONG,
     *            OR_LONG, XOR_LONG, SHL_LONG, SHR_LONG, USHR_LONG, ADD_FLOAT,
     *            SUB_FLOAT, MUL_FLOAT, DIV_FLOAT, REM_FLOAT, ADD_DOUBLE,
     *            SUB_DOUBLE, MUL_DOUBLE, DIV_DOUBLE, REM_DOUBLE, ADD_INT_2ADDR,
     *            SUB_INT_2ADDR, MUL_INT_2ADDR, DIV_INT_2ADDR, REM_INT_2ADDR,
     *            AND_INT_2ADDR, OR_INT_2ADDR, XOR_INT_2ADDR, SHL_INT_2ADDR,
     *            SHR_INT_2ADDR, USHR_INT_2ADDR, ADD_LONG_2ADDR, SUB_LONG_2ADDR,
     *            MUL_LONG_2ADDR, DIV_LONG_2ADDR, REM_LONG_2ADDR,
     *            AND_LONG_2ADDR, OR_LONG_2ADDR, XOR_LONG_2ADDR, SHL_LONG_2ADDR,
     *            SHR_LONG_2ADDR, USHR_LONG_2ADDR, ADD_FLOAT_2ADDR,
     *            SUB_FLOAT_2ADDR, MUL_FLOAT_2ADDR, DIV_FLOAT_2ADDR,
     *            REM_FLOAT_2ADDR, ADD_DOUBLE_2ADDR, SUB_DOUBLE_2ADDR,
     *            MUL_DOUBLE_2ADDR, DIV_DOUBLE_2ADDR, REM_DOUBLE_2ADDR.
     * @param vdest Destination register or register pair.
     * @param vsrc1 First source register or register pair.
     * @param vsrc2 Second source register or register pair.
     */
    void visitInstrBinOp(Opcode opcode, int vdest, int vsrc1, int vsrc2);

    /**
     * Visits an instruction with a {@code 22b|22s} format id.
     * 
     * @param opcode An opcode among ADD_INT_LIT16, RSUB_INT_LIT16,
     *            MUL_INT_LIT16, DIV_INT_LIT16, REM_INT_LIT16, AND_INT_LIT16,
     *            OR_INT_LIT16, XOR_INT_LIT16, ADD_INT_LIT8, RSUB_INT_LIT8,
     *            MUL_INT_LIT8, DIV_INT_LIT8, REM_INT_LIT8, AND_INT_LIT8,
     *            OR_INT_LIT8, XOR_INT_LIT8, SHL_INT_LIT8, SHR_INT_LIT8,
     *            USHR_INT_LIT8.
     * @param vdest Destination register or register pair.
     * @param vsrc Source register or register pair.
     * @param value Constant literal value.
     */
    void visitInstrBinOpAndLiteral(Opcode opcode, int vdest, int vsrc, int value);

    /**
     * Visits an instruction with a {@code 21c|31c} format id.
     * 
     * @param opcode An opcode among CONST_STRING, CONST_STRING_JUMBO.
     * @param vdest Destination register (no register pair).
     * @param value Constant string literal value.
     */
    void visitInstrConstString(Opcode opcode, int vdest, String value);

    /**
     * Visits an instruction with a {@code 11n|21h|21s|31i} format id.
     * 
     * @param opcode An opcode among CONST_4, CONST_16, CONST, CONST_HIGH16.
     * @param vdest Destination register (no register pair).
     * @param value Constant literal value.
     */
    void visitInstrConstU32(Opcode opcode, int vdest, int value);

    /**
     * Visits an instruction with a {@code 21h|21s|31i|51l} format id.
     * 
     * @param opcode An opcode among CONST_WIDE_16, CONST_WIDE_32, CONST_WIDE,
     *            CONST_WIDE_HIGH16.
     * @param vdest Destination register pair (no single register).
     * @param value Constant literal value.
     */
    void visitInstrConstU64(Opcode opcode, int vdest, long value);

    /**
     * Visits an instruction with a {@code 21c} format id.
     * 
     * @param opcode An opcode among CONST_CLASS, CHECK_CAST, NEW_INSTANCE.
     * @param vsrcOrDest Source or destination register (no register pair).
     * @param type Constant type descriptor value.
     */
    void visitInstrClass(Opcode opcode, int vsrcOrDest, String type);

    /**
     * Visits an instruction with a {@code 22c} format id.
     * 
     * @param opcode The opcode INSTANCE_OF.
     * @param vdest Destination register (no register pair).
     * @param vsrc Source register (no register pair).
     * @param type Constant type descriptor value.
     */
    void visitInstrInstanceof(Opcode opcode, int vdest, int vsrc, String type);

    /**
     * Visits an instruction with a {@code 10t|20t|30t} format id.
     * 
     * @param opcode An opcode among GOTO_32, GOTO, GOTO_16.
     * @param label The target of the branch.
     */
    void visitInstrGoto(Opcode opcode, Label label);

    /**
     * Visits an instruction with a {@code 21t} format id.
     * 
     * @param opcode An opcode among IF_EQZ, IF_NEZ, IF_LTZ, IF_GEZ, IF_GTZ,
     *            IF_LEZ.
     * @param vsrc Source register (no register pair).
     * @param label The target of the branch.
     */
    void visitInstrIfTestZ(Opcode opcode, int vsrc, Label label);

    /**
     * Visits an instruction with a {@code 22t} format id.
     * 
     * @param opcode An opcode among IF_EQ, IF_NE, IF_LT, IF_GE, IF_GT, IF_LE.
     * @param vsrc1 First source register (no register pair).
     * @param vsrc2 Second source register (no register pair).
     * @param label The target of the branch.
     */
    void visitInstrIfTest(Opcode opcode, int vsrc1, int vsrc2, Label label);

    /**
     * Visits an instruction with a {@code 31t} format id.
     *
     * @param opcode The opcode PACKED_SWITCH.
     * @param vsrc Source register (no register pair).
     * @param firstKey The first (and lowest) key value
     * @param targets The targets of the switch.
     */
    void visitInstrPackedSwitch(Opcode opcode, int vsrc, int firstKey,
            Label[] targets);

    /**
     * Visits an instruction with a {@code 31t} format id.
     *
     * @param opcode The opcode SPARSE_SWITCH.
     * @param vsrc Source register (no register pair).
     * @param keys The key values of the switch (sorted low to high).
     * @param targets The targets of the switch.
     */
    void visitInstrSparseSwitch(Opcode opcode, int vsrc, int[] keys,
            Label[] targets);

    /**
     * Visits an instruction with a {@code 23x} format id.
     * 
     * @param opcode An opcode among APUT, APUT_WIDE, APUT_OBJECT, APUT_BOOLEAN,
     *            APUT_BYTE, APUT_CHAR, APUT_SHORT, AGET, AGET_WIDE,
     *            AGET_OBJECT, AGET_BOOLEAN, AGET_BYTE, AGET_CHAR, AGET_SHORT.
     * @param vsrcOrDest Source or destination register or register pair.
     * @param varray The array register.
     * @param vindex The index register.
     */
    void visitInstrArray(Opcode opcode, int vsrcOrDest, int varray, int vindex);

    /**
     * Visits an instruction with a {@code 21c|22c} format id.
     * 
     * @param opcode opcode among IGET, IGET_WIDE, IGET_OBJECT, IGET_BOOLEAN,
     *            IGET_BYTE, IGET_CHAR, IGET_SHORT, IPUT, IPUT_WIDE,
     *            IPUT_OBJECT, IPUT_BOOLEAN, IPUT_BYTE, IPUT_CHAR, IPUT_SHORT,
     *            SGET, SGET_WIDE, SGET_OBJECT, SGET_BOOLEAN, SGET_BYTE,
     *            SGET_CHAR, SGET_SHORT, SPUT, SPUT_WIDE, SPUT_OBJECT,
     *            SPUT_BOOLEAN, SPUT_BYTE, SPUT_CHAR, SPUT_SHORT.
     * @param vsrcOrDest Source or destination register or register pair.
     * @param vref The object register in instance mode or 0 in static mode.
     * @param owner The field's owner as type descriptor.
     * @param name The fied's name.
     * @param desc The field's type descriptor.
     */
    void visitInstrField(Opcode opcode, int vsrcOrDest, int vref, String owner,
            String name, String desc);

    /**
     * Visits an instruction with a {@code 35c|3rc} format id.
     * 
     * @param opcode An opcode among INVOKE_VIRTUAL, INVOKE_SUPER,
     *            INVOKE_DIRECT, INVOKE_STATIC, INVOKE_INTERFACE,
     *            INVOKE_VIRTUAL_RANGE, INVOKE_SUPER_RANGE, INVOKE_DIRECT_RANGE,
     *            INVOKE_STATIC_RANGE, INVOKE_INTERFACE_RANGE.
     * @param num The number of registers involved.
     * @param va Register A in normal mode or first register in range mode.
     * @param vpacked Register D,E,F,G (4 bits each) in normal mode or 0 in
     *            range mode.
     * @param owner The method's owner as type descriptor.
     * @param name The method's name.
     * @param desc The method's type descriptor.
     */
    void visitInstrMethod(Opcode opcode, int num, int va, int vpacked,
            String owner, String name, String desc);

    /**
     * Visits an instruction with a {@code 35c|3rc} format id.
     * 
     * @param opcode An opcode among FILLED_NEW_ARRAY, FILLED_NEW_ARRAY_RANGE.
     * @param num The number of registers involved.
     * @param va Register A in normal mode or first register in range mode.
     * @param vpacked Register D,E,F,G (4 bits each) in normal mode or 0 in
     *            range mode.
     * @param type Constant type descriptor value.
     */
    void visitInstrFilledNewArray(Opcode opcode, int num, int va, int vpacked,
            String type);

    /**
     * Visits an instruction with a {@code 22c} format id.
     * 
     * @param opcode The opcode NEW_ARRAY.
     * @param vdest Destination register (no register pair).
     * @param vsize The size register.
     * @param type Constant type descriptor value.
     */
    void visitInstrNewArray(Opcode opcode, int vdest, int vsize, String type);

    /**
     * Visits an instruction with a {@code 31t} format id.
     * 
     * @param opcode The opcode FILL_ARRAY_DATA.
     * @param vsrc The array register.
     * @param elementWidth Width of one element in bytes.
     * @param elementNumber Number of elements in the table.
     * @param data The data as a buffer of bytes.
     */
    void visitInstrFillArrayData(Opcode opcode, int vsrc, int elementWidth,
            int elementNumber, ByteBuffer data);

    /**
     * Visits a try-catch block for the method's code.
     * 
     * @param start The label of the handler's scope start.
     * @param end The label of the handler's scope end (exclusive).
     * @param handler The label for the actual handler code.
     * @param type The type descriptor of the exceptions this block handles or
     *            {@code null} in case it is a catch-all handler.
     */
    void visitTryCatch(Label start, Label end, Label handler, String type);

    /**
     * Visits the end of the method.
     */
    void visitEnd();
}
