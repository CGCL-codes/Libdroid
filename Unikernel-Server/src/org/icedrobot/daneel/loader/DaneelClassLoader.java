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

import org.icedrobot.daneel.dex.DexFile;
import org.icedrobot.daneel.rewriter.DexRewriter;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.zip.ZipEntry;

/**
 * A class loader capable of loading classes compiled for the Dalvik VM. The
 * classes are loaded by transforming them into the Java VM class file format
 * and then defining them in the host VM itself.
 */
public class DaneelClassLoader extends ClassLoader {
    private final File[] files;

    /** The list of all files containing DEX classes (lazy initialized). */
    private DexFile[] dexFiles;
    
    /** Lock used to lazy initialize dexFiles */
    private final Object dexLock = new Object();
    
    /** The list of all files containing resources (lazy initialized). */
    private ApkFile[] resourceFiles;
    
    /** Lock used to lazy initialize resourceFiles */
    private final Object resourcesLock = new Object();

    /**
     * Constructs a new class loader. Keep a constructor with such a signature
     * around so that this class loader can be used as a system class loader.
     * For details take a look at the {@link ClassLoader#getSystemClassLoader()}
     * documentation.
     * 
     * @param parent The parent class loader for delegation.
     * @throws IOException throws if an I/O error occurs while reading a DEX files
     */
    public DaneelClassLoader(ClassLoader parent) throws IOException {
        this(parent, defaultFiles());
    }

    /**
     * Constructs a new class loader for a given set of files from which to load
     * classes and resources.
     * 
     * @param parent The parent class loader for delegation.
     * @param files The set of files from which to load.
     * @throws IOException throws if an I/O error occurs while reading a DEX files
     */
    public DaneelClassLoader(ClassLoader parent, File... files) throws IOException {
        super(parent);
        this.files = files.clone();
    }
    
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        System.out.printf("Trying to find class '%s' ...\n", name);
        
        DexFile[] dexFiles;
        synchronized(dexLock) {
            dexFiles = this.dexFiles;
            if (dexFiles == null) {
                // avoid an infinite loop if loadDexFiles() requires to load a class
                // that isn't available in parent classloaders
                this.dexFiles = new DexFile[0];
                
                try {
                    this.dexFiles = dexFiles = loadDexFiles(files);
                } catch (IOException e) {
                    throw new IOError(e);
                }
            }
        }

        // Iterate over all dex files containing classes.
        for (DexFile dexFile : dexFiles) {
            try {
                byte[] bytecode = DexRewriter.rewrite(name, dexFile);
                if (VERIFY) {
                    Verifier.verify(this, dexFile, name, bytecode, new PrintWriter(System.err));
                }
                return defineClass(name, bytecode, 0, bytecode.length);
            } catch (ClassNotFoundException e) {
                // Ignore and skip to the next file.
            }
        }
        // Unable to find class definition for given class name.
        throw new ClassNotFoundException(name);
    }

    @Override
    protected URL findResource(String name) {
        System.out.printf("Trying to find resource '%s' ...\n", name);
        
        ApkFile[] resourceFiles;
        synchronized(resourcesLock) {
            resourceFiles = this.resourceFiles;
            if (resourceFiles == null) {
                // avoid an infinite loop if findResourceFiles() calls
                // findResource() on the current classloader
                this.resourceFiles = new ApkFile[0];
                try {
                    this.resourceFiles = resourceFiles = findResourceFiles(files);
                } catch (IOException e) {
                    throw new IOError(e);
                }
            }
        }

        // Iterate over all files containing resources.
        for (ApkFile apk : resourceFiles) {
            ZipEntry entry = apk.getEntry(name);
            if (entry != null)
                return apk.getJarURL(entry);
        }

        // Unable to find resource for given resource name.
        return null;
    }

    /**
     * Helper method to get the default set of files to load from.
     */
    private static File[] defaultFiles() {
        String path = System.getProperty("daneel.class.path");
        if (path == null || path.isEmpty())
            return new File[0];
        String[] paths = path.split(File.pathSeparator);
        File[] files = new File[paths.length];
        for (int i = 0; i < paths.length; i++)
            files[i] = new File(paths[i]);
        return files;
    }
    
    /** 
     * Filter an array of files to find all DEX files. 
     * @param files an array of files.
     * @return an array of DEX files.
     * @throws IOException
     */
    private static DexFile[] loadDexFiles(File[] files) throws IOException {
        ArrayList<DexFile> dexs = new ArrayList<DexFile>(files.length);
        for (File file : files) {
            DexFile dexFile;
            if (file.getName().endsWith(".apk")) {
                @SuppressWarnings("resource")
				ApkFile apk = new ApkFile(file);
                dexFile = apk.getDexFile();
            } else {
                dexFile = DexFile.parse(file);
            }
            dexs.add(dexFile);
        }
        return dexs.toArray(new DexFile[dexs.size()]);
    }
    
    /** 
     * Filter an array of files to find all APK files. 
     * @param files an array of files.
     * @return an array of APK files.
     * @throws IOException
     */
    private static ApkFile[] findResourceFiles(File[] files) throws IOException {
        ArrayList<ApkFile> apks = new ArrayList<ApkFile>(files.length);
        for (File file : files) {
            if (file.getName().endsWith(".apk")) {
                ApkFile apk = new ApkFile(file);
                apks.add(apk);
            }
        }
        return apks.toArray(new ApkFile[apks.size()]);
    }
    
    private static final boolean VERIFY;
    static {
        VERIFY = Boolean.getBoolean("daneel.verify");
    }
}
