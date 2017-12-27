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
import java.nio.ShortBuffer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.icedrobot.daneel.dex.DebugInfo.LineNumber;
import org.icedrobot.daneel.dex.DebugInfo.LocalVariable;
import org.icedrobot.daneel.util.BufferUtil;

/**
 * A parser class capable of parsing {@code code_item} structures as part of DEX
 * files. Keep package-private to hide internal API.
 */
class Code {

    /**
     * Parses a {@code code_item} structure in a DEX file at the buffer's
     * current position.
     * 
     * @param buffer The byte buffer to read from.
     * @param dex The DEX file currently being parsed.
     * @param method The method identifier this code belongs to.
     * @param flags The access flags of the method this code belongs to.
     * @return An object representing the parsed data.
     */
    public static Code parse(ByteBuffer buffer, DexFile dex, MethodId method,
            int flags) {
        return new Code(buffer, dex, method, flags);
    }

    private final DexFile dex;

    private final MethodId method;

    private final int flags;

    private final int registersSize;

    private final int insSize;

    private final int outsSize;

    private final int triesSize;

    private final int debugInfoOff;

    private final int insnsSize;

    private final ShortBuffer insns;

    private final List<TryCatchInfo> tryCatchInfos;

    private Code(ByteBuffer buffer, DexFile dex, MethodId method, int flags) {
        this.dex = dex;
        this.method = method;
        this.flags = flags;
        registersSize = buffer.getShort();
        insSize = buffer.getShort();
        outsSize = buffer.getShort();
        triesSize = buffer.getShort();
        debugInfoOff = buffer.getInt();
        insnsSize = buffer.getInt();

        // Keep a separate buffer for the instructions array.
        insns = (ShortBuffer) buffer.asShortBuffer().limit(insnsSize);

        // Find labels inside the instruction stream and also parse possible
        // in-code structures at such positions.
        findLabels(buffer);

        // Skip the instructions array.
        buffer.position(buffer.position() + 2 * insnsSize);

        // Skip optional padding if present.
        if (triesSize != 0 && (insnsSize & 1) == 1)
            if (buffer.getShort() != 0)
                throw new DexParseException("Padding should be zero.");

        // Parse optional try_item structures in tries array and
        // encoded_catch_handler_list structure. Also adds necessary labels.
        tryCatchInfos = new LinkedList<TryCatchInfo>();
        if (triesSize != 0) {
            int[] startAddr = new int[triesSize];
            int[] insnCount = new int[triesSize];
            int[] handlerOff = new int[triesSize];
            for (int i = 0; i < triesSize; i++) {
                startAddr[i] = buffer.getInt();
                insnCount[i] = buffer.getShort();
                handlerOff[i] = buffer.getShort();
            }
            ByteBuffer buf = buffer.slice();
            for (int i = 0; i < triesSize; i++) {
                Label startLabel = putLabel(startAddr[i], false);
                Label endLabel = putLabel(startAddr[i] + insnCount[i], false);
                buf.position(handlerOff[i]);
                // Note that size is a signed value here, which indicates
                // whether the try-block has a catch-all handler or not.
                int size = BufferUtil.getSLEB128(buf);
                for (int j = 0; j < Math.abs(size); j++) {
                    int typeIdx = BufferUtil.getULEB128(buf);
                    int addr = BufferUtil.getULEB128(buf);
                    String type = dex.getTypeDescriptor(typeIdx);
                    addTryCatch(startLabel, endLabel, type, addr);
                }
                if (size <= 0) {
                    int addr = BufferUtil.getULEB128(buf);
                    addTryCatch(startLabel, endLabel, null, addr);
                }
            }
        }
    }

    /**
     * Returns the method identifier of the method this code belongs to.
     * 
     * @return The method identifier object.
     */
    public MethodId getMethod() {
        return method;
    }

    /**
     * Returns the access flags of the method this code belongs to.
     * 
     * @return The access flags value.
     */
    public int getFlags() {
        return flags;
    }

    /**
     * Returns the number of registers used by the code as specified in the DEX
     * file.
     * 
     * @return The number of registers.
     */
    public int getRegistersSize() {
        return registersSize;
    }

    /**
     * Returns the debug information associated with this code. The underlying
     * implementation uses a lazy parsing approach.
     * 
     * @return The debug info object or {@code null} if there is none.
     */
    public DebugInfo getDebugInfo() {
        if (debugInfoOff == 0)
            return null;

        // Parse any associated debug information.
        ByteBuffer buf = dex.getDataBuffer(debugInfoOff);
        DebugInfo debugInfo = DebugInfo.parse(buf, dex, this);

        // Return the non-null object.
        return debugInfo;
    }

