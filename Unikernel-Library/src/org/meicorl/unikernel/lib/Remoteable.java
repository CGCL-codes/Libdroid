package org.meicorl.unikernel.lib;

import java.io.File;
import java.io.Serializable;
import java.util.LinkedList;

public abstract class Remoteable implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public abstract void copyState(Remoteable state);

	/**
	 * Load all provided shared libraries - used when an exception is thrown on
	 * the server-side, meaning that the necessary libraries have not been
	 * loaded. x86 version of the libraries included in the apk of the remoted
	 * application are then loaded and the operation is re-executed.
	 * 
	 * @param libFiles
	 */
	public void loadLibraries(LinkedList<File> libFiles) {
		for (File libFile : libFiles) {

			System.load(libFile.getAbsolutePath());
		}
	}
}
