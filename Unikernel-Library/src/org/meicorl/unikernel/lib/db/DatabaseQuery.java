package org.meicorl.unikernel.lib.db;

import java.util.ArrayList;
import android.content.Context;
import android.database.Cursor;

/**
 * This class adds multiple entries to the database and pulls them back
 * out.
 * @author Sokol
 */
public class DatabaseQuery {
	// Variables area
	private ArrayList<String> arrayKeys = null;
	private ArrayList<String> arrayValues = null;
	private ArrayList<String> databaseKeys = null;
	private ArrayList<String> databaseKeyOptions = null;
	private DBAdapter database;
	
	/**
	 * Initialize the ArrayList
	 * @param context Pass context from calling class.
	 */
	public DatabaseQuery(Context context) {
		// Create an ArrayList of keys and one of the options/parameters
		// for the keys.
		databaseKeys = new ArrayList<String>();
		databaseKeyOptions = new ArrayList<String>();
		
		databaseKeys.add("methodName");
		databaseKeyOptions.add("text not null");
		
		databaseKeys.add("execLocation");
		databaseKeyOptions.add("text not null");
		
		databaseKeys.add("networkType");
		databaseKeyOptions.add("text");
		
		databaseKeys.add("networkSubType");
		databaseKeyOptions.add("text");
		
		databaseKeys.add("execDuration");
		databaseKeyOptions.add("text");
		
		databaseKeys.add("energyConsumption");
		databaseKeyOptions.add("text");
		
		
		// Call the database adapter to create the database
		database = new DBAdapter(context, "logTable", databaseKeys, databaseKeyOptions);
        database.open();
		arrayKeys = new ArrayList<String>();
		arrayValues = new ArrayList<String>();

	}
	
	/**
	 * Append data to an ArrayList to then submit to the database
	 * @param key Key of the value being appended to the Array.
	 * @param value Value to be appended to Array.
	 */
	public void appendData(String key, String value){
		arrayKeys.add(key);
		arrayValues.add(value);
	}
	
	/**
	 * This method adds the row created by appending data to the database.
	 * The parameters constitute one row of data.
	 */
	public void addRow(){
		database.insertEntry(arrayKeys, arrayValues);
	}
	
	public void updateRow(){
		database.updateEntry(arrayKeys, arrayValues);
	}
	
	/**
	 * Get data from the table.
	 * @param keys List of columns to include in the result.
	 * @param selection Return rows with the following string only. Null returns all rows.
	 * @param selectionArgs Arguments of the selection.
	 * @param groupBy Group results by.
	 * @param having A filter declare which row groups to include in the cursor.
	 * @param sortBy Column to sort elements by.
	 * @param sortOption ASC for ascending, DESC for descending.
	 * @return Returns an ArrayList<String> with the results of the selected field.
	 */
	public ArrayList<String> getData(String[] keys, String selection, String[] 
	  selectionArgs, String groupBy, String having, String sortBy, String sortOption){
		
		ArrayList<String> list = new ArrayList<String>(); 
		Cursor results = database.getAllEntries(keys, selection, 
				selectionArgs, groupBy, having, sortBy, sortOption);
		while(results.moveToNext())
		{
			list.add(results.getString(results.getColumnIndex(sortBy)));
			list.add(results.getString(results.getColumnIndex("energyConsumption")));
		}
		
		try{
			results.close();
		}
		finally {
			// Log.v("PowerDroid-Database", "Cursor closed");
		}
		
		return list;
	}
	
	
	/**
	 * Destroy the reporter.
	 * @throws Throwable
	 */
	public void destroy() throws Throwable{
        database.close();
	}
}