    /**
     * Allows the given visitor to visit this code object.
     * 
     * @param visitor The given DEX method visitor object.
     * @param skip Flags indicating which information to skip while visiting.
     */
    public void accept(DexMethodVisitor visitor, int skip) {
        visitor.visitCode(registersSize, insSize, outsSize);

        // Visit try-catch block information if available.
        for (TryCatchInfo tryCatch : tryCatchInfos)
            visitor.visitTryCatch(tryCatch.startLabel, tryCatch.endLabel,
                    tryCatch.handlerLabel, tryCatch.type);

        // Visit instructions.
        acceptInsns(visitor);

        // Visit debug information if available and requested.
        if (debugInfoOff != 0 && (skip & DexReader.SKIP_DEBUGINFO) == 0) {
            DebugInfo debugInfo = getDebugInfo();
            for (LocalVariable local : debugInfo.getLocalVariables())
                visitor.visitLocalVariable(local.name, local.type,
                        local.startLabel, local.endLabel, local.regNum);
            for (LineNumber line : debugInfo.getLineNumbers())
                visitor.visitLineNumber(line.source, line.line, line.label);
        }
    }

    /**
     * Allows the given visitor to visit all instructions and labels contained
     * within this code object.
     * 
     * @param v The given DEX method visitor object.
     * @throws DexParseException In case any instruction cannot be decoded.
     */
    private void acceptInsns(DexMethodVisitor v) {
        ShortBuffer insns = this.insns.duplicate();

        // Iterate over all 16-bit code units.
        while (insns.hasRemaining()) {
            int pos = insns.position(); // bytecode address
            int s1 = insns.get(); // signed 1st short

            // Read and decode further code units.
            Opcode op = Opcode.getOpcode(s1 & 0xff); // current opcode
            int codeLength = OP_LENGTH[op.ordinal()];
            int s2 = (codeLength >= 2) ? insns.get() : 0; // signed 2nd short
            int s3 = (codeLength >= 3) ? insns.get() : 0; // signed 3rd short
            int b1 = ((s1 >> 8) & 0xff); // unsig. 1st byte
            int n1 = ((s1 >> 12) & 0x0f); // unsig. 1st nibble
            int n2 = ((s1 >> 8) & 0x0f); // unsig. 2nd nibble
            int u2 = (s2 & 0xffff); // unsig. 2nd short
            int u3 = (s3 & 0xffff); // unsig. 3rd short
            int b3, b4;

            // Local variables used within the big switch.
            int i;
            long l;
            String type, string;
            FieldId field;
            MethodId method;
            Label label;

            // Visit a label at the current position.
            if ((label = labelMap.get(pos)) != null)
                v.visitLabel(label);

            // Switch over all possible opcodes.
            switch (op) {
            case NOP:
                // Skip possible in-code data structures.
                if (b1 != 0 && skipInCodeData(insns, pos))
                    break;
                // fall-through;

            case RETURN_VOID:
                // Format 10x: 00|op
                // Syntax: op
                if (b1 != 0)
                    throw new DexParseException("Malformed instruction word: "
                            + String.format("0x%04x", s1));
                v.visitInstr(op);
                break;

            case MOVE:
            case MOVE_WIDE:
            case MOVE_OBJECT:
            case ARRAY_LENGTH:
            case NEG_INT:
            case NOT_INT:
            case NEG_LONG:
            case NOT_LONG:
            case NEG_FLOAT:
            case NEG_DOUBLE:
            case INT_TO_LONG:
            case INT_TO_FLOAT:
            case INT_TO_DOUBLE:
            case LONG_TO_INT:
            case LONG_TO_FLOAT:
            case LONG_TO_DOUBLE:
            case FLOAT_TO_INT:
            case FLOAT_TO_LONG:
            case FLOAT_TO_DOUBLE:
            case DOUBLE_TO_INT:
            case DOUBLE_TO_LONG:
            case DOUBLE_TO_FLOAT:
            case INT_TO_BYTE:
            case INT_TO_CHAR:
            case INT_TO_SHORT:
                // Format 12x: B|A|op
                // Syntax: op vA, vB
                v.visitInstrUnaryOp(op, n2, n1);
                break;

            case MOVE_FROM16:
            case MOVE_WIDE_FROM16:
            case MOVE_OBJECT_FROM16:
                // Format 22x: AA|op BBBB
                // Syntax: op vAA, vBBBB
                v.visitInstrUnaryOp(op, b1, u2);
                break;

            case MOVE_16:
            case MOVE_WIDE_16:
            case MOVE_OBJECT_16:
                // Format 32x: 00|op AAAA BBBB
                // Syntax: op vAAAA, vBBBB
                if (b1 != 0)
                    throw new DexParseException("Malformed instruction word: "
                            + String.format("0x%04x", s1));
                v.visitInstrUnaryOp(op, u2, s3);
                break;

            case MOVE_RESULT:
            case MOVE_RESULT_WIDE:
            case MOVE_RESULT_OBJECT:
            case MOVE_EXCEPTION:
            case RETURN:
            case RETURN_WIDE:
            case RETURN_OBJECT:
            case MONITOR_ENTER:
            case MONITOR_EXIT:
            case THROW:
                // Format 11x: AA|op
                // Syntax: op vAA
                v.visitInstrOp(op, b1);
                break;

            case CONST_4:
                // Format 11n: B|A|op
                // Syntax: op vA, #+B
                // We need to decode B separately here because it is used as
                // a signed value in this context, whereas n1 is unsigned.
                i = (s1 >> 12);
                v.visitInstrConstU32(op, n2, i);
                break;

            case CONST_16:
                // Format 21s: AA|op BBBB
                // Syntax: op vAA, #+BBBB
                v.visitInstrConstU32(op, b1, s2);
                break;

            case CONST:
                // Format 31i: AA|op BBBBlo BBBBhi
                // Syntax: op vAA, #+BBBBBBBB
                i = u2 | (s3 << 16);
                v.visitInstrConstU32(op, b1, i);
                break;

            case CONST_HIGH16:
                // Format 21h: AA|op BBBB
                // Syntax: op vAA, #+BBBB0000
                i = (s2 << 16);
                v.visitInstrConstU32(op, b1, i);
                break;

            case CONST_WIDE_16:
                // Format 21s: AA|op BBBB
                // Syntax: op vAA, #+BBBB
                l = s2;
                v.visitInstrConstU64(op, b1, l);
                break;

            case CONST_WIDE_32:
                // Format 31i: AA|op BBBBlo BBBBhi
                // Syntax: op vAA, #+BBBBBBBB
                l = u2 | (s3 << 16);
                v.visitInstrConstU64(op, b1, l);
                break;

            case CONST_WIDE:
                // Format 51l: AA|op BBBBlo BBBB BBBB BBBBhi
                // Syntax: op vAA, #+BBBBBBBBBBBBBBBB
                int u4 = (insns.get() & 0xffff);
                int u5 = (insns.get() & 0xffff);
                l = ((long) u2) | (((long) u3) << 16) | (((long) u4) << 32)
                        | (((long) u5) << 48);
                v.visitInstrConstU64(op, b1, l);
                break;

            case CONST_WIDE_HIGH16:
                // Format 21h: AA|op BBBB
                // Syntax: op vAA, #+BBBB000000000000
                l = (((long) s2) << 48);
                v.visitInstrConstU64(op, b1, l);
                break;

            case CONST_STRING:
                // Format 21c: AA|op BBBB
                // Syntax: op vAA, string@BBBB
                string = dex.getString(u2);
                v.visitInstrConstString(op, b1, string);
                break;

            case CONST_STRING_JUMBO:
                // Format 31c: AA|op BBBBlo BBBBhi
                // Syntax: op vAA, string@BBBBBBBB
                i = u2 | (u3 << 16);
                string = dex.getString(i);
                v.visitInstrConstString(op, b1, string);
                break;

            case CONST_CLASS:
            case CHECK_CAST:
            case NEW_INSTANCE:
                // Format 21c: AA|op BBBB
                // Syntax: op vAA, type@BBBB
                type = dex.getTypeDescriptor(u2);
                v.visitInstrClass(op, b1, type);
                break;

            case INSTANCE_OF:
                // Format 22c: B|A|op CCCC
                // Syntax: op vA, vB, type@CCCC
                type = dex.getTypeDescriptor(u2);
                v.visitInstrInstanceof(op, n2, n1, type);
                break;

            case NEW_ARRAY:
                // Format 22c: B|A|op CCCC
                // Syntax: op vA, vB, type@CCCC
                type = dex.getTypeDescriptor(u2);
                v.visitInstrNewArray(op, n2, n1, type);
                break;

            case FILLED_NEW_ARRAY:
                // Format 35c B|A|op CCCC G|F|E|D
                // Syntax: op {vD, vE, vF, vG, vA}, type@CCCC
                type = dex.getTypeDescriptor(u2);
                v.visitInstrFilledNewArray(op, n1, n2, u3, type);
                break;

            case FILL_ARRAY_DATA:
                // Format 31t: AA|op BBBBlo BBBBhi
                // Syntax: op vAA, +BBBBBBBB
                i = u2 | (s3 << 16);
                label = getLabel(pos + i);
                if (!(label instanceof FillArrayDataLabel))
                    throw new DexParseException("Mistyped branch target.");
                FillArrayDataLabel fadl = (FillArrayDataLabel) label;
                ByteBuffer data = fadl.data.duplicate().order(insns.order());
                v.visitInstrFillArrayData(op, b1, fadl.elementWidth, fadl.size,
                        data);
                break;

            case GOTO:
                // Format 10t: AA|op
                // Syntax: op +AA
                // We need to decode AA separately here because it is used as
                // a signed value in this context, whereas b1 is unsigned.
                label = getLabel(pos + (s1 >> 8));
                v.visitInstrGoto(op, label);
                break;

            case GOTO_16:
                // Format 20t: 00|op AAAA
                // Syntax: op +AAAA
                if (b1 != 0)
                    throw new DexParseException("Malformed instruction word: "
                            + String.format("0x%04x", s1));
                label = getLabel(pos + s2);
                v.visitInstrGoto(op, label);
                break;

            case PACKED_SWITCH:
                // Format 31t: AA|op BBBBlo BBBBhi
                // Syntax: op vAA, +BBBBBBBB
                i = u2 | (s3 << 16);
                label = getLabel(pos + i);
                if (!(label instanceof PackedSwitchLabel))
                    throw new DexParseException("Mistyped branch target.");
                PackedSwitchLabel psl = (PackedSwitchLabel) label;
                v.visitInstrPackedSwitch(op, b1, psl.firstKey, psl.targets);
                break;

            case SPARSE_SWITCH:
                // Format 31t: AA|op BBBBlo BBBBhi
                // Syntax: op vAA, +BBBBBBBB
                i = u2 | (s3 << 16);
                label = getLabel(pos + i);
                if (!(label instanceof SparseSwitchLabel))
                    throw new DexParseException("Mistyped branch target.");
                SparseSwitchLabel ssl = (SparseSwitchLabel) label;
                v.visitInstrSparseSwitch(op, b1, ssl.keys, ssl.targets);
                break;

            case IF_EQZ:
            case IF_NEZ:
            case IF_LTZ:
            case IF_GEZ:
            case IF_GTZ:
            case IF_LEZ:
                // Format 21t: AA|op BBBB
                // Syntax: op vAA, +BBBB
                label = getLabel(pos + s2);
                v.visitInstrIfTestZ(op, b1, label);
                break;

            case IF_EQ:
            case IF_NE:
            case IF_LT:
            case IF_GE:
            case IF_GT:
            case IF_LE:
                // Format 22t: B|A|op CCCC
                // Syntax: op vA, vB, +CCCC
                label = getLabel(pos + s2);
                v.visitInstrIfTest(op, n2, n1, label);
                break;

            case AGET:
            case AGET_WIDE:
            case AGET_OBJECT:
            case AGET_BOOLEAN:
            case AGET_BYTE:
            case AGET_CHAR:
            case AGET_SHORT:
            case APUT:
            case APUT_WIDE:
            case APUT_OBJECT:
            case APUT_BOOLEAN:
            case APUT_BYTE:
            case APUT_CHAR:
            case APUT_SHORT:
                // Format 23x: AA|op CC|BB
                // Syntax: op vAA, vBB, vCC
                b4 = (s2 & 0xff);
                b3 = ((s2 >> 8) & 0xff);
                v.visitInstrArray(op, b1, b4, b3);
                break;

            case IGET:
            case IGET_WIDE:
            case IGET_OBJECT:
            case IGET_BOOLEAN:
            case IGET_BYTE:
            case IGET_CHAR:
            case IGET_SHORT:
            case IPUT:
            case IPUT_WIDE:
            case IPUT_OBJECT:
            case IPUT_BOOLEAN:
            case IPUT_BYTE:
            case IPUT_CHAR:
            case IPUT_SHORT:
                // Format 22c: B|A|op CCCC
                // Syntax: op vA, vB, field@CCCC
                field = dex.getFieldId(u2);
                v.visitInstrField(op, n2, n1, field.getClassName(),
                        field.getName(), field.getTypeDescriptor());
                break;

            case SGET:
            case SGET_WIDE:
            case SGET_OBJECT:
            case SGET_BOOLEAN:
            case SGET_BYTE:
            case SGET_CHAR:
            case SGET_SHORT:
            case SPUT:
            case SPUT_WIDE:
            case SPUT_OBJECT:
            case SPUT_BOOLEAN:
            case SPUT_BYTE:
            case SPUT_CHAR:
            case SPUT_SHORT:
                // Format 21c: AA|op BBBB
                // Syntax: op vAA, field@BBBB
                field = dex.getFieldId(u2);
                v.visitInstrField(op, b1, 0, field.getClassName(),
                        field.getName(), field.getTypeDescriptor());
                break;

            case INVOKE_VIRTUAL:
            case INVOKE_SUPER:
            case INVOKE_DIRECT:
            case INVOKE_STATIC:
            case INVOKE_INTERFACE:
                // Format 35c B|A|op CCCC G|F|E|D
                // Syntax: op {vD, vE, vF, vG, vA}, meth@CCCC
                method = dex.getMethodId(u2);
                v.visitInstrMethod(op, n1, n2, u3, method.getClassName(),
                        method.getName(), method.getMethodDesc());
                break;

            case INVOKE_VIRTUAL_RANGE:
            case INVOKE_SUPER_RANGE:
            case INVOKE_DIRECT_RANGE:
            case INVOKE_STATIC_RANGE:
            case INVOKE_INTERFACE_RANGE:
                // Format 3rc AA|op BBBB CCCC
                // Syntax: op {vCCCC .. vNNNN}, meth@BBBB
                method = dex.getMethodId(u2);
                v.visitInstrMethod(op, b1, u3, 0, method.getClassName(),
                        method.getName(), method.getMethodDesc());
                break;

            case CMPL_FLOAT:
            case CMPG_FLOAT:
            case CMPL_DOUBLE:
            case CMPG_DOUBLE:
            case CMP_LONG:
            case ADD_INT:
            case SUB_INT:
            case MUL_INT:
            case DIV_INT:
            case REM_INT:
            case AND_INT:
            case OR_INT:
            case XOR_INT:
            case SHL_INT:
            case SHR_INT:
            case USHR_INT:
            case ADD_LONG:
            case SUB_LONG:
            case MUL_LONG:
            case DIV_LONG:
            case REM_LONG:
            case AND_LONG:
            case OR_LONG:
            case XOR_LONG:
            case SHL_LONG:
            case SHR_LONG:
            case USHR_LONG:
            case ADD_FLOAT:
            case SUB_FLOAT:
            case MUL_FLOAT:
            case DIV_FLOAT:
            case REM_FLOAT:
            case ADD_DOUBLE:
            case SUB_DOUBLE:
            case MUL_DOUBLE:
            case DIV_DOUBLE:
            case REM_DOUBLE:
                // Format 23x: AA|op CC|BB
                // Syntax: op vAA, vBB, vCC
                b4 = (s2 & 0xff);
                b3 = ((s2 >> 8) & 0xff);
                v.visitInstrBinOp(op, b1, b4, b3);
                break;

            case ADD_INT_2ADDR:
            case SUB_INT_2ADDR:
            case MUL_INT_2ADDR:
            case DIV_INT_2ADDR:
            case REM_INT_2ADDR:
            case AND_INT_2ADDR:
            case OR_INT_2ADDR:
            case XOR_INT_2ADDR:
            case SHL_INT_2ADDR:
            case SHR_INT_2ADDR:
            case USHR_INT_2ADDR:
            case ADD_LONG_2ADDR:
            case SUB_LONG_2ADDR:
            case MUL_LONG_2ADDR:
            case DIV_LONG_2ADDR:
            case REM_LONG_2ADDR:
            case AND_LONG_2ADDR:
            case OR_LONG_2ADDR:
            case XOR_LONG_2ADDR:
            case SHL_LONG_2ADDR:
            case SHR_LONG_2ADDR:
            case USHR_LONG_2ADDR:
            case ADD_FLOAT_2ADDR:
            case SUB_FLOAT_2ADDR:
            case MUL_FLOAT_2ADDR:
            case DIV_FLOAT_2ADDR:
            case REM_FLOAT_2ADDR:
            case ADD_DOUBLE_2ADDR:
            case SUB_DOUBLE_2ADDR:
            case MUL_DOUBLE_2ADDR:
            case DIV_DOUBLE_2ADDR:
            case REM_DOUBLE_2ADDR:
                // Format 12x: B|A|op
                // Syntax: op vA, vB
                v.visitInstrBinOp(op, n2, n2, n1);
                break;

            case ADD_INT_LIT16:
            case RSUB_INT_LIT16:
            case MUL_INT_LIT16:
            case DIV_INT_LIT16:
            case REM_INT_LIT16:
            case AND_INT_LIT16:
            case OR_INT_LIT16:
            case XOR_INT_LIT16:
                // Format 22s: B|A|op CCCC
                // Syntax: op vA, vB, #+CCCC
                v.visitInstrBinOpAndLiteral(op, n2, n1, s2);
                break;

            case ADD_INT_LIT8:
            case RSUB_INT_LIT8:
            case MUL_INT_LIT8:
            case DIV_INT_LIT8:
            case REM_INT_LIT8:
            case AND_INT_LIT8:
            case OR_INT_LIT8:
            case XOR_INT_LIT8:
            case SHL_INT_LIT8:
            case SHR_INT_LIT8:
            case USHR_INT_LIT8:
                // Format 22b: AA|op CC|BB
                // Syntax: op vAA, vBB, #+CC
                b4 = (s2 & 0xff);
                i = (s2 >> 8);
                v.visitInstrBinOpAndLiteral(op, b1, b4, i);
                break;

            default:
                throw new DexParseException("Unkown opcode: " + op);
            }
        }

        // Visit the end label after all instructions.
        v.visitLabel(getEndLabel());
    }

