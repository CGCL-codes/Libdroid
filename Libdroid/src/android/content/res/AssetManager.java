/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.content.res;

import android.util.Log;
import android.util.TypedValue;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

public final class AssetManager {
    /* modes used when opening an asset */

    /**
     * Mode for {@link #open(String, int)}: no specific information about how
     * data will be accessed.
     */
    public static final int ACCESS_UNKNOWN = 0;
    /**
     * Mode for {@link #open(String, int)}: Read chunks, and seek forward and
     * backward.
     */
    public static final int ACCESS_RANDOM = 1;
    /**
     * Mode for {@link #open(String, int)}: Read sequentially, with an
     * occasional forward seek.
     */
    public static final int ACCESS_STREAMING = 2;
    /**
     * Mode for {@link #open(String, int)}: Attempt to load contents into
     * memory, for fast small reads.
     */
    public static final int ACCESS_BUFFER = 3;

    private static final String TAG = "AssetManager";
    private static final boolean localLOGV = false || false;
    
    private static final boolean DEBUG_REFS = false;
    
    private static final Object sSync = new Object();
    /*package*/ static AssetManager sSystem = null;

    private final TypedValue mValue = new TypedValue();
    private final long[] mOffsets = new long[2];
    
    // For communication with native code.
    private int mObject;
    private int mNObject;  // used by the NDK

    
    private int mNumRefs = 1;
    private boolean mOpen = true;
    private HashMap<Integer, RuntimeException> mRefStacks; 


    
    private AssetManager(boolean isSystem) {
        if (DEBUG_REFS) {
            synchronized (this) {
                mNumRefs = 0;
                incRefsLocked(this.hashCode());
            }
        }
        init();
        if (localLOGV) Log.v(TAG, "New asset manager: " + this);
    }
    /**
     * Close this asset manager.
     */
    public void close() {
        synchronized(this) {
            //System.out.println("Release: num=" + mNumRefs
            //                   + ", released=" + mReleased);
            if (mOpen) {
                mOpen = false;
                decRefsLocked(this.hashCode());
            }
        }
    }

    /**
     * Retrieve the string array associated with a particular resource
     * identifier.
     * @param id Resource id of the string array
     */
    /*package*/ final String[] getResourceStringArray(final int id) {
        String[] retArray = getArrayStringResource(id);
        return retArray;
    }

    /**
     * Open an asset using ACCESS_STREAMING mode.  This provides access to
     * files that have been bundled with an application as assets -- that is,
     * files placed in to the "assets" directory.
     * 
     * @param fileName The name of the asset to open.  This name can be
     *                 hierarchical.
     * 
     * @see #open(String, int)
     * @see #list
     */
    public final InputStream open(String fileName) throws IOException {
        return open(fileName, ACCESS_STREAMING);
    }

    /**
     * Open an asset using an explicit access mode, returning an InputStream to
     * read its contents.  This provides access to files that have been bundled
     * with an application as assets -- that is, files placed in to the
     * "assets" directory.
     * 
     * @param fileName The name of the asset to open.  This name can be
     *                 hierarchical.
     * @param accessMode Desired access mode for retrieving the data.
     * 
     * @see #ACCESS_UNKNOWN
     * @see #ACCESS_STREAMING
     * @see #ACCESS_RANDOM
     * @see #ACCESS_BUFFER
     * @see #open(String)
     * @see #list
     */
    public final InputStream open(String fileName, int accessMode)
        throws IOException {
        synchronized (this) {
            if (!mOpen) {
                throw new RuntimeException("Assetmanager has been closed");
            }
            int asset = openAsset(fileName, accessMode);
            if (asset != 0) {
                AssetInputStream res = new AssetInputStream(asset);
                incRefsLocked(res.hashCode());
                return res;
            }
        }
        throw new FileNotFoundException("Asset file: " + fileName);
    }



    /**
     * Return a String array of all the assets at the given path.
     * 
     * @param path A relative path within the assets, i.e., "docs/home.html".
     * 
     * @return String[] Array of strings, one for each asset.  These file
     *         names are relative to 'path'.  You can open the file by
     *         concatenating 'path' and a name in the returned string (via
     *         File) and passing that to open().
     * 
     * @see #open
     */
    public native final String[] list(String path)
        throws IOException;

    /**
     * {@hide}
     * Open a non-asset file as an asset using ACCESS_STREAMING mode.  This
     * provides direct access to all of the files included in an application
     * package (not only its assets).  Applications should not normally use
     * this.
     * 
     * @see #open(String)
     */
    public final InputStream openNonAsset(String fileName) throws IOException {
        return openNonAsset(0, fileName, ACCESS_STREAMING);
    }

    /**
     * {@hide}
     * Open a non-asset file as an asset using a specific access mode.  This
     * provides direct access to all of the files included in an application
     * package (not only its assets).  Applications should not normally use
     * this.
     * 
     * @see #open(String, int)
     */
    public final InputStream openNonAsset(String fileName, int accessMode)
        throws IOException {
        return openNonAsset(0, fileName, accessMode);
    }

