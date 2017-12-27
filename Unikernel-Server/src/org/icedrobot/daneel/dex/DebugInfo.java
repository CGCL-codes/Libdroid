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
import java.util.LinkedList;
import java.util.List;

import org.icedrobot.daneel.dex.Code.InCodeDataLabel;
import org.icedrobot.daneel.util.BufferUtil;
import org.icedrobot.daneel.util.TypeUtil;

/**
 * A parser class capable of parsing {@code debug_info_item} structures as part
 * of DEX files. Keep package-private to hide internal API.
 */
class DebugInfo {

    public static final int DBG_END_SEQUENCE         = 0x00;
    public static final int DBG_ADVANCE_PC           = 0x01;
    public static final int DBG_ADVANCE_LINE         = 0x02;
    public static final int DBG_START_LOCAL          = 0x03;
    public static final int DBG_START_LOCAL_EXTENDED = 0x04;
    public static final int DBG_END_LOCAL            = 0x05;
    public static final int DBG_RESTART_LOCAL        = 0x06;
    public static final int DBG_SET_PROLOGUE_END     = 0x07;
    public static final int DBG_SET_EPILOGUE_BEGIN   = 0x08;
    public static final int DBG_SET_FILE             = 0x09;
    public static final int DBG_FIRST_SPECIAL        = 0x0a;

    /**
     * Parses a {@code debug_info_item} structure in a DEX file at the buffer's
     * current position.
     * 
     * @param buffer The byte buffer to read from.
     * @param dex The DEX file currently being parsed.
     * @param code The parsed {@code code_item} this debug info belongs to.
     * @return An object representing the parsed data.
     */
    public static DebugInfo parse(ByteBuffer buffer, DexFile dex, Code code) {
        return new DebugInfo(buffer, dex, code);
    }

    private final DexFile dex;

    private final Code code;

    private final int lineStart;

    private final int parametersSize;

    private final String[] parameterNames;

    private final List<LocalVariable> localVariables;

    private final List<LineNumber> lineNumbers;

    private DebugInfo(ByteBuffer buffer, DexFile dex, Code code) {
        this.dex = dex;
        this.code = code;
        lineStart = BufferUtil.getULEB128(buffer);
        parametersSize = BufferUtil.getULEB128(buffer);

        // Parse parameter_names array and resolve string indices.
        parameterNames = new String[parametersSize];
        for (int i = 0; i < parametersSize; i++) {
            int nameIdx = BufferUtil.getULEB128(buffer) - 1;
            if (nameIdx != DexFile.NO_INDEX)
                parameterNames[i] = dex.getString(nameIdx);
        }

        // Interpret all subsequent state machine bytecodes to compute local
        // variable and line number information.
        localVariables = new LinkedList<LocalVariable>();
        lineNumbers = new LinkedList<LineNumber>();
        interpret(buffer);
    }

    /**
     * Returns the list of all parameter names (excluding an instance method's
     * {@code this}). There should be one per method parameter, but the value
     * can be {@code null} in case no name is available for the associated
     * parameter.
     * 
     * @return The list as specified above as array.
     */
    public String[] getParameterNames() {
        return parameterNames;
    }

    /**
     * Returns the list of all local variable information as it was emitted by
     * the byte-coded state machine.
     * 
     * @return The list as specified above, never {@code null}.
     */
    public List<LocalVariable> getLocalVariables() {
        return localVariables;
    }

    /**
     * Returns the list of all line numbers as emitted by the byte-coded state
     * machine. Entries are sorted by instruction address low-to-high. Every
     * entry denotes the original source file and line for the instructions
     * following the given label.
     * 
     * @return The list as specified above, never {@code null}.
     */
    public List<LineNumber> getLineNumbers() {
        return lineNumbers;
    }

