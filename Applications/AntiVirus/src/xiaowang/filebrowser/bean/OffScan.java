package xiaowang.filebrowser.bean;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import org.jason.lxcoff.lib.ControlMessages;
import org.jason.lxcoff.lib.ExecutionController;
import org.jason.lxcoff.lib.Remoteable;

import xiaowang.filebrowser.biz.FileHelper;
import xiaowang.filebrowser.biz.FileMD5;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import dalvik.system.DexClassLoader;

public class OffScan extends Remoteable{
	
	transient private static String TAG = "OffScan";
	transient private ExecutionController controller;
	
	transient public static ClassLoader mCurrent;
	transient public static DexClassLoader mCurrentDexLoader = null;
	
	private String DATABASE_PATH = ControlMessages.CONTAINER_APK_DIR  + "dictionary";
	private String DATABASE_FILENAME = "dictionary.db2";
	
	public OffScan(ExecutionController controller){
		this.controller = controller;
	}
	
	public boolean scan(String filename){
		Method toExecute;
		Class<?>[] paramTypes = {String.class};
		Object[] paramValues = {filename};

		boolean result = false;
		try {
			toExecute = this.getClass().getDeclaredMethod("localScan", paramTypes);
			// I need to send file first, so invoke the 4-params-version execute
			result = (Boolean) controller.execute(toExecute, paramValues, this, filename);
		} catch (SecurityException e) {
			// Should never get here
			e.printStackTrace();
			throw e;
		} catch (NoSuchMethodException e) {
			// Should never get here
			e.printStackTrace();
		} catch (Throwable e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return result;
	}
	
	public boolean localScan(String filename){
		boolean foundVirus = false;
		String sql = "select name,signature from virus";
		SQLiteDatabase database;
		database = openDatabase();
		
		Cursor cursor = database.rawQuery(sql, null);
		ArrayList<String> virusName = new ArrayList<String>();
		ArrayList<String> virusPath = new ArrayList<String>();
		while (cursor.moveToNext()) {
			String name = cursor.getString(cursor
					.getColumnIndex("name"));

			String signature = cursor.getString(cursor
					.getColumnIndex("signature"));

			//Log.d("VirusSignature", signature);
			try {
				HashMap<String, String> fileSignature = getFileSignature(new File(filename));//
				Set<String> keys = fileSignature.keySet();
				for (String key : keys) {
					if (fileSignature.get(key).equals(signature)) {
						foundVirus = true;
						virusName.add(name);
						virusPath.add("路径:" + key);
					}
				}
				fileSignature.clear();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		if (cursor != null) {
			cursor.close();
		}
		if (database.isOpen()) {
			database.close();
		}
		
		return foundVirus;
	}
	
	private SQLiteDatabase openDatabase() {
		try {
			// 获得dictionary.db文件的绝对路径
			String databaseFilename = DATABASE_PATH + "/" + DATABASE_FILENAME;
			//String databaseFilename = "/sdcard/dictionary/dictionary.db2";
			// 打开dictionary目录中的dictionary.db文件
			//SQLiteDatabase database = SQLiteDatabase.openOrCreateDatabase(databaseFilename, null);
			SQLiteDatabase database = SQLiteDatabase.openDatabase(databaseFilename, null, SQLiteDatabase.OPEN_READONLY);
			return database;
		} catch (Exception e) {
		}
		return null;
	}
	
	public HashMap<String, String> getFileSignature(File[] files)
			throws IOException {

		HashMap<String, String> fileSignature = new HashMap<String, String>();
		for (int i = 0; i < files.length; i++) {
			File currentFile = files[i];
			//Log.d("FileBrowser", currentFile.getAbsolutePath());
			if (currentFile.isDirectory() && currentFile.listFiles() != null) {
				getFileSignature(currentFile.listFiles());
			} else {
				String fileName = currentFile.getName();
				if (fileName.endsWith(".apk")) {
					FileMD5 md5 = new FileMD5(currentFile.getPath());
					fileSignature.put(currentFile.getPath(), md5.getMd5());
				} else if (fileName.endsWith(".zip")) {
					FileHelper.unZip(currentFile.getPath());
				}
			}
		}
		return fileSignature;
	}
	
	public HashMap<String, String> getFileSignature(File file)
			throws IOException {

		HashMap<String, String> fileSignature = new HashMap<String, String>();
		File currentFile = file;
		//Log.d("FileBrowser", currentFile.getAbsolutePath());
		if (currentFile.isDirectory() && currentFile.listFiles() != null) {
			getFileSignature(currentFile.listFiles());
		} else {
			String fileName = currentFile.getName();
			if (fileName.endsWith(".apk")) {
				FileMD5 md5 = new FileMD5(currentFile.getPath());
				fileSignature.put(currentFile.getPath(), md5.getMd5());
			} else if (fileName.endsWith(".zip")) {
				FileHelper.unZip(currentFile.getPath());
			}
		}
		return fileSignature;
	}
	
	@Override
	public void copyState(Remoteable arg0) {
		// TODO Auto-generated method stub
		
	}

}
