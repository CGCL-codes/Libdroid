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
import java.util.HashMap;

/**
 * An abstract interpreter for virtual registers. It is used to infer the
 * register type information by tracking all load and store operations on them.
 */
public class Interpreter {
    private final Register[] registers;
    private final HashMap<Object, Register[]> joinPointMap = new HashMap<Object, Register[]>();
    private boolean isDead;

    /**
     * Creates a new abstract interpreter capable of tracking a given number of
     * virtual registers.
     * 
     * @param registers The array of registers to handle.
     */
    public Interpreter(Register[] registers) {
       this.registers = registers;
    }

    /**
     * Returns the tracked register for a given register number.
     * 
     * @param vregister The register number of a tracked register.
     * @return The tracked register object.
     */
    public Register getRegister(int vregister) {
        return registers[vregister];
    }
    
    /**
     * Returns the number of virtual registers.
     * 
     * @return the number of virtual registers managed by the current interpreter.
     */
    public int getRegisterCount() {
        return registers.length;
    }

    /**
     * Tracks a load operation on the given register. The expected type has to
     * be a concrete (non-untyped) type. This might trigger the register to
     * change from an untyped to the given concrete type and in turn trigger
     * operation patching.
     * 
     * @param vregister The register number of a tracked register.
     * @param expectedType The expected (non-untyped) type of the register.
     */
    public void load(int vregister, int expectedType) {
        registers[vregister] = registers[vregister].load(expectedType);
    }

    /**
     * Tracks a store operation on the given register. The expected type has to
     * be a concrete (non-untyped) type. The previous content of the given
     * register will be destroyed.
     * 
     * @param vregister The register number of a tracked register.
     * @param type The (non-untyped) type of the operation.
     */
    public void store(int vregister, int type) {
        if (Register.isUntyped(type)) {
            throw new IllegalArgumentException("invalid type");
        }
        
        registers[vregister] = new Register(type, null);
    }

    /**
     * Tracks an untyped typed store operation on the given register. Since
     * the operation is not yet concretely typed, the operation has to be
     * "patchable" to change it's type later on. The previous content of the
     * given register will be destroyed.
     * 
     * @param vregister The register number of a tracked register.
     * @param untypedType The (untyped) type of the operation.
     * @param patchable The patcher to change the operation type.
     */
    public void storeUntypedType(int vregister, int untypedType,
            Patchable patchable) {
        if (!Register.isUntyped(untypedType)) {
            throw new IllegalArgumentException("invalid type");
        }
        
        registers[vregister] = new Register(untypedType, patchable);
    }

    /**
     * Marks a possible join-point in the control flow. Each branch target
     * (basic block boundary) is a candidate for a join-point. The current
     * register information is preserved and associated with the given
     * join-point identity.
     * 
     * @param label An object identity representing the join-point.
     */
    public void cloneJoinPoint(Object label) {
        joinPointMap.put(label, registers.clone());
    }

    /**
     * Queries a previously marked join-point in the control flow. If the given
     * join-point identity hasn't been marked before, {@code null} will be
     * returned.
     * 
     * @param label An object identity representing the join-point.
     * @return The register information at the join-point or {@code null}.
     */
    public Register[] getJoinPoint(Object label) {
        return joinPointMap.get(label);
    }

    /**
     * Marks all tracked registers as dead. This can be useful for code after
     * unconditional branch instructions.
     */
    public void setDead() {
        this.isDead = true;
    }

    /**
     * Checks if all registers are marked as dead.
     * 
     * @return True if all registers are dead, false otherwise.
     */
    public boolean isDead() {
        return isDead;
    }

    /**
     * Merges the current and a given set of register information. This is done
     * at join-points in the control flow. It merges each register, thus
     * changing each register to the most concrete type known at that point.
     * 
     * @param registers The given set of register information.
     */
    public void merge(Register[] registers) {
        assert this.registers.length == registers.length;

        if (isDead) {
            System.arraycopy(registers, 0, this.registers, 0, registers.length);
            isDead = false;
            return;
        }

        for (int i = 0; i < registers.length; i++) {
            Register thisRegister = this.registers[i];
            Register register = registers[i];
            if (thisRegister == register) {
                continue;
            }
            this.registers[i] = thisRegister.merge(register);
        }
    }

    @Override
    public String toString() {
        return Arrays.toString(registers);
    }
}
