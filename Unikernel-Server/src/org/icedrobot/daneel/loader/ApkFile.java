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
package org.icedrobot.daneel.loader;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.icedrobot.daneel.DaneelException;
import org.icedrobot.daneel.dex.DexFile;

/**
 * This class reads the contents of an APK file. Those APK files are actually
 * JAR files which also contain resources and classes, but with all classes
 * packed into one DEX file within the archive.
 */
public class ApkFile extends JarFile {

    /** The actual file object for this APK file. */
    private final File file;

    /**
     * Creates a new reader for the APK file represented by the given object.
     * @param file The given file object.
     * @throws IOException In case of an error while accessing the file.
     */
    public ApkFile(File file) throws IOException {
        super(file);
        this.file = file;
    }

    /**
     * Creates a new reader for the APK file represented by the given name.
     * @param name The given file name.
     * @throws IOException In case of an error while accessing the file.
     */
    public ApkFile(String name) throws IOException {
        super(name);
        this.file = new File(name);
    }

    /**
     * Returns the DEX file containing all the classes of this APK file.
     * @return The contained DEX file object.
     * @throws IOException In case of an error while accessing the file.
     * @throws IllegalStateException if the APK doesn't contains any classes.
     */
    public DexFile getDexFile() throws IOException {
        ZipEntry entry = getEntry("classes.dex");
        if (entry == null)
            throw new IllegalStateException("The APK doesn't contain classes.");
        
        return DexFile.parse(getInputStream(entry), entry.getSize());
    }

    /**
     * Constructs an URL representation for the specified entry inside the APK
     * file. See {@link java.net.JarURLConnection} for details about the syntax
     * of such an URL.
     * @param entry The given entry inside this APK file.
     * @return The URL representation for the entry.
     */
    public URL getJarURL(ZipEntry entry) {
        try {
            return new URL("jar:" + file.toURI() + "!/" + entry.getName());
        } catch (MalformedURLException e) {
            throw new DaneelException("Unable to construct URL.", e);
        }
    }
}
