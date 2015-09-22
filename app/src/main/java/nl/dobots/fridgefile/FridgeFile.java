package nl.dobots.fridgefile;

import android.app.Application;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import nl.dobots.bluenet.callbacks.IStatusCallback;
import nl.dobots.bluenet.extended.BleExt;

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
 * Created on 7-9-15
 *
 * @author Bart van Vliet
 */
public class FridgeFile extends Application {
	private static final String TAG = FridgeFile.class.getCanonicalName();
	private static FridgeFile _instance;
	private Context _context;
	private Handler _handler;
	private SharedPreferences _preferences;

	public static final String DATABASE_NAME = "FridgeFileDataBase";
	public static final int DATABASE_VERSION = 1;
	public static final String PREFERENCES_FILE = "FridgeFilePreferences";
	public static final int FRIDGE_NOTIFICATION_ID = 1;

	private BleExt _ble;

	private NotificationManager _notificationManager;
	private NotificationCompat.Builder _notificationBuilder;
	private StoredBleDeviceList _storedDeviceList;
	private List<FridgeFileListener> _listenerList;


	private BleFridgeService _fridgeService = null;
	private ServiceConnection _fridgeServiceConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			_fridgeService = ((BleFridgeService.BleFridgeBinder)service).getService();
			_fridgeService.addListener(_fridgeListener);
		}
		@Override
		public void onServiceDisconnected(ComponentName name) {
			_fridgeService = null;
		}
	};

	@Override
	public void onCreate() {
		super.onCreate();
		_instance = this;
		_context = this.getApplicationContext();

		_handler = new Handler();

		_notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

		_preferences = getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE);
//		_phoneNumber = _preferences.getString("phoneNumber", "");

		_storedDeviceList = new StoredBleDeviceList(_context);
		_storedDeviceList.load();
		_listenerList = new ArrayList<>();

		start();
	}

	public static FridgeFile getInstance() {
		return _instance;
	}

	public void start() {
		if (_ble == null) {
			_ble = new BleExt();
			_ble.init(_context, new IStatusCallback() {
				@Override
				public void onSuccess() {

				}

				@Override
				public void onError(int error) {

				}
			});
		}

		if (_fridgeService == null) {
			bindService(new Intent(this, BleFridgeService.class), _fridgeServiceConnection, Context.BIND_AUTO_CREATE);
		}
	}

	/** Stop all services
	 */
	public void stop() {
		_storedDeviceList.save();
		if (_fridgeService != null) {
			_fridgeService.removeListener(_fridgeListener);
			unbindService(_fridgeServiceConnection);
			_fridgeService = null;
		}
		_ble = null;
	}

	public BleExt getBle() {
		return _ble;
	}

	public StoredBleDeviceList getStoredDeviceList() {
		return _storedDeviceList;
	}

	public void setStoredDeviceList(StoredBleDeviceList storedDeviceList) {
		_storedDeviceList = storedDeviceList;
		sendToListeners(storedDeviceList);
	}



	public void addListener(FridgeFileListener listener) {
		_listenerList.add(listener);
	}

	public void removeListener(FridgeFileListener listener) {
		_listenerList.remove(listener);
	}

	public void sendToListeners(StoredBleDeviceList list) {
		for (FridgeFileListener listener : _listenerList) {
			listener.onStoredDeviceList(list);
		}
	}


	//////////////////////////////////////////
	// Communication with the BleFridgeService
	//////////////////////////////////////////
	final BleFridgeServiceListener _fridgeListener = new BleFridgeServiceListener() {
		@Override
		public void onTemperature(StoredBleDevice device, int temperature) {
			StoredBleDevice listedDevice = _storedDeviceList.get(device);
			if (listedDevice != null) {
				Log.d(TAG, "update current temp");
				listedDevice.setCurrentTemperature(temperature);
			}
		}
	};
}