    /**
     * {@hide}
     * Open a non-asset in a specified package.  Not for use by applications.
     * 
     * @param cookie Identifier of the package to be opened.
     * @param fileName Name of the asset to retrieve.
     */
    public final InputStream openNonAsset(int cookie, String fileName)
        throws IOException {
        return openNonAsset(cookie, fileName, ACCESS_STREAMING);
    }

    /**
     * {@hide}
     * Open a non-asset in a specified package.  Not for use by applications.
     *
     * @param cookie Identifier of the package to be opened.
     * @param fileName Name of the asset to retrieve.
     * @param accessMode Desired access mode for retrieving the data.
     */
    public final InputStream openNonAsset(int cookie, String fileName, int accessMode)
        throws IOException {
        synchronized (this) {
            if (!mOpen) {
                throw new RuntimeException("Assetmanager has been closed");
            }
            int asset = openNonAssetNative(cookie, fileName, accessMode);
            if (asset != 0) {
                AssetInputStream res = new AssetInputStream(asset);
                incRefsLocked(res.hashCode());
                return res;
            }
        }
        throw new FileNotFoundException("Asset absolute file: " + fileName);
    }


    /*package*/ void xmlBlockGone(int id) {
        synchronized (this) {
            decRefsLocked(id);
        }
    }

    /*package*/ final int createTheme() {
        synchronized (this) {
            if (!mOpen) {
                throw new RuntimeException("Assetmanager has been closed");
            }
            int res = newTheme();
            incRefsLocked(res);
            return res;
        }
    }

    /*package*/ final void releaseTheme(int theme) {
        synchronized (this) {
            deleteTheme(theme);
            decRefsLocked(theme);
        }
    }

    protected void finalize() throws Throwable {
        try {
            if (DEBUG_REFS && mNumRefs != 0) {
                Log.w(TAG, "AssetManager " + this
                        + " finalized with non-zero refs: " + mNumRefs);
                if (mRefStacks != null) {
                    for (RuntimeException e : mRefStacks.values()) {
                        Log.w(TAG, "Reference from here", e);
                    }
                }
            }
            destroy();
        } finally {
            super.finalize();
        }
    }
    
    public final class AssetInputStream extends InputStream {
        public final int getAssetInt() {
            return mAsset;
        }
        private AssetInputStream(int asset)
        {
            mAsset = asset;
            mLength = getAssetLength(asset);
        }
        public final int read() throws IOException {
            return readAssetChar(mAsset);
        }
        public final boolean markSupported() {
            return true;
        }
        public final int available() throws IOException {
            long len = getAssetRemainingLength(mAsset);
            return len > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)len;
        }
        public final void close() throws IOException {
            synchronized (AssetManager.this) {
                if (mAsset != 0) {
                    destroyAsset(mAsset);
                    mAsset = 0;
                    decRefsLocked(hashCode());
                }
            }
        }
        public final void mark(int readlimit) {
            mMarkPos = seekAsset(mAsset, 0, 0);
        }
        public final void reset() throws IOException {
            seekAsset(mAsset, mMarkPos, -1);
        }
        public final int read(byte[] b) throws IOException {
            return readAsset(mAsset, b, 0, b.length);
        }
        public final int read(byte[] b, int off, int len) throws IOException {
            return readAsset(mAsset, b, off, len);
        }
        public final long skip(long n) throws IOException {
            long pos = seekAsset(mAsset, 0, 0);
            if ((pos+n) > mLength) {
                n = mLength-pos;
            }
            if (n > 0) {
                seekAsset(mAsset, n, 0);
            }
            return n;
        }

        protected void finalize() throws Throwable
        {
            close();
        }