    /** The array associating bytecode instructions to their lengths. */
    private static final int[] OP_LENGTH = new int[] { 1, 1, 2, 3, 1, 2, 3, 1,
            2, 3, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 3, 2, 2, 3, 5, 2, 2, 3, 2, 1,
            1, 2, 2, 1, 2, 2, 3, 3, 3, 1, 1, 2, 3, 3, 3, 2, 2, 2, 2, 2, 2, 2,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 1, 3, 3,
            3, 3, 3, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1 };

    /**
     * Adds a new try-catch-block information structure to our internal list.
     * 
     * @param startLabel The start of the try block.
     * @param endLabel The end (exclusive) of the try block.
     * @param type The type descriptor of the exception to catch or {@code null}
     *            for a catch-all handler.
     * @param handlerAddr The bytecode address of the associated exception
     *            handler, a label will be added for this address.
     */
    private void addTryCatch(Label startLabel, Label endLabel, String type,
            int handlerAddr) {
        Label handlerLabel = putLabel(handlerAddr, true);
        TryCatchInfo info = new TryCatchInfo(startLabel, endLabel,
                handlerLabel, type);
        tryCatchInfos.add(info);
    }

    /** The map associating bytecode positions with labels. */
    private final Map<Integer, Label> labelMap = new HashMap<Integer, Label>();

