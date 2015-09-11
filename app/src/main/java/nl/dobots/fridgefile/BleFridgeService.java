package nl.dobots.fridgefile;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import nl.dobots.bluenet.BleDeviceConnectionState;
import nl.dobots.bluenet.callbacks.IIntegerCallback;
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
 * Created on 8-9-15
 *
 * @author Bart van Vliet
 */
public class BleFridgeService extends Service {
	private static final String TAG = BleFridgeService.class.getCanonicalName();
	private BleExt _ble;
	private Handler _handler = new Handler();
	private List<BleFridgeServiceListener> _listenerList = new ArrayList<>();
	private int _checkDeviceNum;

	@Override
	public void onCreate() {
		super.onCreate();
		_ble = FridgeFile.getInstance().getBle();
		startSampling();
		_checkDeviceNum = 0;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.d(TAG, "onDestroy");
		if (_ble != null) {
			_ble.stopScan(new IStatusCallback() {
				@Override
				public void onSuccess() {

				}

				@Override
				public void onError(int error) {

				}
			});
		}

		// Remove all callbacks and messages that were posted
		_handler.removeCallbacksAndMessages(null);
	}

	public void startSampling() {
		_handler.removeCallbacks(stopSamplingRunnable);
		_handler.postDelayed(checkTempRunnable, Config.BLE_DELAY_CONNECT_NEXT_DEVICE);
	}

	public void stopSampling() {
		_handler.removeCallbacks(checkTempRunnable);
		switch (_ble.getConnectionState()) {
			case connected: {
				_ble.disconnect(new IStatusCallback() {
					@Override
					public void onSuccess() {

					}

					@Override
					public void onError(int error) {

					}
				});
				break;
			}
			case connecting: {
//				_handler.postDelayed(stopSamplingRunnable, 500);
				break;
			}
		}
	}

	final Runnable checkTempRunnable = new Runnable() {
		@Override
		public void run() {
			StoredBleDeviceList deviceList = FridgeFile.getInstance().getStoredDeviceList();
			int size = deviceList.size();
			if (size > 0) {
				// Check here, as the list may have changed since last time
				if (_checkDeviceNum >= size) {
					_checkDeviceNum = 0;
				}
				if (_ble.getConnectionState() == BleDeviceConnectionState.initialized) {
					checkTemperature(deviceList.toList().get(_checkDeviceNum));
					_checkDeviceNum++;
				}
			}
			_handler.postDelayed(this, 10000); //TODO: magic nr
		}
	};

	final Runnable stopSamplingRunnable = new Runnable() {
		@Override
		public void run() {
			stopSampling();
		}
	};

	private void checkTemperature(final StoredBleDevice device) {
		Log.d(TAG, "check temperature of " + device.getAddress());
		// Also connects and discovers
		_ble.readTemperature(device.getAddress(), new IIntegerCallback() {
			@Override
			public void onSuccess(int result) {
				Log.d(TAG, "Current temperature of " + device.getAddress() + "(" + device.getName() + ") = " + result);
				sendToListeners(device, result);
			}

			@Override
			public void onError(int error) {
				_ble.disconnect(new IStatusCallback() {
					@Override
					public void onSuccess() {

					}

					@Override
					public void onError(int error) {

					}
				});
			}
		});
	}

	/** Binder given to users that bind to this service */
	public class BleFridgeBinder extends Binder {
		BleFridgeService getService() {
			return BleFridgeService.this;
		}
	}
	private final IBinder _binder = new BleFridgeBinder();

	@Override
	public IBinder onBind(Intent intent) {
		return _binder;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		//The service will at this point continue running until Context.stopService() or stopSelf() is called
		return Service.START_STICKY;
	}


	public void addListener(BleFridgeServiceListener listener) {
		_listenerList.add(listener);
	}

	public void removeListener(BleFridgeServiceListener listener) {
		_listenerList.remove(listener);
	}

	private void sendToListeners(StoredBleDevice device, int temperature) {
		for (BleFridgeServiceListener listener : _listenerList) {
			listener.onTemperature(device, temperature);
		}
	}
}
