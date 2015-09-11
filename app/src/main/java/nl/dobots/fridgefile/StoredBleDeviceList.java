package nl.dobots.fridgefile;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Copyright (c) 2015 Bart van Vliet <bart@dobots.nl>. All rights reserved.
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
 * Created on 27-7-15
 *
 * @author Bart van Vliet
 */

public class StoredBleDeviceList extends HashMap<String, StoredBleDevice> {
	private static final String TAG = StoredBleDeviceList.class.getCanonicalName();
	private Context _context;
	private DatabaseHelper _databaseHelper;


	public StoredBleDeviceList(Context context) {
		_context = context;
		_databaseHelper = new DatabaseHelper(_context);
	}

	/**
	 * Adds an item, overwrites existing
	 * Also adds it to the database
	 */
	public void add(StoredBleDevice device) {
		put(device.getAddress(), device);
	}

	/**
	 * Adds an item, overwrites existing
	 * Also adds it to the database
	 */
	@Override
	public StoredBleDevice put(String key, StoredBleDevice value) {
		Log.d(TAG, "Put " + key);
		_databaseHelper.put(value);
		return super.put(key, value);
	}

	/**
	 * Removes an item
	 * Also removes it from the database
	 */
	public void remove(StoredBleDevice device) {
		remove(device.getAddress());
	}

	/**
	 * Removes an item
	 * Also removes it from the database
	 */
	@Override
	public StoredBleDevice remove(Object key) {
		Log.d(TAG, "Remove " + key);
		_databaseHelper.remove(this.get(key));
		return super.remove(key);
	}

	/**
	 * Removes all items
	 * Also clears the database
	 */
	@Override
	public void clear() {
		super.clear();
		_databaseHelper.clear();
	}

	public boolean contains(StoredBleDevice device) {
		return containsKey(device.getAddress());
	}

	public boolean contains(String address) {
		return containsKey(address);
	}

	public StoredBleDevice get(StoredBleDevice device) {
		return super.get(device.getAddress());
	}

	public List<StoredBleDevice> toList() {
		return new ArrayList<StoredBleDevice>(values());
	}

	public void save() {
		_databaseHelper.saveAll(this);
	}

	public void load() {
		_databaseHelper.loadAll(this);
	}

	// From http://www.androidhive.info/2011/11/android-sqlite-database-tutorial/
	private class DatabaseHelper extends SQLiteOpenHelper {
		private static final String TABLE_NAME = "StoredDeviceList";
		private static final String KEY_ADDRESS = "address";
		private static final String KEY_NAME = "name";
		private static final String KEY_MIN_TEMP = "minTemp";
		private static final String KEY_MAX_TEMP = "maxTemp";
		// See: https://www.sqlite.org/datatype3.html
		private static final String DATABASE_CREATE = "create table " + TABLE_NAME + " (" +
				KEY_ADDRESS + " text primary key, " +
				KEY_NAME + " text not null, " +
				KEY_MIN_TEMP + " integer," +
				KEY_MAX_TEMP + " integer" +
				");";

		public DatabaseHelper(Context context) {
			super(context, FridgeFile.DATABASE_NAME, null, FridgeFile.DATABASE_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL(DATABASE_CREATE);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			Log.i(TAG, "Upgrading from v" + oldVersion + " to v" + newVersion);
			// TODO: this isn't the best way..
			Log.w(TAG, "This will destroy all data!");
			db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
			onCreate(db);
		}

		public void saveAll(StoredBleDeviceList devices) {
			SQLiteDatabase database = this.getWritableDatabase();
			ContentValues values = new ContentValues();
//			Iterator it = devices.entrySet().iterator();
//			while (it.hasNext()) {
//				Map.Entry pair = (Map.Entry)it.next();
//				deviceToValue(pair.getValue(), values);
//			}
			for (StoredBleDevice device : devices.values()) {
				deviceToValue(device, values);
				// Replace inserts or replaces when the key already exists
				database.replace(TABLE_NAME, null, values);
				Log.d(TAG, "replace " + values);
			}
			database.close();
		}

		public void put(StoredBleDevice device) {
			SQLiteDatabase database = this.getWritableDatabase();
			ContentValues values = new ContentValues();
			deviceToValue(device, values);
			database.replace(TABLE_NAME, null, values);
			database.close();
		}

		public void deviceToValue(StoredBleDevice device, ContentValues values) {
			values.put(KEY_ADDRESS, device.getAddress());
			values.put(KEY_NAME, device.getName());
			values.put(KEY_MIN_TEMP, device.getMinTemperature());
			values.put(KEY_MAX_TEMP, device.getMaxTemperature());
		}

		public void remove(StoredBleDevice device) {
			SQLiteDatabase database = this.getWritableDatabase();
			database.delete(TABLE_NAME, KEY_ADDRESS + " = ?", new String[] { device.getAddress() });
			database.close();
		}

		public void clear() {
			SQLiteDatabase database = this.getWritableDatabase();
			database.delete(TABLE_NAME, null, null);
			database.close();
		}

		/**
		 * Loads devices from database and _adds_ them to the list
		 */
		public void loadAll(StoredBleDeviceList devices) {
			Log.d(TAG, "load from database");
			String selectQuery = "SELECT * FROM " + TABLE_NAME;
			SQLiteDatabase database = this.getWritableDatabase();
			Cursor cursor = database.rawQuery(selectQuery, null);

			if (cursor.moveToFirst()) {
				do {
					StoredBleDevice device = new StoredBleDevice(
							cursor.getString(0),
							cursor.getString(1),
							cursor.getInt(2),
							cursor.getInt(3)
					);
					Log.d(TAG, "Loaded " +device.getName() + " (" + device.getAddress() + ") minTemp=" + device.getMinTemperature() + " maxTemp=" + device.getMaxTemperature());
					devices.add(device);
				} while (cursor.moveToNext());
			}
			Log.d(TAG, "Loaded " + cursor.getCount() + " items");
		}
	}
}
