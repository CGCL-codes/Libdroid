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

/*
 * A non negligible part of this code comes from a code
 * that can be found in ASM's class file CheckClassAdapter.java.
 * 
 * ASM: a very small and fast Java bytecode manipulation framework
 * Copyright (c) 2000-2007 INRIA, France Telecom
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holders nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.icedrobot.daneel.loader;

import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;

import org.icedrobot.daneel.dex.DexAnnotationVisitor;
import org.icedrobot.daneel.dex.DexClassVisitor;
import org.icedrobot.daneel.dex.DexFieldVisitor;
import org.icedrobot.daneel.dex.DexFile;
import org.icedrobot.daneel.dex.DexMethodVisitor;
import org.icedrobot.daneel.dex.DexReader;
import org.icedrobot.daneel.dex.Label;
import org.icedrobot.daneel.dex.Opcode;
import org.icedrobot.daneel.rewriter.DexRewriter.MethodRewriter;
import org.icedrobot.daneel.rewriter.Interpreter;
import org.icedrobot.daneel.util.TypeUtil;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.EmptyVisitor;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.TraceMethodVisitor;

public class Verifier {
    public static void verify(ClassLoader classloader, DexFile dexFile, String className, byte[] bytecode, PrintWriter writer) {
        ClassReader reader = new ClassReader(bytecode);
        ClassNode node = new ClassNode();
        reader.accept(new CheckClassAdapter(node, false),
                ClassReader.SKIP_DEBUG);

        // Type superType = (node.superName == null) ? null : Type.getObjectType(node.superName);

        @SuppressWarnings("unchecked")
        List<String> interfaces = node.interfaces;
        Type[] interfazes = new Type[interfaces.size()];
        for (int i = 0; i < interfazes.length; i++) {
            interfazes[i] = Type.getObjectType(interfaces.get(i));
        }

        @SuppressWarnings("unchecked")
        List<MethodNode> methods = node.methods;
        String internalName = node.name;
        for (int i = 0; i < methods.size(); i++) {
            MethodNode method = methods.get(i);
            /*SimpleVerifier verifier = new SimpleVerifier(
                    Type.getObjectType(internalName),
                    superType,
                    Arrays.asList(interfazes),
                    (node.access & ~Opcodes.ACC_INTERFACE) != 0);*/
            BasicVerifier verifier = new BasicVerifier();
            Analyzer analyzer = new Analyzer(verifier);
            //verifier.setClassLoader(classloader);

            try {
                analyzer.analyze(internalName, method);
            } catch (Exception e) {
                dump(dexFile, className, method, analyzer, writer);
                e.printStackTrace(writer);
            }
        }
        writer.flush();
    }

    private static void dump(DexFile dexFile, final String className, final MethodNode method,
            Analyzer analyzer, final PrintWriter writer) {
        writer.println("--- DEX bytecode");
        try {
            dexFile.accept("L" + className.replace('.', '/') + ";", new DexClassVisitor() {
                @Override
                public void visit(int access, String name, String supername,
                        String[] interfaces) {
                    // nothing
                }
                
                @Override
                public void visitSource(String source) {
                    // nothing
                }
                
                @Override
                public DexAnnotationVisitor visitAnnotation(int visibility, String type) {
                    return null;
                }
                
                @Override
                public DexMethodVisitor visitMethod(int access, String name, String shorty,
                        String returnType, String[] parameterTypes) {
                    
                    String desc = TypeUtil.convertProtoToDesc(returnType, parameterTypes);
                    if (!name.equals(method.name) || !desc.equals(method.desc)) {
                        return null;
                    }
                    
                    final MethodRewriter mv = new MethodRewriter(new EmptyVisitor(),
                            (access & Opcodes.ACC_STATIC) != 0,
                            desc);
                    
                    writer.println(className+'.'+method.name + method.desc);
                    return new DexMethodVisitor() {
                        private final HashMap<Label, String> labelMap =
                            new HashMap<Label, String>();
                        private final StringBuilder builder = new StringBuilder();
                        
                        private String getLabel(Label label) {
                            String text = labelMap.get(label);
                            if (text != null) {
                                return text;
                            }
                            text = "L" + labelMap.size();
                            labelMap.put(label, text);
                            return text;
                        }
                        
                        private void dumpRegisters() {
                            StringBuilder registerBuilder = new StringBuilder(); 
                            Interpreter interpreter = mv.getInterpreter();
                            for(int i=0; i<interpreter.getRegisterCount(); i++) {
                                registerBuilder.append(interpreter.getRegister(i));
                                if (i != interpreter.getRegisterCount() - 1)
                                    registerBuilder.append(' ');
                            }
                            writer.println(String.format("%-50s %s", builder, registerBuilder));
                            builder.setLength(0);
                        }
                        
                        @Override
                        public void visitCode(int registers, int ins, int outs) {
                            builder.append("code registers:"+registers+" ins:"+ins+" outs:"+outs);
                            mv.visitCode(registers, ins, outs);
                            dumpRegisters();
                        }
                        
                        @Override
                        public DexAnnotationVisitor visitAnnotation(int visibility, String type) {
                            return null;
                        }
                        
                        @Override
                        public DexAnnotationVisitor visitParameterAnnotation(int parameter,
                                int visibility, String type) {
                            return null;
                        }
                        
                        @Override
                        public void visitLocalVariable(String name, String desc, Label start,
                                Label end, int reg) {
                            mv.visitLocalVariable(name, desc, start, end, reg);
                        }
                        
                        @Override
                        public void visitLineNumber(String source, int line, Label start) {
                            mv.visitLineNumber(source, line, start);
                        }
                        
                        @Override
                        public void visitTryCatch(Label start, Label end, Label handler, String type) {
                            builder.append("try-catch "+type+" "+getLabel(start)+" "+getLabel(end)+" "+getLabel(handler));
                            mv.visitTryCatch(start, end, handler, type);
                            dumpRegisters();
                        }
                        
                        @Override
                        public void visitLabel(Label label) {
                            builder.append(getLabel(label)+":");
                            mv.visitLabel(label);
                            dumpRegisters();
                        }
                        
                        @Override
                        public void visitInstr(Opcode opcode) {
                            builder.append(opcode.name());
                            mv.visitInstr(opcode);
                            dumpRegisters();
                        }
                        
                        @Override
                        public void visitInstrOp(Opcode opcode, int srcOrDst) {
                            builder.append(opcode.name()+" "+srcOrDst);
                            mv.visitInstrOp(opcode, srcOrDst);
                            dumpRegisters();
                        }
                        
                        @Override
                        public void visitInstrUnaryOp(Opcode opcode, int vdest, int vsrc) {
                            builder.append(opcode.name()+" "+vdest+", "+vsrc);
                            mv.visitInstrUnaryOp(opcode, vdest, vsrc);
                            dumpRegisters();
                        }
                        
                        @Override
                        public void visitInstrBinOpAndLiteral(Opcode opcode, int vdest, int vsrc,
                                int value) {
                            builder.append(opcode.name()+" "+vdest+", "+vsrc+" #"+value);
                            mv.visitInstrBinOpAndLiteral(opcode, vdest, vsrc, value);
                            dumpRegisters();
                        }
                        
                        @Override
                        public void visitInstrBinOp(Opcode opcode, int vdest, int vsrc1, int vsrc2) {
                            builder.append(opcode.name()+" "+vdest+", "+vsrc1+", "+vsrc2);
                            mv.visitInstrBinOp(opcode, vdest, vsrc1, vsrc2);
                            dumpRegisters();
                        }
                        
                        @Override
                        public void visitInstrMethod(Opcode opcode, int num, int va, int vpacked,
                                String owner, String name, String desc) {
                            builder.append(opcode.name()+" "+owner+"."+name+desc+" "+num+" "+va+" "+vpacked);
                            mv.visitInstrMethod(opcode, num, va, vpacked, owner, name, desc);
                            dumpRegisters();
                        }
                        
                        @Override
                        public void visitInstrField(Opcode opcode, int vsrcOrDest, int vref,
                                String owner, String name, String desc) {
                            builder.append(opcode.name()+" "+owner+"."+name+desc+" "+vsrcOrDest+" "+vref);
                            mv.visitInstrField(opcode, vsrcOrDest, vref, owner, name, desc);
                            dumpRegisters();
                        }
                        
                        @Override
                        public void visitInstrConstU32(Opcode opcode, int vdest, int value) {
                            builder.append(opcode.name()+" "+vdest+" #"+value);
                            mv.visitInstrConstU32(opcode, vdest, value);
                            dumpRegisters();
                        }
                        
                        @Override
                        public void visitInstrConstU64(Opcode opcode, int vdest, long value) {
                            builder.append(opcode.name()+" "+vdest+" #"+value);
                            mv.visitInstrConstU64(opcode, vdest, value);
                            dumpRegisters();
                        }
                        
                        @Override
                        public void visitInstrConstString(Opcode opcode, int vdest, String value) {
                            builder.append(opcode.name()+" "+vdest+" \""+value+'"');
                            mv.visitInstrConstString(opcode, vdest, value);
                            dumpRegisters();
                        }
                        
                        @Override
                        public void visitInstrInstanceof(Opcode opcode, int vdest, int vsrc,
                                String type) {
                            builder.append(opcode.name()+" "+type+" "+vdest+", "+vsrc);
                            mv.visitInstrInstanceof(opcode, vdest, vsrc, type);
                            dumpRegisters();
                        }
                        
                        @Override
                        public void visitInstrClass(Opcode opcode, int vsrcOrDest, String type) {
                            builder.append(opcode.name()+" "+type+" "+vsrcOrDest);
                            mv.visitInstrClass(opcode, vsrcOrDest, type);
                            dumpRegisters();
                        }
                        
                        @Override
                        public void visitInstrGoto(Opcode opcode, Label label) {
                            builder.append(opcode.name()+" "+getLabel(label));
                            mv.visitInstrGoto(opcode, label);
                            dumpRegisters();
                        }
                        
                        @Override
                        public void visitInstrIfTestZ(Opcode opcode, int vsrc, Label label) {
                            builder.append(opcode.name()+" "+vsrc+" "+getLabel(label));
                            mv.visitInstrIfTestZ(opcode, vsrc, label);
                            dumpRegisters();
                        }
                        
                        @Override
                        public void visitInstrIfTest(Opcode opcode, int vsrc1, int vsrc2,
                                Label label) {
                            builder.append(opcode.name()+" "+vsrc1+", "+vsrc2+" "+getLabel(label));
                            mv.visitInstrIfTest(opcode, vsrc1, vsrc2, label);
                            dumpRegisters();
                        }
                        
                        @Override
                        public void visitInstrNewArray(Opcode opcode, int vdest, int vsize,
                                String type) {
                            builder.append(opcode.name()+" "+type+" "+vdest+"  "+vsize);
                            mv.visitInstrNewArray(opcode, vdest, vsize, type);
                            dumpRegisters();
                        }
                        
                        @Override
                        public void visitInstrFilledNewArray(Opcode opcode, int num, int va,
                                int vpacked, String type) {
                            builder.append(opcode.name()+" "+type+" "+num+" "+va+",  "+vpacked);
                            mv.visitInstrFilledNewArray(opcode, num, va, vpacked, type);
                        }
                        
                        @Override
                        public void visitInstrFillArrayData(Opcode opcode, int vsrc,
                                int elementWidth, int elementNumber, ByteBuffer data) {
                            builder.append(opcode.name()+" "+vsrc+" "+elementWidth+" "+elementNumber);
                            mv.visitInstrFillArrayData(opcode, vsrc, elementWidth, elementNumber, data);
                            dumpRegisters();
                            while (data.hasRemaining()) {
                                writer.print((int)data.get());
                                if (data.hasRemaining()) {
                                    writer.print(", ");
                                }
                            }
                            writer.println();
                        }
                        
                        @Override
                        public void visitInstrArray(Opcode opcode, int vsrcOrDest, int varray,
                                int vindex) {
                            builder.append(opcode.name()+" "+vsrcOrDest+", "+varray+"  "+vindex);
                            mv.visitInstrArray(opcode, vsrcOrDest, varray, vindex);
                            dumpRegisters();
                        }
                        
                        
                        @Override
                        public void visitInstrSparseSwitch(Opcode opcode, int vsrc, int[] keys,
                                Label[] targets) {
                            builder.append(opcode.name()+" "+vsrc);
                            mv.visitInstrSparseSwitch(opcode, vsrc, keys, targets);
                            dumpRegisters();
                            for(int i=0; i<keys.length; i++) {
                                writer.println("  "+keys[i]+" "+targets[i]);
                            }
                            writer.println();
                        }
                        
                        @Override
                        public void visitInstrPackedSwitch(Opcode opcode, int vsrc, int firstKey,
                                Label[] targets) {
                            builder.append(opcode.name()+" "+vsrc+" "+firstKey);
                            mv.visitInstrPackedSwitch(opcode, vsrc, firstKey, targets);
                            dumpRegisters();
                            for(int i=0; i<targets.length; i++) {
                                writer.println("  "+targets[i]);
                            }
                            writer.println();
                        }
                        
                        @Override
                        public void visitEnd() {
                            mv.visitEnd();
                        }
                    };
                }
                
                @Override
                public DexFieldVisitor visitField(int access, String name, String type,
                        Object value) {
                    return null;
                }
                
                @Override
                public void visitEnd() {
                   // do nothing
                } 
            }, DexReader.SKIP_NONE);
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
        
        writer.println();
        writer.println("--- JVM bytecode");
        Frame[] frames = analyzer.getFrames();
        TraceMethodVisitor mv = new TraceMethodVisitor();
        
        for (int j = 0; j < method.instructions.size(); ++j) {
            method.instructions.get(j).accept(mv);

            StringBuilder builder = new StringBuilder();
            Frame f = frames[j];
            if (f == null) {
                builder.append('?');
            } else {
                for (int k = 0; k < f.getLocals(); ++k) {
                    builder.append(f.getLocal(k).toString())
                            .append(' ');
                }
                builder.append(" : ");
                for (int k = 0; k < f.getStackSize(); ++k) {
                    builder.append(f.getStack(k).toString())
                            .append(' ');
                }
            }
            while (builder.length() < method.maxStack + method.maxLocals + 1) {
                builder.append(' ');
            }
            writer.print(String.format("%05d", j));
            writer.print(" " + builder + " : " + mv.text.get(j));
        }
        for (int j = 0; j < method.tryCatchBlocks.size(); ++j) {
            ((TryCatchBlockNode)method.tryCatchBlocks.get(j)).accept(mv);
            writer.print(" " + mv.text.get(j));
        }
        writer.println();
    }
}
