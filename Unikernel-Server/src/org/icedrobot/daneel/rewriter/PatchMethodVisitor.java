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

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.Label;
//import org.objectweb.asm.MethodHandle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * A method visitor which is able to patch some of its instruction. To avoid the
 * overhead of always storing all instructions in memory, to patch at least one
 * instruction, the {@link #setPatchMode() patch mode} must be activated.
 * 
 * By default, this method visitor delegates to the underlying method visitor
 * but if the {@link #setPatchMode() patch mode} is activated, all new
 * instructions are stored and dumped when {@link #visitMaxs(int, int)} is
 * called.
 * 
 * So if the patch mode is activated, one can get the last instruction
 * {@link #getLastInsnNode()}, remember it, and replace it later using
 * {@link #patch(AbstractInsnNode, AbstractInsnNode)} with another instruction.
 * 
 * The methods {@link #getLastInsnNode()} and
 * {@link #patch(AbstractInsnNode, AbstractInsnNode)} can only be called is the
 * patch mode is activated. After a call to {@link #visitMaxs(int, int)}, the
 * patch mode is deactivated.
 */
public class PatchMethodVisitor implements MethodVisitor {
    private final MethodVisitor mv;
    private MethodNode methodNode;

    /**
     * Create a method visitor that can be patched.
     * @param mv the adapted method visitor.
     */
    public PatchMethodVisitor(MethodVisitor mv) {
        this.mv = mv;
    }

    @Override
    public AnnotationVisitor visitAnnotationDefault() {
        return mv.visitAnnotationDefault();
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        return mv.visitAnnotation(desc, visible);
    }

    @Override
    public AnnotationVisitor visitParameterAnnotation(int parameter,
            String desc, boolean visible) {
        return mv.visitParameterAnnotation(parameter, desc, visible);
    }

    @Override
    public void visitAttribute(Attribute attr) {
        mv.visitAttribute(attr);
    }

    @Override
    public void visitCode() {
        mv.visitCode();
    }

    private MethodVisitor mv() {
        return (methodNode == null) ? mv : methodNode;
    }

    /**
     * Switch to patch mode. If the patch mode is already activated, this method
     * does nothing.
     * 
     * @see #getLastInsnNode()
     * @see #patch(AbstractInsnNode, AbstractInsnNode)
     */
    public void setPatchMode() {
        if (methodNode == null) {
            methodNode = new MethodNode();
        }
    }

    /**
     * Returns the last instruction node added by visiting an instruction or
     * explicitly by {@link #addNode(AbstractInsnNode)}.
     * 
     * @return the last instruction node added to the current visitor.
     * @throws IllegalStateException if the {@link #setPatchMode() patch mode}
     *         is not activated.
     * 
     * @see #setPatchMode()
     */
    public AbstractInsnNode getLastInsnNode() {
        if (methodNode == null) {
            throw new IllegalStateException("patchMode is not activated");
        }
        return methodNode.instructions.getLast();
    }
    
    /**
     * Adds a new instruction node to the current visitor.
     * 
     * @param node the instruction to add.
     * @throws IllegalStateException if the {@link #setPatchMode() patch mode}
     *         is not activated.
     *         
     * @see #setPatchMode()
     */
    public void addNode(AbstractInsnNode node) {
        if (methodNode == null) {
            throw new IllegalStateException("patchMode is not activated");
        }
        methodNode.instructions.add(node);
    }

    /**
     * Patch i.e. replace an instruction node previously added to
     * a new instruction node. 
     * 
     * @param node node to be replaced.
     * @param newNode node to be inserted.
     * @throws IllegalStateException if the {@link #setPatchMode() patch mode}
     *         is not activated.
     *         
     * @see #setPatchMode()
     */
    public void patch(AbstractInsnNode node, AbstractInsnNode newNode) {
        if (methodNode == null) {
            throw new IllegalStateException("patchMode is not activated");
        }
        methodNode.instructions.set(node, newNode);
    }

    @Override
    public void visitFrame(int type, int nLocal, Object[] local, int nStack,
            Object[] stack) {
        mv().visitFrame(type, nLocal, local, nStack, stack);
    }

    @Override
    public void visitInsn(int opcode) {
        mv().visitInsn(opcode);
    }

    @Override
    public void visitIntInsn(int opcode, int operand) {
        mv().visitIntInsn(opcode, operand);
    }

    @Override
    public void visitVarInsn(int opcode, int var) {
        mv().visitVarInsn(opcode, var);
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        mv().visitTypeInsn(opcode, type);
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name,
            String desc) {
        mv().visitFieldInsn(opcode, owner, name, desc);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name,
            String desc) {
        mv().visitMethodInsn(opcode, owner, name, desc);
    }

    // @Override
    // public void visitInvokeDynamicInsn(
    // String name,
    // String desc,
    // MethodHandle bsm,
    // Object... bsmArgs) {
    // mv().visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
    // }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
        mv().visitJumpInsn(opcode, label);
    }

    @Override
    public void visitLabel(Label label) {
        mv().visitLabel(label);
    }

    @Override
    public void visitLdcInsn(Object cst) {
        mv().visitLdcInsn(cst);
    }

    @Override
    public void visitIincInsn(int var, int increment) {
        mv().visitIincInsn(var, increment);
    }

    @Override
    public void visitTableSwitchInsn(int min, int max, Label dflt,
            Label[] labels) {
        mv().visitTableSwitchInsn(min, max, dflt, labels);
    }

    @Override
    public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
        mv().visitLookupSwitchInsn(dflt, keys, labels);
    }

    @Override
    public void visitMultiANewArrayInsn(String desc, int dims) {
        mv().visitMultiANewArrayInsn(desc, dims);
    }

    @Override
    public void visitTryCatchBlock(Label start, Label end, Label handler,
            String type) {
        mv().visitTryCatchBlock(start, end, handler, type);
    }

    @Override
    public void visitLocalVariable(String name, String desc, String signature,
            Label start, Label end, int index) {
        mv.visitLocalVariable(name, desc, signature, start, end, index);
    }

    @Override
    public void visitLineNumber(int line, Label start) {
        mv().visitLineNumber(line, start);
    }

    @Override
    public void visitMaxs(int maxStack, int maxLocals) {
        if (methodNode != null) {
            final MethodVisitor mv = this.mv;
            for (AbstractInsnNode insn = methodNode.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                insn.accept(mv);
            }
            methodNode = null;
        }
        mv.visitMaxs(maxStack, maxLocals);
    }

    @Override
    public void visitEnd() {
        mv.visitEnd();
    }
}
