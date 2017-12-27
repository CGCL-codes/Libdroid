package rs.pedjaapps.Linpack;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;


public class DatabaseHandler extends SQLiteOpenHelper
{

    // All Static variables
    // Database Version
    private static final int DATABASE_VERSION = 8;

    // Database Name
    private static final String DATABASE_NAME = "linpack.db";

    // table names
    private static final String TABLE_RESULTS = "results";

    // Table Columns names


    private static final String[] filds = {"_id", "mflops", "nres", "time", "precision", "date"};

    public DatabaseHandler(Context context)
    {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    // Creating Tables
    @Override
    public void onCreate(SQLiteDatabase db)
    {
        String CREATE_LIST_TABLE = "CREATE TABLE " + TABLE_RESULTS + "("
                + filds[0] + " INTEGER PRIMARY KEY,"
                + filds[1] + " DOUBLE,"
                + filds[2] + " DOUBLE,"
                + filds[3] + " DOUBLE,"
                + filds[4] + " DOUBLE,"
                + filds[5] + " INTEGER"
                +
                ")";

        db.execSQL(CREATE_LIST_TABLE);
    }


    // Upgrading database
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
    {
        // Drop older table if existed
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_RESULTS);

        // Create tables again
        onCreate(db);
    }

    /**
     * All CRUD(Create, Read, Update, Delete) Operations
     */

    public void addResult(Result result)
    {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(filds[1], result.mflops);
        values.put(filds[2], result.nres);
        values.put(filds[3], result.time);
        values.put(filds[4], result.precision);
        values.put(filds[5], result.date.getTime());

        // Inserting Row
        db.insert(TABLE_RESULTS, null, values);
        db.close(); // Closing database connection
    }

    public Result getResult(int id)
    {
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(TABLE_RESULTS, new String[]{filds[0],
                        filds[1],
                        filds[2],
                        filds[3],
                        filds[4],
                        filds[5]


                }, filds[0] + "=?",
                new String[]{String.valueOf(id)}, null, null, null, null);
        if (cursor != null)
            cursor.moveToFirst();

        Result entry = new Result();
        entry.id = cursor.getInt(0);
        entry.mflops = cursor.getDouble(1);
        entry.nres = cursor.getDouble(2);
        entry.time = cursor.getDouble(3);
        entry.precision = cursor.getDouble(4);
        entry.date = new Date(cursor.getLong(5));
        // return list
        db.close();
        cursor.close();
        return entry;
    }

    public Result getResultByDate(Date date)
    {
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(TABLE_RESULTS, new String[]{filds[0],
                        filds[1],
                        filds[2],
                        filds[3],
                        filds[4],
                        filds[5]


                }, filds[5] + "=?",
                new String[]{date.getTime() + ""}, null, null, null, null);
        if (cursor != null)
            cursor.moveToFirst();

        Result entry = new Result();
        entry.id = cursor.getInt(0);
        entry.mflops = cursor.getDouble(1);
        entry.nres = cursor.getDouble(2);
        entry.time = cursor.getDouble(3);
        entry.precision = cursor.getDouble(4);
        entry.date = new Date(cursor.getLong(5));
        // return list
        db.close();
        cursor.close();
        return entry;
    }

    public List<Result> getAllResults()
    {
        List<Result> lists = new ArrayList<Result>();
        // Select All Query

        String selectQuery = "SELECT  * FROM " + TABLE_RESULTS;

        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);

        // looping through all rows and adding to list
        if (cursor.moveToFirst())
        {
            do
            {
                Result list = new Result();
                list.id = Integer.parseInt(cursor.getString(0));
                list.mflops = cursor.getDouble(1);
                list.nres = cursor.getDouble(2);
                list.time = cursor.getDouble(3);
                list.precision = cursor.getDouble(4);
                list.date = new Date(cursor.getLong(5));

                // Adding  to list
                lists.add(list);
            }
            while (cursor.moveToNext());
        }

        // return list
        db.close();
        cursor.close();
        return lists;
    }
}