    /** The end label used to mark the address after the last instruction. */
    private final Label endLabel = new Label() {

        @Override
        public boolean isJumpTarget() {
            return false;
        }

        @Override
        public String toString() {
            return "LEnd";
        }
    };

    /**
     * Returns the label associated with the given bytecode position.
     * 
     * @param pos The given bytecode position in 16-bit code units.
     * @return The associated label, never returns {@code null}.
     * @throws DexParseException In case no label can be found.
     */
    private Label getLabel(int pos) {
        if (pos < 0 || pos >= insnsSize)
            throw new DexParseException("Label position out of range: " + pos);
        Label label = labelMap.get(pos);
        if (label == null)
            throw new DexParseException("No label at branch target: " + pos);
        return label;
    }

    /**
     * Returns the label marking the end of the instruction stream. The label
     * actually points to the address after the last instruction.
     * 
     * @return The associated label, never returns {@code null}.
     */
    Label getEndLabel() {
        return endLabel;
    }

    /**
     * Adds a new label to the given bytecode position. Keep this method
     * package-private in favor of private as long as there are inner classes
     * which call it, to avoid trampoline code.
     * 
     * @param pos The given bytecode position in 16-bit code units.
     * @param jumpTarget Indicates whether the label should be marked as a jump
     *        target or not. That information can be useful for the visitor and
     *        is passed out through the {@link Label#isJumpTarget()} method.
     * @return The associated label, never returns {@code null}.
     */
    Label putLabel(int pos, boolean jumpTarget) {
        if (pos < 0 || pos >= insnsSize)
            throw new DexParseException("Label position out of range: " + "pos=" + pos + "  insnsSize=" + insnsSize);
        Label label = labelMap.get(pos);
        if (label == null) {
            label = new DebugLabel(pos, jumpTarget);
            labelMap.put(pos, label);
        } else {
            if (jumpTarget & !label.isJumpTarget()) {
                if (!(label instanceof DebugLabel))
                    throw new DexParseException("Cannot mark label as target.");
                DebugLabel debugLabel = (DebugLabel) label;
                debugLabel.setJumpTarget();
            }
        }
        return label;
    }

