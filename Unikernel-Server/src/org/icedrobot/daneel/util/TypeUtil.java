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

package org.icedrobot.daneel.util;

/**
 * Utility methods for mangling names and type descriptors. For specifications
 * about the differences between each form of representation you should visit
 * the following material:
 * <ul>
 * <li>The Java(TM) Virtual Machine Specification, Section 4.2 and 4.3</li>
 * <li>DEX - Dalvik Executable Format, Section "String Syntax"</li>
 * <li>ASM 3.0 User Guide, Section 2.1.2 and 2.1.3</li>
 * </ul>
 */
public class TypeUtil {

    /**
     * Converts a method prototype (separate type descriptors for return type
     * and parameter types) into a method descriptor.
     * 
     * @param returnType The method's return type as a type descriptor or
     *        {@code "V"} in case the method returns {@code void}.
     * @param parameterTypes The method's parameter types as type descriptors or
     *        {@code null} in case there are none.
     * @return The method descriptor for the given method prototype.
     */
    public static String convertProtoToDesc(String returnType,
            String[] parameterTypes) {
        StringBuilder builder = new StringBuilder();
        builder.append('(');
        if (parameterTypes != null)
            for (String parameterType : parameterTypes)
                builder.append(parameterType);
        builder.append(')');
        builder.append(returnType);
        return builder.toString();
    }

    /**
     * Converts a type descriptor into a fully qualified internal class name.
     * 
     * @param desc The given type descriptor.
     * @return The internal class name for the given type descriptor.
     */
    public static String convertDescToInternal(String desc) {
        int index = 0;
        while (desc.charAt(index) == '[') {
            index++;
        }
        int length = desc.length();
        if (desc.charAt(index) != 'L' || desc.charAt(length - 1) != ';')
            throw new IllegalArgumentException(
                    "Descriptor is not a class type: " + desc);
        if (index != 0)
            return desc.substring(0, index).concat(
                    desc.substring(index + 1, length - 1));
        return desc.substring(index + 1, length - 1);
    }

    /**
     * Converts an array of type descriptors into an array of a fully qualified
     * internal class name.
     * 
     * @param descs The given array of type descriptors.
     * @return The internal class names for the given type descriptors.
     */
    public static String[] convertDescToInternals(String[] descs) {
        int length = descs.length;
        String[] internalNames = new String[length];
        for (int i = 0; i < length; i++) {
            internalNames[i] = TypeUtil.convertDescToInternal(descs[i]);
        }
        return internalNames;
    }

    /**
     * Checks whether the given type descriptor is a wide type. That means that
     * it requires two machine words (e.g. register pair, two local variable
     * slots) to store such a value.
     * 
     * @param desc The given type descriptor.
     * @return True if the type is wide, false otherwise.
     */
    public static boolean isWideType(String desc) {
        return desc.equals("J") || desc.equals("D");
    }
}
