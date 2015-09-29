package nl.dobots.fridgefile;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Copyright (c) 2015 Dominik Egger <dominik@dobots.nl>. All rights reserved.
 * <p/>
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3, as
 * published by the Free Software Foundation.
 * <p/>
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 3 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 * <p/>
 * Created on 24-9-15
 *
 * @author Dominik Egger
 */
public class TemperatureDbAdapter {


	///////////////////////////////////////////////////////////////////////////////////////////
	/// Variables
	///////////////////////////////////////////////////////////////////////////////////////////

	private static final String TAG = "TemperatureDbAdapter";

	// database version, defines form of entries. increase if data changes
	public static final int DATABASE_VERSION = 1;
	// filename of the database
	public static final String DATABASE_NAME = "temperature.db";

	// key names of the database fields
	public static final String KEY_DATETIME = "date";
	public static final String KEY_DEVICE = "device";
	public static final String KEY_TEMPERATURE = "temperature";
	public static final String KEY_ROWID = "_id";

	// table name
	public static final String TABLE_NAME = "temperature_log";

	// database helper to manage database creation and version management.
	private DatabaseHelper mDbHelper;

	// database object to read and write database
	private SQLiteDatabase mDb;

	// define query used to create the database
	public static final String DATABASE_CREATE =
			"create table " + TABLE_NAME + " (" +
					KEY_ROWID + " integer primary key autoincrement, " +
					KEY_DATETIME + " integer not null," +
					KEY_DEVICE + " text not null," +
					KEY_TEMPERATURE + " integer" + " )";

	// application context
	private final Context mContext;

	// date formats to simplify entry access
	private SimpleDateFormat sdf_date;
	private SimpleDateFormat sdf_time;

	///////////////////////////////////////////////////////////////////////////////////////////
	/// Code
	///////////////////////////////////////////////////////////////////////////////////////////

	// helper class to manage database creation and version management, see SQLiteOpenHelper
	private static class DatabaseHelper extends SQLiteOpenHelper {

		// default constructor
		DatabaseHelper(Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}

		// called when database should be created
		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL(DATABASE_CREATE);
		}