    /**
     * Adds a given label to the given bytecode position.
     * 
     * @param pos The given bytecode position in 16-bit code units.
     * @param label The label to be associated with that position.
     * @throws DexParseException In case there already is a label.
     */
    private void putLabel(int pos, Label label) {
        if (pos < 0 || pos >= insnsSize)
            throw new DexParseException("Label position out of range: " + pos);
        if (labelMap.containsKey(pos))
            throw new DexParseException("Duplicate label at branch target: "
                    + pos);
        labelMap.put(pos, label);
    }

    /**
     * Finds all branch targets by iterating over the bytecode instructions.
     * This method also parses all encountered in-code data structures.
     * 
     * @param buffer The original buffer needed for decoding of in-code data
     *            structures.
     */
    private void findLabels(ByteBuffer buffer) {
        ShortBuffer insns = this.insns.duplicate();

        // Mark the buffer so we can always find in-code data structures which
        // are referenced relative to the bytecode address.
        buffer.mark();

        // Iterate over all 16-bit code units.
        while (insns.hasRemaining()) {
            int pos = insns.position();
            int s1 = insns.get();

            // Read and decode further code units.
            Opcode op = Opcode.getOpcode(s1 & 0xff);
            int codeLength = OP_LENGTH[op.ordinal()];
            int s2 = (codeLength >= 2) ? insns.get() : 0;
            int s3 = (codeLength >= 3) ? insns.get() : 0;
            int off;

            // Skip all further code units.
            if (codeLength > 3)
                insns.position(insns.position() + codeLength - 3);

            // Switch over all opcodes with a branch target.
            switch (op) {
            case NOP:
                // Skip in-code data structures.
                skipInCodeData(insns, pos);
                break;

            case GOTO:
                // Format 10t: AA|op
                // Syntax: op +AA
                putLabel(pos + (s1 >> 8), true);
                break;

            case GOTO_16:
                // Format 20t: 00|op AAAA
                // Syntax: op +AAAA
                // fall-through;

            case IF_EQ:
            case IF_NE:
            case IF_LT:
            case IF_GE:
            case IF_GT:
            case IF_LE:
                // Format 22t: B|A|op CCCC
                // Syntax: op vA, vB, +CCCC
                // fall-through;

            case IF_EQZ:
            case IF_NEZ:
            case IF_LTZ:
            case IF_GEZ:
            case IF_GTZ:
            case IF_LEZ:
                // Format 21t: AA|op BBBB
                // Syntax: op vAA, +BBBB
                putLabel(pos + s2, true);
                break;

            case FILL_ARRAY_DATA:
                // Format 31t: AA|op BBBBlo BBBBhi
                // Syntax: op vAA, +BBBBBBBB
                off = (s2 & 0xffff) | (s3 << 16);
                buffer.position(buffer.position() + (pos + off) * 2);
                putLabel(pos + off, new FillArrayDataLabel(buffer));
                buffer.reset();
                break;

            case PACKED_SWITCH:
                // Format 31t: AA|op BBBBlo BBBBhi
                // Syntax: op vAA, +BBBBBBBB
                off = (s2 & 0xffff) | (s3 << 16);
                buffer.position(buffer.position() + (pos + off) * 2);
                putLabel(pos + off, new PackedSwitchLabel(buffer, pos));
                buffer.reset();
                break;

            case SPARSE_SWITCH:
                // Format 31t: AA|op BBBBlo BBBBhi
                // Syntax: op vAA, +BBBBBBBB
                off = (s2 & 0xffff) | (s3 << 16);
                buffer.position(buffer.position() + (pos + off) * 2);
                putLabel(pos + off, new SparseSwitchLabel(buffer, pos));
                buffer.reset();
                break;
            }
        }

        // Reset the buffer so that parsing can continue.
        buffer.reset();
    }

