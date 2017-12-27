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

import java.util.Arrays;

import org.icedrobot.daneel.dex.Opcode;
import org.objectweb.asm.Opcodes;

import static org.icedrobot.daneel.dex.Opcode.*;
import static org.objectweb.asm.Opcodes.*;

class DalvikToJVMEncoder {
    public static void main(String[] args) {
        Object[] map = {
                Opcode.NOP,       Opcodes.NOP,
                RETURN_VOID,      Opcodes.RETURN,
                
                MONITOR_ENTER,    MONITORENTER,
                MONITOR_EXIT,     MONITOREXIT,
                THROW,            ATHROW,
                
                NEG_INT,          INEG,
                NEG_LONG,         LNEG,
                NEG_FLOAT,        FNEG,
                NEG_DOUBLE,       DNEG,
                INT_TO_LONG,      I2L,
                INT_TO_FLOAT,     I2F,
                INT_TO_DOUBLE,    I2D,
                LONG_TO_INT,      L2I,
                LONG_TO_FLOAT,    L2F,
                LONG_TO_DOUBLE,   L2D,
                FLOAT_TO_INT,     F2I,
                FLOAT_TO_LONG,    F2L,
                FLOAT_TO_DOUBLE,  F2D,
                DOUBLE_TO_INT,    D2I,
                DOUBLE_TO_LONG,   D2L,
                DOUBLE_TO_FLOAT,  D2F,
                INT_TO_BYTE,      I2B,
                INT_TO_CHAR,      I2C,
                INT_TO_SHORT,     I2S,
                ARRAY_LENGTH,     ARRAYLENGTH,
                
                CMPL_FLOAT,       FCMPL,
                CMPG_FLOAT,       FCMPG,
                CMPL_DOUBLE,      DCMPL,
                CMPG_DOUBLE,      DCMPG,
                CMP_LONG,         LCMP,
                ADD_INT,          IADD,
                SUB_INT,          ISUB,
                MUL_INT,          IMUL,
                DIV_INT,          IDIV,
                REM_INT,          IREM,
                AND_INT,          IADD,
                OR_INT,           IOR,
                XOR_INT,          IXOR,
                SHL_INT,          ISHL,
                SHR_INT,          ISHR,
                USHR_INT,         IUSHR,
                ADD_LONG,         LADD,
                SUB_LONG,         LSUB,
                MUL_LONG,         LMUL,
                DIV_LONG,         LDIV,
                REM_LONG,         LREM,
                AND_LONG,         LAND,
                OR_LONG,          LOR,
                XOR_LONG,         LXOR,
                SHL_LONG,         LSHL,
                SHR_LONG,         LSHR,
                USHR_LONG,        LUSHR,
                ADD_FLOAT,        FADD,
                SUB_FLOAT,        FSUB,
                MUL_FLOAT,        FMUL,
                DIV_FLOAT,        FDIV,
                REM_FLOAT,        FREM,
                ADD_DOUBLE,       DADD,
                SUB_DOUBLE,       DSUB,
                MUL_DOUBLE,       DMUL,
                DIV_DOUBLE,       DDIV,
                REM_DOUBLE,       DREM,
                ADD_INT_2ADDR,    IADD,
                SUB_INT_2ADDR,    ISUB,
                MUL_INT_2ADDR,    IMUL,
                DIV_INT_2ADDR,    IDIV,
                REM_INT_2ADDR,    IREM,
                AND_INT_2ADDR,    IAND,
                OR_INT_2ADDR,     IOR,
                XOR_INT_2ADDR,    IXOR,
                SHL_INT_2ADDR,    ISHL,
                SHR_INT_2ADDR,    ISHR,
                USHR_INT_2ADDR,   IUSHR,
                ADD_LONG_2ADDR,   LADD,
                SUB_LONG_2ADDR,   LSUB,
                MUL_LONG_2ADDR,   LMUL,
                DIV_LONG_2ADDR,   LDIV,
                REM_LONG_2ADDR,   LREM,
                AND_LONG_2ADDR,   LAND,
                OR_LONG_2ADDR,    LOR,
                XOR_LONG_2ADDR,   LXOR,
                SHL_LONG_2ADDR,   LSHL,
                SHR_LONG_2ADDR,   LSHR,
                USHR_LONG_2ADDR,  LUSHR,
                ADD_FLOAT_2ADDR,  FADD,
                SUB_FLOAT_2ADDR,  FSUB,
                MUL_FLOAT_2ADDR,  FMUL,
                DIV_FLOAT_2ADDR,  FDIV,
                REM_FLOAT_2ADDR,  FREM,
                ADD_DOUBLE_2ADDR, DADD,
                SUB_DOUBLE_2ADDR, DSUB,
                MUL_DOUBLE_2ADDR, DMUL,
                DIV_DOUBLE_2ADDR, DDIV,
                REM_DOUBLE_2ADDR, DREM,
                
                ADD_INT_LIT16,    IADD,
                RSUB_INT_LIT16,   ISUB,
                MUL_INT_LIT16,    IMUL,
                DIV_INT_LIT16,    IDIV,
                REM_INT_LIT16,    IREM,
                AND_INT_LIT16,    IADD,
                OR_INT_LIT16,     IOR,
                XOR_INT_LIT16,    IXOR,
                ADD_INT_LIT8,     IADD,
                RSUB_INT_LIT8,    ISUB,
                MUL_INT_LIT8,     IMUL,
                DIV_INT_LIT8,     IDIV,
                REM_INT_LIT8,     IREM,
                AND_INT_LIT8,     IAND,
                OR_INT_LIT8,      IOR,
                XOR_INT_LIT8,     IXOR,
                SHL_INT_LIT8,     ISHL,
                SHR_INT_LIT8,     ISHR,
                USHR_INT_LIT8,    IUSHR,
                
                CHECK_CAST,       CHECKCAST,
                NEW_INSTANCE,     NEW,
                
                INSTANCE_OF,      INSTANCEOF,
                
                GOTO_32,          Opcodes.GOTO,
                Opcode.GOTO,      Opcodes.GOTO,
                GOTO_16,          Opcodes.GOTO,
                
                IF_LTZ,           IFLT,
                IF_GEZ,           IFGE,
                IF_GTZ,           IFGT,
                IF_LEZ,           IFLE,
                
                IF_LT,            IF_ICMPLT,
                IF_LE,            IF_ICMPLE,
                IF_GE,            IF_ICMPGE,
                IF_GT,            IF_ICMPGT,
                
                APUT_OBJECT,      AASTORE,
                APUT_BOOLEAN,     BASTORE,
                APUT_BYTE,        BASTORE,
                APUT_CHAR,        CASTORE,
                APUT_SHORT,       SASTORE,
                AGET_OBJECT,      AALOAD,
                AGET_BOOLEAN,     BALOAD,
                AGET_BYTE,        BALOAD,
                AGET_CHAR,        CALOAD,
                AGET_SHORT,       SALOAD,
                
                NEW_ARRAY,        NEWARRAY,
                
             };

        // encode
        char[] text = new char[512];
        Arrays.fill(text, '@');
        for (int i = 0; i < map.length; i += 2) {
            Opcode opcode = (Opcode) map[i];
            int javaOpcode = (Integer) map[i + 1];
            
            int javaOpcode_hi = javaOpcode >> 4;
            int javaOpcode_lo = javaOpcode & 0x0f;
            text[2 * opcode.ordinal()] = (char) ('A' + javaOpcode_hi);
            text[2 * opcode.ordinal() + 1] = (char) ('A' + javaOpcode_lo);
            
        }
        String s = new String(text);
        System.out.println(s);
        
        // decode
        int[] toJavaOpcode = new int[256];
        int length = s.length();
        for(int i=0; i<length; i+=2) {
            int index = ((s.charAt(i) - 'A' ) << 4) | (s.charAt(i + 1) - 'A');
            toJavaOpcode[i >> 1] = index;
        }
        
        // check
        for (int i = 0; i < map.length; i += 2) {
            Opcode opcode = (Opcode) map[i];
            int javaOpcode = (Integer) map[i + 1];
            if (toJavaOpcode[opcode.ordinal()] != javaOpcode) {
                throw new AssertionError();
            }
        }
    }
}