        private int mAsset;
        private long mLength;
        private long mMarkPos;
    }

    /**
     * Add an additional set of assets to the asset manager.  This can be
     * either a directory or ZIP file.  Not for use by applications.  Returns
     * the cookie of the added asset, or 0 on failure.
     * {@hide}
     */
    public final int addAssetPath(String path) {
        int res = addAssetPathNative(path);
        return res;
    }

    private native final int addAssetPathNative(String path);

    /**
     * Add multiple sets of assets to the asset manager at once.  See
     * {@link #addAssetPath(String)} for more information.  Returns array of
     * cookies for each added asset with 0 indicating failure, or null if
     * the input array of paths is null.
     * {@hide}
     */
    public final int[] addAssetPaths(String[] paths) {
        if (paths == null) {
            return null;
        }

        int[] cookies = new int[paths.length];
        for (int i = 0; i < paths.length; i++) {
            cookies[i] = addAssetPath(paths[i]);
        }

        return cookies;
    }

    /**
     * Determine whether the state in this asset manager is up-to-date with
     * the files on the filesystem.  If false is returned, you need to
     * instantiate a new AssetManager class to see the new data.
     * {@hide}
     */
    public native final boolean isUpToDate();

    /**
     * Change the locale being used by this asset manager.  Not for use by
     * applications.
     * {@hide}
     */
    public native final void setLocale(String locale);

    /**
     * Get the locales that this asset manager contains data for.
     */
    public native final String[] getLocales();

    /**
     * Change the configuation used when retrieving resources.  Not for use by
     * applications.
     * {@hide}
     */
    public native final void setConfiguration(int mcc, int mnc, String locale,
            int orientation, int touchscreen, int density, int keyboard,
            int keyboardHidden, int navigation, int screenWidth, int screenHeight,
            int smallestScreenWidthDp, int screenWidthDp, int screenHeightDp,
            int screenLayout, int uiMode, int majorVersion);

    /**
     * Retrieve the resource identifier for the given resource name.
     */
    /*package*/ native final int getResourceIdentifier(String type,
                                                       String name,
                                                       String defPackage);

    /*package*/ native final String getResourceName(int resid);
    /*package*/ native final String getResourcePackageName(int resid);
    /*package*/ native final String getResourceTypeName(int resid);
    /*package*/ native final String getResourceEntryName(int resid);
    
    private native final int openAsset(String fileName, int accessMode);

    private native final int openNonAssetNative(int cookie, String fileName,
            int accessMode);

    private native final void destroyAsset(int asset);
    private native final int readAssetChar(int asset);
    private native final int readAsset(int asset, byte[] b, int off, int len);
    private native final long seekAsset(int asset, long offset, int whence);
    private native final long getAssetLength(int asset);
    private native final long getAssetRemainingLength(int asset);

    /** Returns true if the resource was found, filling in mRetStringBlock and
     *  mRetData. */
    private native final int loadResourceValue(int ident, short density, TypedValue outValue,
            boolean resolve);
    /** Returns true if the resource was found, filling in mRetStringBlock and
     *  mRetData. */
    private native final int loadResourceBagValue(int ident, int bagEntryId, TypedValue outValue,
                                               boolean resolve);
    /*package*/ static final int STYLE_NUM_ENTRIES = 6;
    /*package*/ static final int STYLE_TYPE = 0;
    /*package*/ static final int STYLE_DATA = 1;
    /*package*/ static final int STYLE_ASSET_COOKIE = 2;
    /*package*/ static final int STYLE_RESOURCE_ID = 3;
    /*package*/ static final int STYLE_CHANGING_CONFIGURATIONS = 4;
    /*package*/ static final int STYLE_DENSITY = 5;
    /*package*/ native static final boolean applyStyle(int theme,
            int defStyleAttr, int defStyleRes, int xmlParser,
            int[] inAttrs, int[] outValues, int[] outIndices);
    /*package*/ native final boolean retrieveAttributes(
            int xmlParser, int[] inAttrs, int[] outValues, int[] outIndices);
    /*package*/ native final int getArraySize(int resource);
    /*package*/ native final int retrieveArray(int resource, int[] outValues);
    private native final int getStringBlockCount();
    private native final int getNativeStringBlock(int block);

    /**
     * {@hide}
     */
    public native final String getCookieName(int cookie);

    /**
     * {@hide}
     */
    public native static final int getGlobalAssetCount();
    
    /**
     * {@hide}
     */
    public native static final String getAssetAllocations();
    
    /**
     * {@hide}
     */
    public native static final int getGlobalAssetManagerCount();
    
    private native final int newTheme();
    private native final void deleteTheme(int theme);
    /*package*/ native static final void applyThemeStyle(int theme, int styleRes, boolean force);
    /*package*/ native static final void copyTheme(int dest, int source);
    /*package*/ native static final int loadThemeAttributeValue(int theme, int ident,
                                                                TypedValue outValue,
                                                                boolean resolve);
    /*package*/ native static final void dumpTheme(int theme, int priority, String tag, String prefix);

    private native final int openXmlAssetNative(int cookie, String fileName);

    private native final String[] getArrayStringResource(int arrayRes);
    private native final int[] getArrayStringInfo(int arrayRes);
    /*package*/ native final int[] getArrayIntResource(int arrayRes);

    private native final void init();
    private native final void destroy();

    private final void incRefsLocked(int id) {
        if (DEBUG_REFS) {
            if (mRefStacks == null) {
                mRefStacks = new HashMap<Integer, RuntimeException>();
                RuntimeException ex = new RuntimeException();
                ex.fillInStackTrace();
                mRefStacks.put(this.hashCode(), ex);
            }
        }
        mNumRefs++;
    }
    
    private final void decRefsLocked(int id) {
        if (DEBUG_REFS && mRefStacks != null) {
            mRefStacks.remove(id);
        }
        mNumRefs--;
        //System.out.println("Dec streams: mNumRefs=" + mNumRefs
        //                   + " mReleased=" + mReleased);
        if (mNumRefs == 0) {
            destroy();
        }
    }
}