    /**
     * Skips possible in-code data structures inside the instructions array. The
     * given buffer will be positioned after the in-code data.
     * 
     * @param insns The buffer for the instructions array.
     * @param pos The position at which in-code data is suspeced.
     * @return True if skipping was sucessfull, false otherwise.
     */
    private boolean skipInCodeData(ShortBuffer insns, int pos) {
        Label label = labelMap.get(pos);
        if (label instanceof InCodeDataLabel) {
            insns.position(pos + ((InCodeDataLabel) label).length());
            return true;
        } else
            return false;
    }

    /**
     * An internal representation of a try-catch block combination. This
     * information is parsed from {@code try_item},
     * {@code encoded_catch_handler_list}, {@code encoded_catch_handler} and
     * {@code encoded_type_addr_pair} structures.
     */
    private static class TryCatchInfo {
        final Label startLabel;
        final Label endLabel;
        final Label handlerLabel;
        final String type;

        public TryCatchInfo(Label startLabel, Label endLabel,
                Label handlerLabel, String type) {
            this.startLabel = startLabel;
            this.endLabel = endLabel;
            this.handlerLabel = handlerLabel;
            this.type = type;
        }
    }

    /**
     * In-code data structure for packed-switch instructions.
     */
    private class PackedSwitchLabel extends InCodeDataLabel {
        private final int size;
        final int firstKey;
        final Label[] targets;

