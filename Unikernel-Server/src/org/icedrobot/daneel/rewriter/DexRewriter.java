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

import static org.objectweb.asm.Opcodes.*;

import static org.icedrobot.daneel.rewriter.Register.*;
import static org.icedrobot.daneel.rewriter.Registers.*;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Set;

import org.icedrobot.daneel.dex.AccessFlags;
import org.icedrobot.daneel.dex.DexAnnotationVisitor;
import org.icedrobot.daneel.dex.DexClassVisitor;
import org.icedrobot.daneel.dex.DexFieldVisitor;
import org.icedrobot.daneel.dex.DexMethodVisitor;
import org.icedrobot.daneel.dex.DexReader;
import org.icedrobot.daneel.dex.Label;
import org.icedrobot.daneel.dex.Opcode;
import org.icedrobot.daneel.util.TypeUtil;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public class DexRewriter implements DexClassVisitor {
    private final ClassVisitor cv;
    
    /**
     * Rewrites a class from Dalvik bytecode into Java bytecode representation.
     * The source class is given by specifying its name and the DEX source it is
     * originating from.
     * 
     * @param name The name of the class to rewrite as a normal fully qualified
     *        class name.
     * @param reader The reader for the DEX source to read from.
     * @return The Java bytecode for the given class.
     * @throws ClassNotFoundException In case the source does not define a class
     *         by that name.
     */
    public static byte[] rewrite(String name, DexReader reader)
            throws ClassNotFoundException {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        reader.accept("L" + name.replace('.', '/') + ";", new DexRewriter(writer), 0);
        return writer.toByteArray();
    }

    public DexRewriter(ClassVisitor cv) {
        this.cv = cv;
    }

    @Override
    public void visit(int access, String name, String supername,
            String[] interfaces) {
        name = TypeUtil.convertDescToInternal(name);
        supername = TypeUtil.convertDescToInternal(supername);
        interfaces = (interfaces == null) ? null : TypeUtil
                .convertDescToInternals(interfaces);
        cv.visit(V1_6, access, name, null, supername, interfaces);
    }

    @Override
    public void visitSource(String source) {
        cv.visitSource(source, null);
    }

    @Override
    public DexAnnotationVisitor visitAnnotation(int visibility, String type) {
        if (visibility == DexAnnotationVisitor.VISIBILITY_RUNTIME) {
            AnnotationVisitor av = cv.visitAnnotation(type, true);
            if (av == null)
                return null;
            return new AnnotationRewriter(av);
        }
        return null;
    }

    @Override
    public DexFieldVisitor visitField(int access, String name, String type,
            Object value) {
        final FieldVisitor fv = cv.visitField(access, name, type, null, value);
        if (fv == null) {
            return null;
        }

        return new DexFieldVisitor() {
            @Override
            public void visitEnd() {
                fv.visitEnd();
            }

            @Override
            public DexAnnotationVisitor visitAnnotation(int visibility,
                    String type) {
                if (visibility == DexAnnotationVisitor.VISIBILITY_RUNTIME) {
                    AnnotationVisitor av = fv.visitAnnotation(type, true);
                    if (av == null)
                        return null;
                    return new AnnotationRewriter(av);
                }
                return null;
            }
        };
    }

    @Override
    public DexMethodVisitor visitMethod(int access, String name, String shorty,
            String returnType, String[] parameterTypes) {
        String desc = TypeUtil.convertProtoToDesc(returnType, parameterTypes);
        MethodVisitor mv = cv.visitMethod(access, name, desc, null, null);
        if (mv == null) {
            return null;
        }

        return new MethodRewriter(mv, AccessFlags.isStatic(access), desc);
    }

    @Override
    public void visitEnd() {
        cv.visitEnd();
    }

    public static class MethodRewriter implements DexMethodVisitor {
        final PatchMethodVisitor mv;
        private final boolean isStatic;
        private final String desc;

        private Interpreter interpreter;
        private int returnRegisterType;   // type of the register used to stored
                                          // the return value
        private boolean exceptionOnTop;   // true if an exception is on top of the stack
        private int locals;               // number of local variables
        private int parameters;           // number of parameters
        
        // map DEX jump label to ASM label
        private final HashMap<Label, org.objectweb.asm.Label> labelMap =
            new HashMap<Label, org.objectweb.asm.Label>();
        // set which contains all labels which is the start of an exception handler
        private final Set<Label> exceptionHandlerSet =
            Collections.newSetFromMap(new IdentityHashMap<Label, Boolean>());
        // map the start label of the try label to the corresponding exception handler label
        private final IdentityHashMap<Label, Label> exceptionTryStartToHandlerMap =
            new IdentityHashMap<Label, Label>();

        public MethodRewriter(MethodVisitor mv, boolean isStatic, String desc) {
            this.mv = new PatchMethodVisitor(mv);
            this.isStatic = isStatic;
            this.desc = desc;
            this.returnRegisterType = VOID_TYPE;
        }

        public Interpreter getInterpreter() {
            return interpreter;
        }
        
        /**
         * Translate a register with Dalvik calling convention to a slot with
         * Java calling convention
         * 
         * @param register the register
         * @return the corresponding Java slot.
         */
        private int registerToSlot(int register) {
            if (register >= locals) { // it's a parameter
                return register - locals;
            } // otherwise it's a local variable
            return parameters + register;
        }

        private static int getTypeFromASMType(Type type) {
            switch (type.getSort()) {
            case Type.BOOLEAN:
                return BOOLEAN_TYPE;
            case Type.BYTE:
                return BYTE_TYPE;
            case Type.CHAR:
                return CHAR_TYPE;
            case Type.SHORT:
                return SHORT_TYPE;
            case Type.INT:
                return INT_TYPE;
            case Type.LONG:
                return LONG_TYPE;
            case Type.FLOAT:
                return FLOAT_TYPE;
            case Type.DOUBLE:
                return DOUBLE_TYPE;
            case Type.VOID:
                return VOID_TYPE;
            case Type.OBJECT:
                return OBJECT_TYPE;
            case Type.ARRAY:
                return Register.makeArray(getTypeFromASMType(type.getElementType()), type.getDimensions());
            default:
                throw new AssertionError("bad type "+type);
            }
        }
        
        private static int getNewArrayKindFromASMType(Type type) {
            switch (type.getSort()) {
            case Type.BOOLEAN:
                return T_BOOLEAN;
            case Type.BYTE:
                return T_BYTE;
            case Type.CHAR:
                return T_CHAR;
            case Type.SHORT:
                return T_SHORT;
            case Type.INT:
                return T_INT;
            case Type.LONG:
                return T_LONG;
            case Type.FLOAT:
                return T_FLOAT;
            case Type.DOUBLE:
                return T_DOUBLE;
            default:
                throw new AssertionError("bad type "+type);
            }
        }

        private static int getTypeFromFieldDescriptor(String descriptor) {
            return getTypeFromASMType(Type.getType(descriptor));
        }

        private static int getReturnTypeFromMethodDescriptor(String descriptor) {
            return getTypeFromASMType(Type.getReturnType(descriptor));
        }

        /** Maps DEX label to ASM label
         * @param label a DEX label
         * @return the corresponding ASM label.
         */
        private org.objectweb.asm.Label getASMLabel(Label label) {
            org.objectweb.asm.Label asmLabel = labelMap.get(label);
            if (asmLabel != null) {
                return asmLabel;
            }
            asmLabel = new org.objectweb.asm.Label();
            labelMap.put(label, asmLabel);
            return asmLabel;
        }

        /**
         * This method must be called before each instruction unless it is a
         * {@link Opcode#MOVE_RESULT}* or a {@link Opcode#MOVE_EXCEPTION}
         * instruction. It checks if the last instruction was an invoke or the
         * start of an exception handler and corrects the stack.
         */
        private void fixStackAfterAMethodCallOrAnExceptionHandler() {
            int returnRegisterType = this.returnRegisterType;
            if (returnRegisterType != VOID_TYPE) {
                mv.visitInsn((returnRegisterType == LONG_TYPE || returnRegisterType == DOUBLE_TYPE)? POP2: POP);
                this.returnRegisterType = VOID_TYPE;
            }
            if (exceptionOnTop) {
                mv.visitInsn(POP);
                exceptionOnTop = false;
            }
        }
        
        private void createOrMergeJoinPoint(Label label) {
            Register[] registers = interpreter.getJoinPoint(label);
            if (registers != null) { // backward jump
                interpreter.merge(registers);
            } else {  // forward jump
                interpreter.cloneJoinPoint(label);    
            }
        }
        
        private void visitNotInstr(int type) {
            org.objectweb.asm.Label falsePart = new org.objectweb.asm.Label();
            org.objectweb.asm.Label endPart = new org.objectweb.asm.Label();
            mv.visitJumpInsn(IFNE, falsePart);
            mv.visitInsn((type == INT_TYPE)? ICONST_1: LCONST_1);
            mv.visitJumpInsn(GOTO, endPart);
            mv.visitLabel(falsePart);
            mv.visitInsn((type == INT_TYPE)? ICONST_0: LCONST_0);
            mv.visitLabel(endPart);
        }

        /**
         * Instantiates a new assertion error used to indicate an invalid
         * opcode.
         * 
         * @param opcode The invalid opcode.
         * @return The newly created assertion error.
         */
        private static AssertionError newAssertionError(Opcode opcode) {
            return new AssertionError("Invalid opcode: " + opcode);
        }

        /**
         * Reads a typed value from the given data buffer. Mainly used to read
         * {@link Opcode#FILL_ARRAY_DATA} values.
         * 
         * @param data The given data buffer positioned at the next element.
         * @param type The type constant for the expected value type.
         * @return The boxed primitive value as read from the buffer.
         */
        private static Object readTypedValue(ByteBuffer data, int type) {
            switch (type) {
            case INT_TYPE:
                return data.getInt();
            case LONG_TYPE:
                return data.getLong();
            case FLOAT_TYPE:
                return data.getFloat();
            case DOUBLE_TYPE:
                return data.getDouble();
            case BYTE_TYPE:
                return data.get();
            case CHAR_TYPE:
                return data.getChar();
            case SHORT_TYPE:
                return data.getShort();
            default:
                throw new AssertionError("Unknown data type: " + type);
            }
        }

        @Override
        public DexAnnotationVisitor visitAnnotation(int visibility, String type) {
            if (visibility == DexAnnotationVisitor.VISIBILITY_RUNTIME) {
                AnnotationVisitor av = mv.visitAnnotation(type, true);
                if (av == null)
                    return null;
                return new AnnotationRewriter(av);
            }
            return null;
        }

        @Override
        public DexAnnotationVisitor visitParameterAnnotation(int parameter,
                int visibility, String type) {
            if (visibility == DexAnnotationVisitor.VISIBILITY_RUNTIME) {
                AnnotationVisitor av = mv.visitParameterAnnotation(parameter,
                        type, true);
                if (av == null)
                    return null;
                return new AnnotationRewriter(av);
            }
            return null;
        }

        @Override
        public void visitLineNumber(String source, int line, Label start) {
            mv.visitLineNumber(line, getASMLabel(start));
        }

        @Override
        public void visitLocalVariable(String name, String desc, Label start,
                Label end, int reg) {
            reg = registerToSlot(reg);
            // XXX Local variables can appear more than once in DEX files.
            /*if (name != null && desc != null) {
                mv.visitLocalVariable(name, desc, null, getASMLabel(start),
                        getASMLabel(end), reg);
            }*/
        }

        @Override
        public void visitCode(int registerSize, int insSize, int outSize) {
            mv.visitCode();
            this.locals = registerSize - insSize;
            this.parameters = insSize;
            
            Register[] registers = new Register[registerSize];
            Arrays.fill(registers, 0, registerSize, Register.UNINITIALIZED);
            
            Type[] parameterTypes = Type.getArgumentTypes(desc);
            int length = parameterTypes.length;
            int slot;
            if (isStatic) {
                slot = 0;
            } else {
                registers[0] = new Register(OBJECT_TYPE, null);
                slot = 1;
            }
            for (int i = 0; i < length; i++) {
                Type type = parameterTypes[i];
                registers[slot] = new Register(getTypeFromASMType(type), null);
                slot += type.getSize();
            }
            
            interpreter = new Interpreter(registers);
        }

        @Override
        public void visitInstr(Opcode opcode) {
            fixStackAfterAMethodCallOrAnExceptionHandler();
            if (opcode == Opcode.RETURN_VOID) {
                mv.visitInsn(RETURN);
                interpreter.setDead();
                return;
            }
            if (opcode == Opcode.NOP) {
                mv.visitInsn(NOP);
                return;
            }
            throw newAssertionError(opcode);
        }

        @Override
        public void visitInstrField(Opcode opcode, int vsrcOrDest, int vref,
                String owner, String name, String desc) {
            fixStackAfterAMethodCallOrAnExceptionHandler();
            vsrcOrDest = registerToSlot(vsrcOrDest);
            vref = registerToSlot(vref);
            owner = TypeUtil.convertDescToInternal(owner);
            if (opcode.compareTo(Opcode.IGET) >= 0
                    && opcode.compareTo(Opcode.IGET_SHORT) <= 0) {
                mv.visitVarInsn(ALOAD, vref);
                mv.visitFieldInsn(GETFIELD, owner, name, desc);
                int type = getTypeFromFieldDescriptor(desc);
                interpreter.store(vsrcOrDest, type);
                mv.visitVarInsn(Register.getJavaOpcode(type, ISTORE),
                        vsrcOrDest);
            } else if (opcode.compareTo(Opcode.SGET) >= 0
                    && opcode.compareTo(Opcode.SGET_SHORT) <= 0) {
                mv.visitFieldInsn(GETSTATIC, owner, name, desc);
                int type = getTypeFromFieldDescriptor(desc);
                interpreter.store(vsrcOrDest, type);
                mv.visitVarInsn(Register.getJavaOpcode(type, ISTORE),
                        vsrcOrDest);
            } else if (opcode.compareTo(Opcode.IPUT) >= 0
                    && opcode.compareTo(Opcode.IPUT_SHORT) <= 0) {
                mv.visitVarInsn(ALOAD, vref);
                int type = getTypeFromFieldDescriptor(desc);
                mv.visitVarInsn(Register.getJavaOpcode(type, ILOAD), vsrcOrDest);
                mv.visitFieldInsn(PUTFIELD, owner, name, desc);
                interpreter.load(vsrcOrDest, type);
            } else if (opcode.compareTo(Opcode.SPUT) >= 0
                    && opcode.compareTo(Opcode.SPUT_SHORT) <= 0) {
                int type = getTypeFromFieldDescriptor(desc);
                mv.visitVarInsn(Register.getJavaOpcode(type, ILOAD), vsrcOrDest);
                mv.visitFieldInsn(PUTSTATIC, owner, name, desc);
                interpreter.load(vsrcOrDest, type);
            } else {
                throw newAssertionError(opcode);
            }
        }

        @Override
        public void visitInstrConstString(Opcode opcode, int vdest, String value) {
            fixStackAfterAMethodCallOrAnExceptionHandler();
            vdest = registerToSlot(vdest);
            mv.visitLdcInsn(value);
            mv.visitVarInsn(ASTORE, vdest);
            interpreter.store(vdest, OBJECT_TYPE);
        }
        
        @Override
        public void visitInstrClass(Opcode opcode, int vsrcOrDest, String typeDesc) {
            fixStackAfterAMethodCallOrAnExceptionHandler();
            vsrcOrDest = registerToSlot(vsrcOrDest);
            switch(opcode) {
            case CONST_CLASS:
                mv.visitLdcInsn(Type.getType(typeDesc));
                mv.visitVarInsn(ASTORE, vsrcOrDest);
                interpreter.store(vsrcOrDest, OBJECT_TYPE);
                break;
            case NEW_INSTANCE:
                mv.visitTypeInsn(NEW, TypeUtil
                        .convertDescToInternal(typeDesc));
                mv.visitVarInsn(ASTORE, vsrcOrDest);
                interpreter.store(vsrcOrDest, OBJECT_TYPE);
                break;
            case CHECK_CAST:
                mv.visitVarInsn(ALOAD, vsrcOrDest);
                interpreter.load(vsrcOrDest, OBJECT_TYPE);
                mv.visitTypeInsn(CHECKCAST, Type.getType(typeDesc)
                        .getInternalName());
                mv.visitVarInsn(ASTORE, vsrcOrDest);
                interpreter.store(vsrcOrDest,
                        getTypeFromASMType(Type.getType(typeDesc)));
                break;
            default:
                throw newAssertionError(opcode);
            }
        }

        @Override
        public void visitInstrMethod(Opcode opcode, int num, int va,
                int vpacked, String owner, String name, String desc) {
            fixStackAfterAMethodCallOrAnExceptionHandler();
            Opcode erasedRangeOpcode;
            int[] registers;
            if (opcode.compareTo(Opcode.INVOKE_VIRTUAL_RANGE) >= 0) { // *_range
                registers = null;
                erasedRangeOpcode = Opcode.getOpcode(opcode.ordinal()
                        - INVOKE_RANGE_SHIFT);
            } else {
                registers = new int[] { getRegisterD(vpacked),
                        getRegisterE(vpacked), getRegisterF(vpacked),
                        getRegisterG(vpacked), va };
                erasedRangeOpcode = opcode;
            }

            int javaOpcode;
            switch (erasedRangeOpcode) {
            case INVOKE_STATIC:
                javaOpcode = INVOKESTATIC;
                break;
            case INVOKE_SUPER:
                javaOpcode = INVOKESPECIAL;
                break;
            case INVOKE_VIRTUAL:
                javaOpcode = INVOKEVIRTUAL;
                break;
            case INVOKE_INTERFACE:
                javaOpcode = INVOKEINTERFACE;
                break;
            case INVOKE_DIRECT:
                javaOpcode = ("<init>".equals(name)) ? INVOKESPECIAL
                        : INVOKEVIRTUAL;
                break;
            default:
                throw newAssertionError(opcode);
            }
            
            // We need to load the receiver first.
            int r;
            if (erasedRangeOpcode != Opcode.INVOKE_STATIC) {
                r = 1;
                int register = registerToSlot((registers == null) ? va
                        : registers[0]);
                mv.visitVarInsn(ALOAD, register);
                interpreter.load(register, getTypeFromASMType(Type.getType(owner)));
            } else {
                r = 0;
            }
            
            Type[] types = Type.getArgumentTypes(desc);
            for (Type asmType : types) {
                int type = getTypeFromASMType(asmType);
                int register = registerToSlot((registers == null) ? va + r
                        : registers[r]);
                mv.visitVarInsn(Register.getJavaOpcode(type, ILOAD), register);
                interpreter.load(register, type);
                r += asmType.getSize();
            }

            owner = Type.getType(owner).getInternalName();
            mv.visitMethodInsn(javaOpcode, owner, name, desc);
            returnRegisterType = getReturnTypeFromMethodDescriptor(desc);
            return;
        }

        private static final int INVOKE_RANGE_SHIFT =
                Opcode.INVOKE_VIRTUAL_RANGE.ordinal() -
                Opcode.INVOKE_VIRTUAL.ordinal();

        @Override
        public void visitInstrConstU32(Opcode opcode, int vdest,
                final int value) {
            fixStackAfterAMethodCallOrAnExceptionHandler();
            final int sdest = registerToSlot(vdest);
            mv.setPatchMode();
            mv.visitLdcInsn(value);
            final AbstractInsnNode ldc = mv.getLastInsnNode();
            mv.visitVarInsn(ISTORE, sdest);
            final AbstractInsnNode istore = mv.getLastInsnNode();
            interpreter.storeUntypedType(sdest, U32_TYPE, new Patchable() {
                @Override
                protected void patch(int registerType) {
                    if (registerType == FLOAT_TYPE) {
                        mv.patch(ldc, new LdcInsnNode(Float
                                .intBitsToFloat(value)));
                        mv.patch(istore, new VarInsnNode(FSTORE, sdest));
                        return;
                    }
                    if (registerType == OBJECT_TYPE || Register.isArray(registerType)) {
                        mv.patch(ldc, new InsnNode(ACONST_NULL));
                        mv.patch(istore, new VarInsnNode(ASTORE, sdest));
                        return;
                    }
                }
            });
        }

        @Override
        public void visitInstrConstU64(Opcode opcode, int vdest,
                final long value) {
            fixStackAfterAMethodCallOrAnExceptionHandler();
            final int sdest = registerToSlot(vdest);
            mv.setPatchMode();
            mv.visitLdcInsn(value);
            final AbstractInsnNode ldc = mv.getLastInsnNode();
            mv.visitVarInsn(LSTORE, sdest);
            final AbstractInsnNode lstore = mv.getLastInsnNode();
            interpreter.storeUntypedType(sdest, U64_TYPE, new Patchable() {
                @Override
                protected void patch(int registerType) {
                    if (registerType == DOUBLE_TYPE) {
                        mv.patch(ldc, new LdcInsnNode(Double.longBitsToDouble(value)));
                        mv.patch(lstore, new VarInsnNode(DSTORE, sdest));
                        return;
                    }
                }
            });
        }

        @Override
        public void visitInstrOp(Opcode opcode, int srcOrDst) {
            srcOrDst = registerToSlot(srcOrDst);
            switch (opcode) {
            case RETURN:
            case RETURN_WIDE:
            case RETURN_OBJECT: {
                fixStackAfterAMethodCallOrAnExceptionHandler();
                int type = getReturnTypeFromMethodDescriptor(desc);
                mv.visitVarInsn(Register.getJavaOpcode(type, ILOAD), srcOrDst);
                interpreter.load(srcOrDst, type);
                mv.visitInsn(Register.getJavaOpcode(type, IRETURN));
                interpreter.setDead();
                break;
            }
            case MOVE_RESULT:
            case MOVE_RESULT_WIDE:
            case MOVE_RESULT_OBJECT: {
                // INVOKE + MOVE_RESULT is equivalent to INVOKE + STORE
                if (returnRegisterType != VOID_TYPE) {
                    mv.visitVarInsn(
                            Register.getJavaOpcode(returnRegisterType, ISTORE),
                            srcOrDst);
                    interpreter.store(srcOrDst, returnRegisterType);
                    returnRegisterType = VOID_TYPE;
                }
                break;
            }
            case MOVE_EXCEPTION: {
                mv.visitVarInsn(ASTORE, srcOrDst);
                interpreter.store(srcOrDst, OBJECT_TYPE);
                exceptionOnTop = false;
                break;   
            }
            default: //MONITOR_ENTER, MONITOR_EXIT, THROW.
                fixStackAfterAMethodCallOrAnExceptionHandler();
                mv.visitVarInsn(ALOAD, srcOrDst);
                interpreter.load(srcOrDst, OBJECT_TYPE);
                mv.visitInsn(toJavaOpcode[opcode.ordinal()]);
                if (opcode == Opcode.THROW) {
                    interpreter.setDead();
                }
            }
        }
        
        @Override
        public void visitInstrUnaryOp(Opcode opcode, int dest, int src) {
            fixStackAfterAMethodCallOrAnExceptionHandler();
            final int vdest = registerToSlot(dest);
            final int vsrc = registerToSlot(src);
            int srcType = INT_TYPE, dstType;
            switch(opcode) {
            case MOVE:
            case MOVE_FROM16:
            case MOVE_16:
            case MOVE_WIDE:
            case MOVE_WIDE_FROM16:
            case MOVE_WIDE_16: 
            case MOVE_OBJECT:
            case MOVE_OBJECT_FROM16:
            case MOVE_OBJECT_16: {
                final Register register  = interpreter.getRegister(vsrc);
                int type = register.getType();
                if (Register.isUntyped(type)) {
                    int runtimeType = Register.asDefaultTypedType(type);
                    mv.setPatchMode();
                    mv.visitVarInsn(Register.getJavaOpcode(runtimeType, ILOAD), vsrc);
                    final AbstractInsnNode load = mv.getLastInsnNode();
                    mv.visitVarInsn(Register.getJavaOpcode(runtimeType, ISTORE), vdest);
                    final AbstractInsnNode store = mv.getLastInsnNode();
                    final Patchable patchable = register.getPatchable();
                    interpreter.storeUntypedType(vdest, type, new Patchable() {
                        @Override
                        protected void patch(int registerType) {
                            patchable.doPatch(registerType);
                            if (registerType == FLOAT_TYPE) {
                                mv.patch(load, new VarInsnNode(FLOAD, vsrc));
                                mv.patch(store, new VarInsnNode(FSTORE, vdest));
                                return;
                            }
                            if (registerType == DOUBLE_TYPE) {
                                mv.patch(load, new VarInsnNode(DLOAD, vsrc));
                                mv.patch(store, new VarInsnNode(DSTORE, vdest));
                                return;
                            }
                            if (registerType == OBJECT_TYPE || Register.isArray(registerType)) {
                                mv.patch(load, new VarInsnNode(ALOAD, vsrc));
                                mv.patch(store, new VarInsnNode(ASTORE, vdest));
                                return;
                            }
                        }
                    });
                } else {
                    mv.visitVarInsn(Register.getJavaOpcode(type, ILOAD), vsrc);
                    interpreter.load(vsrc, type);
                    mv.visitVarInsn(Register.getJavaOpcode(type, ISTORE), vdest);
                    interpreter.store(vdest, type);
                }
                return;
            }
            
            case ARRAY_LENGTH:
                srcType = OBJECT_TYPE;
                dstType = INT_TYPE;
                break;
            case NEG_INT: case NOT_INT:
                dstType = INT_TYPE;
                break;
            case NEG_LONG: case NOT_LONG:
                srcType = dstType = LONG_TYPE;
                break;
            case NEG_FLOAT:
                srcType = dstType = FLOAT_TYPE;
                break;
            case NEG_DOUBLE:
                srcType = dstType = DOUBLE_TYPE;
                break;
            
            case INT_TO_BYTE:
            case INT_TO_CHAR:
            case INT_TO_SHORT:
                dstType = BYTE_TYPE + opcode.ordinal() - Opcode.INT_TO_BYTE.ordinal();
                break;
                
            default: { // INT_TO_LONG, INT_TO_FLOAT, INT_TO_DOUBLE, LONG_TO_INT, LONG_TO_FLOAT,
                       // LONG_TO_DOUBLE, FLOAT_TO_INT, FLOAT_TO_LONG, DOUBLE_TO_INT,
                       // DOUBLE_TO_LONG, DOUBLE_TO_FLOAT
                int conversion = opcode.ordinal() - Opcode.INT_TO_LONG.ordinal();
                int div = conversion / 3;
                int modulo = conversion % 3;
                srcType = INT_TYPE + div;
                dstType = INT_TYPE + ((modulo < div)? modulo: modulo + 1);
            }
            }
            
            mv.visitVarInsn(Register.getJavaOpcode(srcType, ILOAD), vsrc);
            interpreter.load(vsrc, srcType);
            if (opcode == Opcode.NOT_INT || opcode == Opcode.NOT_LONG) {
                visitNotInstr(srcType);
            } else {
                mv.visitInsn(toJavaOpcode[opcode.ordinal()]);   
            }
            mv.visitVarInsn(Register.getJavaOpcode(dstType, ISTORE), vdest);
            interpreter.store(vdest, dstType);
        }

        @Override
        public void visitInstrBinOp(Opcode opcode, int vdest, int vsrc1,
                int vsrc2) {
            fixStackAfterAMethodCallOrAnExceptionHandler();
            vdest = registerToSlot(vdest);
            vsrc1 = registerToSlot(vsrc1);
            vsrc2 = registerToSlot(vsrc2);
            
            // 2 addresses -> simple address opcode
            if (opcode.compareTo(Opcode.ADD_INT_2ADDR) >= 0 &&
                opcode.compareTo(Opcode.REM_DOUBLE_2ADDR) <= 0) {
                opcode = Opcode.getOpcode(opcode.ordinal() + OP_2ADDR_SHIFT);
            }
            
            int src1Type, src2Type, dstType;
            switch(opcode) {
            case CMPL_FLOAT: case CMPG_FLOAT: case ADD_FLOAT: case SUB_FLOAT:
            case MUL_FLOAT: case DIV_FLOAT: case REM_FLOAT:
                src1Type = src2Type = FLOAT_TYPE;
                dstType = (opcode == Opcode.CMPL_FLOAT ||
                           opcode == Opcode.CMPG_FLOAT) ? INT_TYPE : FLOAT_TYPE;
                break;
            
            case CMPL_DOUBLE: case CMPG_DOUBLE: case ADD_DOUBLE: case SUB_DOUBLE:
            case MUL_DOUBLE:  case DIV_DOUBLE: case REM_DOUBLE:
                src1Type = src2Type = DOUBLE_TYPE;
                dstType = (opcode == Opcode.CMPL_DOUBLE ||
                           opcode == Opcode.CMPG_DOUBLE) ? INT_TYPE : DOUBLE_TYPE;
                break;
                
            case CMP_LONG: case ADD_LONG: case SUB_LONG: case MUL_LONG:
            case DIV_LONG: case REM_LONG: case AND_LONG: case OR_LONG:
            case XOR_LONG:
                src1Type = src2Type = LONG_TYPE;
                dstType = (opcode == Opcode.CMP_LONG) ? INT_TYPE : LONG_TYPE;
                break;
                
            case SHL_LONG: case SHR_LONG: case USHR_LONG:
                src1Type = LONG_TYPE;
                src2Type = INT_TYPE;
                dstType = LONG_TYPE;
                break;
              
            // ADD_INT, SUB_INT, MUL_INT, DIV_INT, REM_INT,
            // AND_INT, OR_INT, XOR_INT, SHL_INT, SHR_INT, USHR_INT,
            default: 
                src1Type = src2Type = dstType = INT_TYPE;
            }
            
            mv.visitVarInsn(Register.getJavaOpcode(src1Type, ILOAD), vsrc1);
            interpreter.load(vsrc1, src1Type);
            mv.visitVarInsn(Register.getJavaOpcode(src2Type, ILOAD), vsrc2);
            interpreter.load(vsrc2, src2Type);
            mv.visitInsn(toJavaOpcode[opcode.ordinal()]);
            mv.visitVarInsn(Register.getJavaOpcode(dstType, ISTORE), vdest);
            interpreter.store(vdest, dstType);
        }
        
        private static final int OP_2ADDR_SHIFT =
            Opcode.ADD_INT.ordinal() - Opcode.ADD_INT_2ADDR.ordinal();

        @Override
        public void visitInstrBinOpAndLiteral(Opcode opcode, int vdest,
                int vsrc, int value) {
            fixStackAfterAMethodCallOrAnExceptionHandler();
            vdest = registerToSlot(vdest);
            vsrc = registerToSlot(vsrc);
            boolean rsub = opcode == Opcode.RSUB_INT_LIT16
                    || opcode == Opcode.RSUB_INT_LIT8;
            if (rsub) {
                mv.visitLdcInsn(value);
            }
            mv.visitVarInsn(ILOAD, vsrc);
            interpreter.load(vsrc, INT_TYPE);
            if (!rsub) {
                mv.visitLdcInsn(value);
            }
            mv.visitInsn(toJavaOpcode[opcode.ordinal()]);
            mv.visitVarInsn(ISTORE, vdest);
            interpreter.store(vdest, INT_TYPE);
        }

        @Override
        public void visitLabel(Label label) {
            // Only fix the stack if label is a jump target, otherwise it might
            // just be a marker between invoke-* and move-result* instructions.
            if (label.isJumpTarget())
                fixStackAfterAMethodCallOrAnExceptionHandler();

            // Exception handlers have the exception on top of the stack.
            exceptionOnTop = exceptionHandlerSet.contains(label);

            // Merge register states at current jump target labels.
            createOrMergeJoinPoint(label);
            mv.visitLabel(getASMLabel(label));

            // If it's the start of a try, find the corresponding handler.
            Label handler = exceptionTryStartToHandlerMap.get(label);
            if (handler != null) {
                createOrMergeJoinPoint(handler);
            }
        }

        @Override
        public void visitInstrGoto(Opcode opcode, Label label) {
            fixStackAfterAMethodCallOrAnExceptionHandler();
            createOrMergeJoinPoint(label);
            
            mv.visitJumpInsn(GOTO, getASMLabel(label));
            interpreter.setDead();
        }
        
        @Override
        public void visitInstrIfTestZ(final Opcode opcode, int vsrc, Label label) {
            fixStackAfterAMethodCallOrAnExceptionHandler();
            final int ssrc = registerToSlot(vsrc);

            int type, javaOpcode;
            final org.objectweb.asm.Label asmLabel = getASMLabel(label);
            if (opcode == Opcode.IF_EQZ || opcode == Opcode.IF_NEZ) {
                final Register register = interpreter.getRegister(ssrc);
                int srcType = register.getType();
                if (srcType == U32_TYPE) {
                    mv.visitVarInsn(ILOAD, ssrc);
                    final AbstractInsnNode iload = mv.getLastInsnNode();
                    javaOpcode = (opcode == Opcode.IF_EQZ)? IFEQ: IFNE;
                    mv.visitJumpInsn(javaOpcode, asmLabel);
                    final AbstractInsnNode jump = mv.getLastInsnNode();
                    
                    interpreter.storeUntypedType(ssrc, U32_TYPE, new Patchable() {
                        @Override
                        protected void patch(int registerType) {
                            register.getPatchable().doPatch(registerType);
                            if (registerType == OBJECT_TYPE || Register.isArray(registerType)) {
                                mv.patch(iload, new VarInsnNode(ALOAD, ssrc));
                                int javaOpcode = (opcode == Opcode.IF_EQZ)? IFNULL: IFNONNULL;
                                mv.patch(jump, new JumpInsnNode(javaOpcode, new LabelNode(asmLabel)));
                                return;
                            }
                        }
                    });
                    
                    createOrMergeJoinPoint(label);
                    return;
                }  

                if (srcType == OBJECT_TYPE || Register.isArray(srcType)) {
                    type = OBJECT_TYPE;
                    javaOpcode = (opcode == Opcode.IF_EQZ)? IFNULL: IFNONNULL;
                } else { // a numeric type convertible to an int
                    type = INT_TYPE;
                    javaOpcode = (opcode == Opcode.IF_EQZ)? IFEQ: IFNE;
                }
            } else {   // IF_LTZ, IF_GEZ, IF_GTZ, IF_LEZ
                type = INT_TYPE;
                javaOpcode = toJavaOpcode[opcode.ordinal()];
            }
            
            mv.visitVarInsn(Register.getJavaOpcode(type, ILOAD), ssrc);
            interpreter.load(ssrc, type);
            mv.visitJumpInsn(javaOpcode, asmLabel);
            
            createOrMergeJoinPoint(label);
        }
        
        @Override
        public void visitInstrIfTest(final Opcode opcode, int vvsrc1, int vvsrc2,
                Label label) {
            fixStackAfterAMethodCallOrAnExceptionHandler();
            final int vsrc1 = registerToSlot(vvsrc1);
            final int vsrc2 = registerToSlot(vvsrc2);
            
            int type, javaOpcode;
            final org.objectweb.asm.Label asmLabel = getASMLabel(label);
            if (opcode == Opcode.IF_EQ || opcode == Opcode.IF_NE) {
                final Register register1 = interpreter.getRegister(vsrc1);
                int src1Type = register1.getType();
                final Register register2 = interpreter.getRegister(vsrc2);
                int src2Type = register2.getType();
                
                if (src1Type == U32_TYPE && src2Type == U32_TYPE) {
                    mv.visitVarInsn(ILOAD, vsrc1);
                    final AbstractInsnNode iload1 = mv.getLastInsnNode();
                    mv.visitVarInsn(ILOAD, vsrc2);
                    final AbstractInsnNode iload2 = mv.getLastInsnNode();
                    javaOpcode = (opcode == Opcode.IF_EQ)? IF_ICMPEQ: IF_ICMPNE;
                    mv.visitJumpInsn(javaOpcode, asmLabel);
                    final AbstractInsnNode jump = mv.getLastInsnNode();
                    
                    final Patchable patchableUnion = (register1 == register2)? register1.getPatchable():
                        Patchable.union(register1.getPatchable(), register2.getPatchable());
                    
                    Patchable patchable = new Patchable() {
                        @Override
                        protected void patch(int registerType) {
                            patchableUnion.doPatch(registerType);
                            if (registerType == OBJECT_TYPE || Register.isArray(registerType)) {
                                mv.patch(iload1, new VarInsnNode(ALOAD, vsrc1));
                                mv.patch(iload2, new VarInsnNode(ALOAD, vsrc2));
                                int javaOpcode = (opcode == Opcode.IF_EQ)? IF_ACMPEQ: IF_ACMPNE;
                                mv.patch(jump, new JumpInsnNode(javaOpcode, new LabelNode(asmLabel)));
                                return;
                            }
                        }
                    };
                    interpreter.storeUntypedType(vsrc1, U32_TYPE, patchable);
                    if (vsrc1 != vsrc2) {
                        interpreter.storeUntypedType(vsrc2, U32_TYPE, patchable);
                    }
                    
                    createOrMergeJoinPoint(label);
                    return;
                }  
                
                if (src1Type == OBJECT_TYPE || src2Type == OBJECT_TYPE
                        || Register.isArray(src1Type)
                        || Register.isArray(src2Type)) {
                    type = OBJECT_TYPE;
                    javaOpcode = (opcode == Opcode.IF_EQ) ? IF_ACMPEQ
                            : IF_ACMPNE;
                } else { // a numeric type convertible to an int
                    type = INT_TYPE;
                    javaOpcode = (opcode == Opcode.IF_EQ) ? IF_ICMPEQ
                            : IF_ICMPNE;
                }
            } else { // IF_LT, IF_GE, IF_GT, IF_LE
                type = INT_TYPE;
                javaOpcode = toJavaOpcode[opcode.ordinal()];
            }
            
            mv.visitVarInsn(Register.getJavaOpcode(type, ILOAD), vsrc1);
            interpreter.load(vsrc1, type);
            mv.visitVarInsn(Register.getJavaOpcode(type, ILOAD), vsrc2);
            interpreter.load(vsrc2, type);
            mv.visitJumpInsn(javaOpcode, asmLabel);
            
            createOrMergeJoinPoint(label);
        }
        
        @Override
        public void visitInstrNewArray(Opcode opcode, int vdest, int vsize,
                String typeDesc) {
            fixStackAfterAMethodCallOrAnExceptionHandler();
            vdest = registerToSlot(vdest);
            vsize = registerToSlot(vsize);
            mv.visitVarInsn(ILOAD, vsize);
            interpreter.load(vsize, INT_TYPE);
            
            String componentTypeDesc = typeDesc.substring(1);
            Type asmComponentType = Type.getType(componentTypeDesc);
            int componentType = getTypeFromASMType(asmComponentType);
            if (Register.isArray(componentType) || componentType == OBJECT_TYPE) {
                mv.visitTypeInsn(ANEWARRAY, asmComponentType.getInternalName());
            } else {
                mv.visitIntInsn(NEWARRAY, getNewArrayKindFromASMType(asmComponentType));
            }
            
            mv.visitVarInsn(ASTORE, vdest);
            interpreter.store(vdest, Register.makeArray(componentType, 1));
        }
        
        @Override
        public void visitInstrFilledNewArray(Opcode opcode, int num, int va,
                int vpacked, String desc) {
            fixStackAfterAMethodCallOrAnExceptionHandler();
            
            int[] registers;
            if (opcode == Opcode.FILLED_NEW_ARRAY_RANGE) {
                registers = null;
            } else {
                registers = new int[] { getRegisterD(vpacked),
                        getRegisterE(vpacked), getRegisterF(vpacked),
                        getRegisterG(vpacked), va };
            }
            
            Type asmType = Type.getType(desc.substring(1));
            int type = getTypeFromASMType(asmType);
            mv.visitLdcInsn(num);   // array length
            if (Register.isArray(type) || type == OBJECT_TYPE) {
                mv.visitTypeInsn(ANEWARRAY, asmType.getInternalName());
            } else {
                mv.visitIntInsn(NEWARRAY, getNewArrayKindFromASMType(asmType));
            }
            
            for (int r=0, i=0; r<num; r += asmType.getSize()) {
                int register = registerToSlot((registers == null) ? va + r
                        : registers[r]);
                mv.visitInsn(DUP);     // array
                mv.visitLdcInsn(i);    // index
                mv.visitVarInsn(Register.getJavaOpcode(type, ILOAD), register);
                interpreter.load(register, type);
                mv.visitInsn(Register.getJavaOpcode(type, IASTORE));
                
                i++;
            }
            
            returnRegisterType = Register.makeArray(type, 1);
        }
        
        @Override
        public void visitInstrArray(Opcode opcode, int vsrcOrDest, int varray,
                int vindex) {
            fixStackAfterAMethodCallOrAnExceptionHandler();
            vsrcOrDest = registerToSlot(vsrcOrDest);
            varray = registerToSlot(varray);
            vindex = registerToSlot(vindex);
            int type = interpreter.getRegister(varray).getType();
            int componentType = Register.getComponentType(type);
            interpreter.load(vindex, INT_TYPE);
            switch(opcode) {
            case APUT: case APUT_WIDE: case APUT_OBJECT:
            case APUT_BOOLEAN: case APUT_BYTE: case APUT_CHAR: case APUT_SHORT:
                mv.visitVarInsn(Register.getJavaOpcode(type, ILOAD), varray);
                mv.visitVarInsn(ILOAD, vindex);
                mv.visitVarInsn(Register.getJavaOpcode(componentType, ILOAD), vsrcOrDest);
                mv.visitInsn(Register.getJavaOpcode(componentType, IASTORE));
                interpreter.load(vsrcOrDest, componentType);
                break;
            default:
            //case AGET: case AGET_WIDE: case AGET_OBJECT:
            //case AGET_BOOLEAN: case AGET_BYTE: case AGET_CHAR: case AGET_SHORT:
                mv.visitVarInsn(Register.getJavaOpcode(type, ILOAD), varray);
                mv.visitVarInsn(ILOAD, vindex);
                mv.visitInsn(Register.getJavaOpcode(componentType, IALOAD));
                mv.visitVarInsn(Register.getJavaOpcode(componentType, ISTORE), vsrcOrDest);
                interpreter.store(vsrcOrDest, componentType);
                break;
            }
        }
        
        @Override
        public void visitInstrInstanceof(Opcode opcode, int vdest, int vsrc,
                String type) {
            fixStackAfterAMethodCallOrAnExceptionHandler();
            vdest = registerToSlot(vdest);
            vsrc = registerToSlot(vsrc);
            mv.visitVarInsn(ALOAD, vsrc);
            Type asmType = Type.getType(type);
            interpreter.load(vsrc, getTypeFromASMType(asmType));
            mv.visitTypeInsn(INSTANCEOF, asmType.getInternalName());
            mv.visitVarInsn(ISTORE, vdest);
            interpreter.store(vdest, BOOLEAN_TYPE);
        }
        
        @Override
        public void visitTryCatch(Label start, Label end, Label handler, String type) {
            exceptionHandlerSet.add(handler);
            exceptionTryStartToHandlerMap.put(start, handler);
            type = (type == null)? null: TypeUtil.convertDescToInternal(type);
            mv.visitTryCatchBlock(getASMLabel(start), getASMLabel(end),
                    getASMLabel(handler),
                    type);
        }
        
        @Override
        public void visitInstrPackedSwitch(Opcode opcode, int vsrc,
                int firstKey, Label[] targets) {
            fixStackAfterAMethodCallOrAnExceptionHandler();
            vsrc = registerToSlot(vsrc);

            int length = targets.length;
            org.objectweb.asm.Label[] asmLabels = new org.objectweb.asm.Label[length];
            for (int i = 0; i < length; i++) {
                asmLabels[i] = getASMLabel(targets[i]);
            }

            org.objectweb.asm.Label defaultLabel = new org.objectweb.asm.Label();
            mv.visitVarInsn(ILOAD, vsrc);
            interpreter.load(vsrc, INT_TYPE);
            mv.visitTableSwitchInsn(firstKey, firstKey + length - 1,
                    defaultLabel, asmLabels);
            mv.visitLabel(defaultLabel);
        }

        @Override
        public void visitInstrSparseSwitch(Opcode opcode, int vsrc, int[] keys,
                Label[] targets) {
            fixStackAfterAMethodCallOrAnExceptionHandler();
            vsrc = registerToSlot(vsrc);
            
            int length = targets.length;
            org.objectweb.asm.Label[] asmLabels = new org.objectweb.asm.Label[length];
            for (int i = 0; i < length; i++) {
                asmLabels[i] = getASMLabel(targets[i]);
            }

            org.objectweb.asm.Label defaultLabel = new org.objectweb.asm.Label();
            mv.visitVarInsn(ILOAD, vsrc);
            interpreter.load(vsrc, INT_TYPE);
            mv.visitLookupSwitchInsn(defaultLabel, keys, asmLabels);
            mv.visitLabel(defaultLabel);
        }

        @Override
        public void visitInstrFillArrayData(Opcode opcode, int vsrc,
                int elementWidth, int elementNumber, ByteBuffer data) {
            fixStackAfterAMethodCallOrAnExceptionHandler();
            vsrc = registerToSlot(vsrc);

            int type = interpreter.getRegister(vsrc).getType();
            int componentType = Register.getComponentType(type);
            mv.visitVarInsn(ALOAD, vsrc);
            for (int i = 0; i < elementNumber; i++) {
                Object value = readTypedValue(data, componentType);
                if (i < elementNumber - 1)
                    mv.visitInsn(DUP);   // array
                mv.visitLdcInsn(i);      // index
                mv.visitLdcInsn(value);  // value
                mv.visitInsn(Register.getJavaOpcode(componentType, IASTORE));
            }
        }

        @Override
        public void visitEnd() {
            mv.visitMaxs(-1, -1);
            mv.visitEnd();
        }
    };

    private static class AnnotationRewriter implements DexAnnotationVisitor {
        private final AnnotationVisitor av;

        public AnnotationRewriter(AnnotationVisitor av) {
            this.av = av;
        }

        @Override
        public DexAnnotationVisitor visitAnnotation(String name, String type) {
            AnnotationVisitor av = this.av.visitAnnotation(name, type);
            if (av == null)
                return null;
            return new AnnotationRewriter(av);
        }

        @Override
        public DexAnnotationVisitor visitArray(String name, int size) {
            AnnotationVisitor av = this.av.visitArray(name);
            if (av == null)
                return null;
            return new AnnotationRewriter(av);
        }

        @Override
        public void visitEnum(String name, String enumOwner, String enumName) {
            av.visitEnum(name, enumOwner, enumName);
        }

        @Override
        public void visitField(String name, String fieldOwner,
                String fieldName, String fieldDesc) {
            throw new UnsupportedOperationException("Unexpected parameter");
        }

        @Override
        public void visitMethod(String name, String methodOwner,
                String methodName, String methodDesc) {
            throw new UnsupportedOperationException("Unexpected parameter");
        }

        @Override
        public void visitPrimitive(String name, Object value) {
            av.visit(name, value);
        }

        @Override
        public void visitType(String name, String typeDesc) {
            av.visit(name, Type.getType(typeDesc));
        }

        @Override
        public void visitEnd() {
            av.visitEnd();
        }
    };

    static final int[] toJavaOpcode;
    static { // this string is generated using DalvikToJVMEncoder
        String text = "AA@@@@@@@@@@@@@@@@@@@@@@@@@@LB@@@@@@@@@@@@@@@@@@@@@@@@@@@@MCMDMAMBLOLLLM@@@@@@LPKHKHKH@@@@JFJGJHJIJE@@@@KBKCKDKE@@@@JLJMJNJO@@@@@@@@@@@@@@@@DCDDDDDEDF@@@@FDFEFEFFFG@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@HE@@HF@@HGHHIFIGIHIIIJIKILIMINIOIPJAJBJCJDGAGEGIGMHAGAIAICHIHKHMGBGFGJGNHBHPIBIDHJHLHNGCGGGKGOHCGDGHGLGPHDGAGEGIGMHAHOIAICHIHKHMGBGFGJGNHBHPIBIDHJHLHNGCGGGKGOHCGDGHGLGPHDGAGEGIGMHAGAIAICGAGEGIGMHAHOIAICHIHKHM@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@";
        int[] array = new int[256];
        int length = text.length();
        for (int i = 0; i < length; i += 2) {
            int index = ((text.charAt(i) - 'A') << 4)
                    | (text.charAt(i + 1) - 'A');
            array[i >> 1] = index;
        }
        toJavaOpcode = array;
    }
}
