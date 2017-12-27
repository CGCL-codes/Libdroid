package org.meicorl.unikernel.lib.db;
import java.util.ArrayList;

import android.content.ContentValues;
import android.content.Context;
import android.database.*;
import android.database.sqlite.*;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.util.Log;

/**
 * The DBAdapter class enables program integration with a SQLite database.
 * @author Sokol
 */
public class DBAdapter{
	private static final String DATABASE_NAME = "ThinkAir-Log.db";
	private String DATABASE_TABLE;
	private static final int DATABASE_VERSION = 1;
	
	// Index Key column
	public static final String KEY_ID = "_id";
	
	// Name of the column index of each column in DB
	public  ArrayList<String> TABLE_KEYS =  new ArrayList<String>();
	public  ArrayList<String> TABLE_OPTIONS = new ArrayList<String>();
	
	// Create new database
	private String DATABASE_CREATE;
	
	// Variable to hold database instant
	private SQLiteDatabase db;
	
	// Database open/upgrade helper
	private myDBHelper dbHelper;
	
	/**
	 * Open the database if it exists or create it if it doesn't. Additionally checks if the
	 * table exists and creates it if it doesn't.
	 * @param context Context passed by the parent.
	 * @param table Name of the table to operate on.
	 * @param keys Array of Key values in the table.
	 * @param options Array of options for the Key values.
	 */
	@SuppressWarnings("unchecked")
	public DBAdapter(Context context, String table, ArrayList<String> keys, ArrayList<String> options){
		// Start initializing all of the variables
		DATABASE_TABLE = table;
		TABLE_KEYS = (ArrayList<String>)keys.clone();
		TABLE_OPTIONS = options;
		
		String keyString = "";
		for(int i = 0; TABLE_KEYS.size() > i; i++){
			
			// Add commas to the options elements if there is a next value.
			if(i + 1 < TABLE_OPTIONS.size() && TABLE_OPTIONS.get(i) != null){
				TABLE_OPTIONS.set(i, TABLE_OPTIONS.get(i) + ",");
			}else if (i + 1 == TABLE_OPTIONS.size() && TABLE_OPTIONS.get(i) != null) {
				if(i + 1 < TABLE_KEYS.size()){
					TABLE_OPTIONS.set(i, TABLE_OPTIONS.get(i) + ",");
				}else {
					TABLE_KEYS.set(i, TABLE_KEYS.get(i) + "");
				}
			}else if (i + 1 != TABLE_KEYS.size()) {
				TABLE_KEYS.set(i, TABLE_KEYS.get(i) + ",");
			}else {
				TABLE_KEYS.set(i, TABLE_KEYS.get(i) + "");
			}
			
			System.out.println(TABLE_OPTIONS.toString());
			System.out.println(TABLE_KEYS.toString());
			
			if(i + 1 <= TABLE_OPTIONS.size() && TABLE_OPTIONS.get(i) != null)
				keyString = keyString + " " + TABLE_KEYS.get(i) + " " + TABLE_OPTIONS.get(i);
			else if(i + 1 > TABLE_OPTIONS.size() || TABLE_OPTIONS.get(i) == null){
				keyString = keyString + " " + TABLE_KEYS.get(i);
			}
		}
		
		// Create the database creation string.
		DATABASE_CREATE = "CREATE TABLE IF NOT EXISTS " + DATABASE_TABLE + " ("
		+ "_id" + " INTEGER PRIMARY KEY AUTOINCREMENT, " + keyString + ");";
		
		Log.v("PowerDroid-Database", DATABASE_CREATE);
		// Create a new Helper
		dbHelper = new myDBHelper(context, DATABASE_NAME, null, DATABASE_VERSION,
				DATABASE_TABLE, DATABASE_CREATE);
	}
	
	/**
	 * Open the connection to the database.
	 * @return Returns a DBAdapter.
	 * @throws SQLException
	 */
	public DBAdapter open() throws SQLException {
		db = dbHelper.getWritableDatabase();
		return this;
	}
	
	/**
	 * Close the connection to the database.
	 */
	public void close() {
		db.close();
	}
	
	/**
	 * Insert a row into the database.
	 * @param key ArrayList of Keys (column headers).
	 * @param value ArrayList of Key values.
	 * @return Returns the number of the row added.
	 */
	public long insertEntry(ArrayList<String> key, ArrayList<String> value) {
		ContentValues contentValues = new ContentValues();
		for(int i = 0; key.size() > i; i++){
			contentValues.put(key.get(i), value.get(i));
		}
		Log.v("PowerDroid-Database", "Database Add: " + contentValues.toString());
		return db.insert(DATABASE_TABLE, null, contentValues);
	}
	
	/**
	 * Remove a row from the database.
	 * @param rowIndex Number of the row to remove.
	 * @return Returns TRUE if it was deleted, FALSE if failed.
	 */
	public boolean removeEntry(long rowIndex) {
		return db.delete(DATABASE_TABLE, KEY_ID + "=" + rowIndex, null) > 0;
	}
	