        public PackedSwitchLabel(ByteBuffer buffer, int pos) {
            if (buffer.getShort() != 0x0100)
                throw new DexParseException("Unidentified in-code data.");
            size = buffer.getShort();
            firstKey = buffer.getInt();
            targets = new Label[size];
            for (int i = 0; i < size; i++)
                targets[i] = putLabel(pos + buffer.getInt(), true);
        }

        @Override
        public int length() {
            return (size * 2) + 4;
        }
    };

    /**
     * In-code data structure for sparse-switch instructions.
     */
    private class SparseSwitchLabel extends InCodeDataLabel {
        private final int size;
        final int[] keys;
        final Label[] targets;

        public SparseSwitchLabel(ByteBuffer buffer, int pos) {
            if (buffer.getShort() != 0x0200)
                throw new DexParseException("Unidentified in-code data.");
            size = buffer.getShort();
            keys = BufferUtil.getInts(buffer, size);
            targets = new Label[size];
            for (int i = 0; i < size; i++)
                targets[i] = putLabel(pos + buffer.getInt(), true);
        }

        @Override
        public int length() {
            return (size * 4) + 2;
        }
    };

    /**
     * In-code data structure for fill-array-data instructions.
     */
    private static class FillArrayDataLabel extends InCodeDataLabel {
        final int elementWidth;
        final int size;
        final ByteBuffer data;

