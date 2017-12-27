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

/**
 * Definition of all access flags as used in Dalvik VM executables.
 */
public class AccessFlags {

    public static final int ACC_PUBLIC = 0x1;
    public static final int ACC_PRIVATE = 0x2;
    public static final int ACC_PROTECTED = 0x4;
    public static final int ACC_STATIC = 0x8;
    public static final int ACC_FINAL = 0x10;
    public static final int ACC_SYNCHRONIZED = 0x20;
    public static final int ACC_VOLATILE = 0x40;
    public static final int ACC_BRIDGE = 0x40;
    public static final int ACC_TRANSIENT = 0x80;
    public static final int ACC_VARARGS = 0x80;
    public static final int ACC_NATIVE = 0x100;
    public static final int ACC_INTERFACE = 0x200;
    public static final int ACC_ABSTRACT = 0x400;
    public static final int ACC_STRICT = 0x800;
    public static final int ACC_SYNTHETIC = 0x1000;
    public static final int ACC_ANNOTATION = 0x2000;
    public static final int ACC_ENUM = 0x4000;
    // (unused) = 0x8000
    public static final int ACC_CONSTRUCTOR = 0x10000;
    public static final int ACC_DECLARED_SYNCHRONIZED = 0x20000;

    /**
     * Checks whether the given access flags field has the {@link #ACC_STATIC}
     * flag set or not.
     * 
     * @param flags The given access flags field.
     * @return True if access flag is set, false otherwise.
     */
    public static boolean isStatic(int flags) {
        return (flags & ACC_STATIC) != 0;
    }

    private AccessFlags() {
        // No instances of this class.
    }
}