    /**
     * Interprets the byte-coded state machine that is part of a {@code
     * debug_info_item} and emits all the local variable and line number
     * information.
     * 
     * @param buffer The buffer positioned at the beginning of the bytecode.
     */
    private void interpret(ByteBuffer buffer) {

        // The five state machine registers.
        int addr = 0;
        int line = lineStart;
        String sourceFile = null;
        // We ignore "prologueEnd" for now.
        // We ignore "epilogueBegin" for now.

        // Keep track of local variables in registers.
        int maxRegs = code.getRegistersSize();
        LocalVariable[] regs = new LocalVariable[maxRegs];

        // Emit local variables for method parameters.
        MethodId method = code.getMethod();
        String[] parameterTypes = method.getProtoId().getParameters();
        if (parameterTypes != null) {
            if (parametersSize != parameterTypes.length)
                throw new DexParseException("Parameter count does not match.");
            int regNum = maxRegs - 1;
            for (int i = parametersSize - 1; i >= 0; i--, regNum--) {
                String name = parameterNames[i];
                String type = parameterTypes[i];
                if (TypeUtil.isWideType(type))
                    regNum--;
                regs[regNum] = emitLocalVariable(regNum, 0, name, type, null);
            }
            if (!AccessFlags.isStatic(code.getFlags())) {
                String name = "<this>";
                String type = method.getClassName();
                regs[regNum] = emitLocalVariable(regNum, 0, name, type, null);
            }
        }

        // Iterate over all state machine bytecodes.
        while (buffer.hasRemaining()) {
            int opcode = buffer.get() & 0xff;
            int regNum, nameIdx, typeIdx, sigIdx;
            String name, type, sig;
            LocalVariable local;

            // Switch over all possible bytecodes.
            switch (opcode) {
            case DBG_END_SEQUENCE:
                for (LocalVariable open : regs)
                    if (open != null && open.endLabel == null)
                        open.endLabel = code.getEndLabel();
                return;

            case DBG_ADVANCE_PC:
                addr += BufferUtil.getULEB128(buffer);
                break;

            case DBG_ADVANCE_LINE:
                line += BufferUtil.getSLEB128(buffer);
                break;

            case DBG_START_LOCAL:
                regNum = BufferUtil.getULEB128(buffer);
                nameIdx = BufferUtil.getULEB128(buffer) - 1;
                typeIdx = BufferUtil.getULEB128(buffer) - 1;
                name = resolveString(nameIdx);
                type = resolveType(typeIdx);
                local = regs[regNum];
                if (local != null && local.endLabel == null)
                    closeLocalVariable(local, addr);
                regs[regNum] = emitLocalVariable(regNum, addr, name, type, null);
                break;

            case DBG_START_LOCAL_EXTENDED:
                regNum = BufferUtil.getULEB128(buffer);
                nameIdx = BufferUtil.getULEB128(buffer) - 1;
                typeIdx = BufferUtil.getULEB128(buffer) - 1;
                sigIdx = BufferUtil.getULEB128(buffer) - 1;
                name = resolveString(nameIdx);
                type = resolveType(typeIdx);
                sig = resolveString(sigIdx);
                local = regs[regNum];
                if (local != null && local.endLabel == null)
                    closeLocalVariable(local, addr);
                regs[regNum] = emitLocalVariable(regNum, addr, name, type, sig);
                break;

            case DBG_END_LOCAL:
                regNum = BufferUtil.getULEB128(buffer);
                local = regs[regNum];
                if (local == null || local.endLabel != null)
                    throw new DexParseException("No live local in register.");
                closeLocalVariable(local, addr);
                break;

            case DBG_RESTART_LOCAL:
                regNum = BufferUtil.getULEB128(buffer);
                local = regs[regNum];
                if (local == null)
                    throw new DexParseException("No local to re-introduce.");
                if (local != null && local.endLabel == null)
                    closeLocalVariable(local, addr);
                name = local.name;
                type = local.type;
                sig = local.sig;
                regs[regNum] = emitLocalVariable(regNum, addr, name, type, sig);
                break;

            case DBG_SET_PROLOGUE_END:
                break;
            case DBG_SET_EPILOGUE_BEGIN:
                break;

            case DBG_SET_FILE:
                nameIdx = BufferUtil.getULEB128(buffer) - 1;
                sourceFile = resolveString(nameIdx);
                break;

            default:
                int adjustedOpcode = opcode - DBG_FIRST_SPECIAL;
                line += (adjustedOpcode % 15) - 4;
                addr += (adjustedOpcode / 15);
                emitLineNumber(line, sourceFile, addr);
                break;
            }
        }

        // If we fall through we ran out of bytecodes.
        throw new DexParseException("Premature end of state machine.");
    }

    private String resolveString(int idx) {
        return (idx != DexFile.NO_INDEX) ? dex.getString(idx) : null;
    }

    private String resolveType(int idx) {
        return (idx != DexFile.NO_INDEX) ? dex.getTypeDescriptor(idx) : null;
    }

    /**
     * Emits a new local variable information. The returned object has all
     * information set correctly, except for the label at which the local
     * variable runs out of scope.
     * 
     * @param regNum The register number that will contain the local variable.
     * @param startAddr The instruction offset where the local variable starts.
     * @param name The local variable's name or {@code null}.
     * @param type The local variable's type descriptor or {@code null}.
     * @param sig The local variable's type signature or {@code null}.
     * @return The object representing the local variable information.
     */
    private LocalVariable emitLocalVariable(int regNum, int startAddr,
            String name, String type, String sig) {
        Label startLabel = code.putLabel(startAddr, false);
        LocalVariable local = new LocalVariable(regNum, name, type, sig,
                startLabel);
        localVariables.add(local);
        return local;
    }

    /**
     * Closes a previously emitted local variable information. The label at
     * which the local variable runs out of scope is set here.
     * 
     * @param local The local variable information.
     * @param endAddr The instruction offset where the local variable ends.
     */
    private void closeLocalVariable(LocalVariable local, int endAddr) {
        Label endLabel = code.putLabel(endAddr, false);
        if (endLabel == local.startLabel)
            localVariables.remove(local);
        else
            local.endLabel = endLabel;
    }

    /**
     * Emits a new line number entry. Every entry denotes the original source
     * file and line for the instructions following the given offset.
     * 
     * @param line The given line number.
     * @param source The source file name the line number makes reference to.
     * @param addr The given instruction offset in 16-bit code units.
     */
    private void emitLineNumber(int line, String source, int addr) {
        Label label = code.putLabel(addr, false);
        if (label instanceof InCodeDataLabel)
            return;
        LineNumber lineNumber = new LineNumber(line, source, label);
        lineNumbers.add(lineNumber);
    }

    /**
     * An internal representation of local variable information as emitted by
     * the {@code debug_info_item} state machine.
     */
    static class LocalVariable {
        final int regNum;
        final String name;
        final String type;
        final String sig;
        final Label startLabel;
        Label endLabel;

        public LocalVariable(int regNum, String name, String type, String sig,
                Label startLabel) {
            this.regNum = regNum;
            this.name = name;
            this.type = type;
            this.sig = sig;
            this.startLabel = startLabel;
        }
    };

    /**
     * An internal representation of line number information as emitted by the
     * {@code debug_info_item} state machine.
     */
    static class LineNumber {
        final int line;
        final String source;
        final Label label;

        public LineNumber(int line, String source, Label label) {
            this.line = line;
            this.source = source;
            this.label = label;
        }
    };
}