        public FillArrayDataLabel(ByteBuffer buffer) {
            if (buffer.getShort() != 0x0300)
                throw new DexParseException("Unidentified in-code data.");
            elementWidth = buffer.getShort();
            size = buffer.getInt();
            data = (ByteBuffer) buffer.slice().limit(size * elementWidth);
        }

        @Override
        public int length() {
            return (size * elementWidth + 1) / 2 + 4;
        }
    };

    /**
     * A label as defined by the interface, but representing in-code data of a
     * particular length that can be skipped.
     */
    static abstract class InCodeDataLabel extends Label {
        public abstract int length();

        @Override
        public boolean isJumpTarget() {
            return false;
        }
    };

    /**
     * A label as defined by the interface, but enriched with additional debug
     * information.
     */
    private static class DebugLabel extends Label {
        private final int pos;
        private boolean jumpTarget;

        public DebugLabel(int pos, boolean jumpTarget) {
            this.pos = pos;
            this.jumpTarget = jumpTarget;
        }

        @Override
        public boolean isJumpTarget() {
            return jumpTarget;
        }

        public void setJumpTarget() {
            this.jumpTarget = true;
        }

        @Override
        public String toString() {
            return String.format("L%03d%s", pos, jumpTarget ? "*" : "");
        }
    };
}