		// called if version changed and database needs to be upgraded
		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			Log.w(TAG, "Upgrading database from version " + oldVersion + " to " +
					newVersion + ", which will destroy all old data");
			db.execSQL("DROP TABLE IF EXISTS notes");
			onCreate(db);
		}

	}

	// default constructor, assigns context and initializes date formats
	public TemperatureDbAdapter(Context context) {
		mContext = context;

		sdf_date = new SimpleDateFormat("yyyy/MM/dd");
		sdf_time = new SimpleDateFormat("yyyy/MM/dd-HH:mm");
	}

	/**
	 * Open the database. If it cannot be opened, try to create a new
	 * instance of the database. If it cannot be created, throw an exception to
	 * signal the failure
	 *
	 * @return this (self reference, allowing this to be chained in an
	 *         initialization call)
	 * @throws SQLException if the database could be neither opened or created
	 */
	public TemperatureDbAdapter open() throws SQLException {
		mDbHelper = new DatabaseHelper(mContext);
		mDb = mDbHelper.getWritableDatabase();
		return this;
	}

	/**
	 * Close the database
	 */
	public void close() {
		mDbHelper.close();
	}

	/**
	 * Create a new entry using the date and temperature provided. If the entry is
	 * successfully created return the new rowId for that entry, otherwise return
	 * a -1 to indicate failure.
	 *
	 * @param date the date of the entry
	 * @param temperature the temperature for the entry
	 * @return rowId or -1 if failed
	 */
	public long createEntry(String deviceAddress, Date date, int temperature) {
		ContentValues values = new ContentValues();

		values.put(KEY_DATETIME, date.getTime());
		values.put(KEY_DEVICE, deviceAddress);
		values.put(KEY_TEMPERATURE, temperature);

		return mDb.insert(TABLE_NAME, null, values);
	}

	/**
	 * Update existing entry. Return true if entry was updated
	 * successfully
	 *
	 * @param id the row id of the entry to be updated
	 * @param date the date of the entry
	 * @param temperature the temperature for the entry
	 * @return true if updated successfully, false otherwise
	 */
	public boolean updateEntry(long id, Date date, String deviceAddress, int temperature) {
		ContentValues values = new ContentValues();

		values.put(KEY_DATETIME, date.getTime());
		values.put(KEY_DEVICE, deviceAddress);
		values.put(KEY_TEMPERATURE, temperature);

		int num = mDb.update(TABLE_NAME, values, "_id "+"="+ id, null);
		return num == 1;
	}

	/**
	 * Delete the entry with the given rowId
	 *
	 * @param rowId id of note to delete
	 * @return true if deleted, false otherwise
	 */
	public boolean deleteEntry(long rowId) {
		return mDb.delete(TABLE_NAME, KEY_ROWID + "=" + rowId, null) > 0;
	}

	/**
	 * Fetch all entries in the database
	 *
	 * @return cursor to access the entries
	 */
	public Cursor fetchAllEntries() {
		Cursor mCursor = mDb.query(TABLE_NAME, new String[] {KEY_DATETIME, KEY_DEVICE, KEY_TEMPERATURE},
				null, null, null, null, null);
		if (mCursor != null) {
			mCursor.moveToFirst();
		}
		return mCursor;
	}

	/**
	 * Fetch entry defined by row id
	 *
	 * @param rowId the id of the entry which should be returned
	 * @return cursor to access the entry
	 */
	public Cursor fetchEntry(long rowId) {
		Cursor mCursor = mDb.query(TABLE_NAME, new String[] {KEY_DATETIME, KEY_DEVICE, KEY_TEMPERATURE},
				KEY_ROWID + "=" + rowId, null, null, null, null);
		if (mCursor != null) {
			mCursor.moveToFirst();
		}
		return mCursor;
	}

	/**
	 * Fetch entries for given date
	 *
	 * @param date string representation of the date, in the form of yyyy/MM/dd
	 * @return cursor to access the entries
	 * @throws ParseException if a wrong date string is provided
	 */
	public Cursor fetchEntriesForDate(String address, String date) throws ParseException {
		Date parsedDate = sdf_date.parse(date);
		return fetchEntriesForDate(address, parsedDate);
	}

	/**
	 * Fetch entries for given date
	 *
	 * @param address
	 * @param date date object
	 * @return cursor to access the entries
	 */
	public Cursor fetchEntriesForDate(String address, Date date) {

		Date startDate = new Date(date.getYear(), date.getMonth(), date.getDate(), 0, 0);
		Date endDate = new Date(date.getYear(), date.getMonth(), date.getDate(), 23, 59);

		return fetchEntriesForDateRange(address, startDate, endDate);
	}

	/**
	 * Fetch entries for given date range
	 *
	 * @param startDate string representation of start date (inclusive) in the form of yyyy/MM/dd
	 * @param endDate string representation of end date (inclusive) in the form of yyyy/MM/dd
	 * @return cursor to access the entries
	 * @throws ParseException if a wrong date string is provided
	 */
	public Cursor fetchEntriesForDateRange(String address, String startDate, String endDate) throws ParseException {
		Date parsedStartDate = sdf_date.parse(startDate);
		Date parsedEndDate = sdf_date.parse(endDate);
		return fetchEntriesForDateRange(address, parsedStartDate, parsedEndDate);
	}

	/**
	 * Fetch entries for given date range
	 *
	 *
	 * @param address
	 * @param startDate start date (inclusive)
	 * @param endDate end date (inclusive)
	 * @return cursor to access the entries
	 * @throws ParseException if a wrong date string is provided
	 */
	public Cursor fetchEntriesForDateRange(String address, Date startDate, Date endDate) {
		String[] args = new String[] { address, String.valueOf(startDate.getTime()), String.valueOf(endDate.getTime()) };
		Cursor mCursor = mDb.query(TABLE_NAME, new String[] {KEY_DATETIME, KEY_DEVICE, KEY_TEMPERATURE},
				KEY_DEVICE + "=? AND " + KEY_DATETIME + " between ? and ?", args, null, null, null);
		if (mCursor != null) {
			mCursor.moveToFirst();
		}
		return mCursor;
	}

	/** Fetch entries with given time stamp
	 *
	 * @param time string representation for time stamp, in the form of yyyy/MM/dd-HH:mm
	 * @return cursor to access the entries
	 * @throws ParseException if a wrong time string is provided
	 */
	public Cursor fetchEntriesForTime(String address, String time) throws ParseException {
		Date parsedTime = sdf_time.parse(time);
		return fetchEntriesForTime(address, parsedTime);
	}

	/**
	 * Fetch entries for given time stamp
	 *
	 * @param time time stamp object
	 * @return cursor to access the entries
	 */
	public Cursor fetchEntriesForTime(String address, Date time) {
		String[] args = new String[] { address, String.valueOf(time.getTime()) };
		Cursor mCursor = mDb.query(TABLE_NAME, new String[] {KEY_DATETIME, KEY_DEVICE, KEY_TEMPERATURE},
				KEY_DEVICE + "=? AND " + KEY_DATETIME + "=?", null, null, null, null);
		if (mCursor != null) {
			mCursor.moveToFirst();
		}
		return mCursor;
	}

	/**
	 * Fetch entries for given time range
	 *
	 * @param startTime string representation of the start time (inclusive), in the form of yyyy/MM/dd-HH:mm
	 * @param endTime string representation of the end time (inclusive), in the form of yyyy/MM/dd-HH:mm
	 * @return cursor to access the entries
	 * @throws ParseException if a wrong time string is provided
	 */
	public Cursor fetchEntriesForTimeRange(String address, String startTime, String endTime) throws ParseException {
		Date parsedStartTime = sdf_time.parse(startTime);
		Date parsedEndTime = sdf_time.parse(endTime);
		return fetchEntriesForTimeRange(address, parsedStartTime, parsedEndTime);
	}

	/**
	 * Fetch entries for given time range
	 *
	 * @param startTime the start time (inclusive)
	 * @param endTime the end time (inclusive)
	 * @return cursor to access the entries
	 * @throws ParseException if a wrong time string is provided
	 */
	public Cursor fetchEntriesForTimeRange(String address, Date startTime, Date endTime) {
		String[] args = new String[] { address, String.valueOf(startTime.getTime()), String.valueOf(endTime.getTime()) };
		Cursor mCursor = mDb.query(TABLE_NAME, new String[] {KEY_DATETIME, KEY_DEVICE, KEY_TEMPERATURE},
				KEY_DEVICE + "=? AND " + KEY_DATETIME + "between ? and ?", args, null, null, null);
		if (mCursor != null) {
			mCursor.moveToFirst();
		}
		return mCursor;
	}



}