	/**
	 * Get all entries in the database sorted by the given value.
	 * @param columns List of columns to include in the result.
	 * @param selection Return rows with the following string only. Null returns all rows.
	 * @param selectionArgs Arguments of the selection.
	 * @param groupBy Group results by.
	 * @param having A filter declare which row groups to include in the cursor.
	 * @param sortBy Column to sort elements by.
	 * @param sortOption ASC for ascending, DESC for descending.
	 * @return Returns a cursor through the results.
	 */
	public Cursor getAllEntries(String[] columns, String selection, String[] selectionArgs,
			String groupBy, String having, String sortBy, String sortOption) {
		return db.query(DATABASE_TABLE, columns, selection, selectionArgs, groupBy,
				having, sortBy + " " + sortOption);
	}
	
	public Cursor getAllEntries(String[] columns, String selection, String[] selectionArgs) {
		return db.query(DATABASE_TABLE, columns, selection, selectionArgs, null, null, null);
	}
	
	/**
	 * Does the SQL UPDATE function on the table with given SQL string
	 * @param sqlQuery an SQL Query starting at SET
	 */
	public void update(String sqlQuery) {
		db.rawQuery("UPDATE " + DATABASE_TABLE + sqlQuery, null);		
	}
	
	/**
	 * Get all entries in the database sorted by the given value.
	 * @param columns List of columns to include in the result.
	 * @param selection Return rows with the following string only. Null returns all rows.
	 * @param selectionArgs Arguments of the selection.
	 * @param groupBy Group results by.
	 * @param having A filter declare which row groups to include in the cursor.
	 * @param sortBy Column to sort elements by.
	 * @param sortOption ASC for ascending, DESC for descending.
	 * @param limit limiting number of records to return
	 * @return Returns a cursor through the results.
	 */
	public Cursor getAllEntries(String[] columns, String selection, String[] selectionArgs,
			String groupBy, String having, String sortBy, String sortOption, String limit) {
		return db.query(DATABASE_TABLE, columns, selection, selectionArgs, groupBy,
				having, sortBy + " " + sortOption, limit);
	}
	
	
	/**
	 * This is a function that should only be used if you know what you're doing.
	 * It is only here to clear the appended test data. This clears out all data within
	 * the table specified when the database connection was opened.
	 * @return Returns TRUE if successful. FALSE if not.
	 */
	public boolean clearTable() {
		return db.delete(DATABASE_TABLE, null, null) > 0;	
	}
	
	/**
	 * Update the selected row of the open table.
	 * @param rowIndex Number of the row to update.
	 * @param key ArrayList of Keys (column headers).
	 * @param value ArrayList of Key values.
	 * @return Returns an integer.
	 */
	public int updateEntry(long rowIndex, ArrayList<String> key, ArrayList<String> value) {
		String where = KEY_ID + "=" + rowIndex;
		ContentValues contentValues = new ContentValues();
		for(int i = 0; key.size() > i; i++){
			contentValues.put(key.get(i), value.get(i));
		}
		return db.update(DATABASE_TABLE, contentValues, where, null);
	}
	
	
	public int updateEntry(ArrayList<String> key, ArrayList<String> value) {
		String where = "methodName = ? AND execLocation = ? AND networkType = ? AND networkSubType = ?";
		String[] whereArgs = new String[] {value.get(0), value.get(1), value.get(2), value.get(3)};
		ContentValues contentValues = new ContentValues();
		for(int i = 0; key.size() > i; i++){
			contentValues.put(key.get(i), value.get(i));
		}
		return db.update(DATABASE_TABLE, contentValues, where, whereArgs);
	}
	
	
	/**
	 * Helper Class for DBAdapter. Does the job of creating the database and checking
	 * if the database needs an upgrade to new version depending on version number specified
	 * by DBAdapter.
	 */
	private static class myDBHelper extends SQLiteOpenHelper {
		private String creationString;
		private String tableName;
		@SuppressWarnings("unused")
		SQLiteDatabase db;
		
		/**
		 * Creates a myDBHelper object.
		 * @param context The context where the access is needed
		 * @param name Name of database file
		 * @param factory A CursorFactory, or null to use default CursorFactory
		 * @param version Database version
		 * @param tableName Name of table within database
		 * @param creationString SQL String used to create the database
		 */
		public myDBHelper(Context context, String name, CursorFactory factory,
				int version, String tableName, String creationString) {			
			super(context, name, factory, version);
			this.creationString = creationString;
			this.tableName = tableName;
		}

		/**
		 * Creates the database table.
		 * @param db The database used by this helper to create the table in
		 */
		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL(creationString);
		}

		/**
		 * This method determines if the database needs to be updated or not.
		 * @param db The database used by this helper
		 * @param oldVersion The old database version
		 * @param newVersion The new database version
		 */
		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			// Log the version upgrade
			Log.w("PowerDroid-Database", "Upgrading from version " + oldVersion +
					" to " + newVersion + ", which will destroy all old data");
			
			db.execSQL("DROP TABLE IF EXISTS " + tableName);
			onCreate(db);
			
		}
		
		/**
		 * Creates tables when the database is opened if the tables need to be created.
		 * @param db The database used by this helper
		 */
		@Override
		public void onOpen(SQLiteDatabase db) {
			db.execSQL(creationString);
		}
		
	}	
}
