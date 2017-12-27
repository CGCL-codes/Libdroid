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
 * A common interface of a DEX file reader which is able to accept visitors. One
 * implementation of such a reader is {@link DexFile}, which parses given DEX
 * files.
 */
public interface DexReader {

    /** Skip no information. Default behavior. */
    public static final int SKIP_NONE = 0;

    /** Skip {@code code_item} information. */
    public static final int SKIP_CODE = 0x1;

    /** Skip {@code debug_info_item} information. */
    public static final int SKIP_DEBUGINFO = 0x2;

    /** Skip {@code annotations_directory_item} information. */
    public static final int SKIP_ANNOTATIONS = 0x4;

    /**
     * Allows the given visitor to visit this DEX file.
     * 
     * @param visitor The given DEX file visitor object.
     * @param skip Flags indicating which information to skip while visiting.
     */
    public void accept(DexFileVisitor visitor, int skip);

    /**
     * Allows the given visitor to visit a class inside this DEX file.
     * 
     * @param className The name of the class to visit as a type descriptor.
     * @param visitor The given DEX class visitor object.
     * @param skip Flags indicating which information to skip while visiting.
     * @throws ClassNotFoundException In case the given class is not defined
     *         inside this DEX file.
     */
    public void accept(String className, DexClassVisitor visitor, int skip)
            throws ClassNotFoundException;
}
