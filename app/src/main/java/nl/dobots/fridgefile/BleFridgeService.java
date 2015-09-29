package nl.dobots.fridgefile;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import nl.dobots.bluenet.ble.base.callbacks.IAlertCallback;
import nl.dobots.bluenet.ble.base.callbacks.IIntegerCallback;
import nl.dobots.bluenet.ble.base.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.base.structs.BleAlertState;
import nl.dobots.bluenet.ble.extended.BleDeviceConnectionState;
import nl.dobots.bluenet.ble.extended.BleExt;

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
	private boolean _deviceCheckRunning;

	@Override
	public void onCreate() {
		super.onCreate();
		_ble = FridgeFile.getInstance().getBle();
		startSampling();
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
		Log.d(TAG, "start sampling");
		_handler.removeCallbacks(stopSamplingRunnable);
		_handler.postDelayed(sampleRunnable, Config.BLE_DELAY_CONNECT_NEXT_DEVICE);
	}

	public void stopSampling() {
		Log.d(TAG, "stop sampling");
		while (_deviceCheckRunning) {
			// nada
		}
		_handler.removeCallbacks(sampleRunnable);
		switch (_ble.getConnectionState()) {
			case connected: {
				_ble.disconnectAndClose(false, new IStatusCallback() {
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

	final Runnable sampleRunnable = new Runnable() {
		@Override
		public void run() {
			Log.d(TAG, "starting new sample ...");
			StoredBleDeviceList deviceList = FridgeFile.getInstance().getStoredDeviceList();
			int size = deviceList.size();
			if (size > 0) {
				switch (_ble.getConnectionState()) {
				case initialized: {
					Iterator<StoredBleDevice> deviceIt = deviceList.toList().iterator();
					if (deviceIt.hasNext()) {
						checkDevices(deviceIt);
						return;
					}
				}
				case uninitialized: {
					Log.e(TAG, "Bluetooth is disabled!");
					break;
				}
				default: {
					_ble.disconnectAndClose(false, new IStatusCallback() {
						@Override
						public void onSuccess() {
							checkDeviceDone();

						}

						@Override
						public void onError(int error) {
							checkDeviceDone();
						}
					});
					break;
				}
				}
			}
			_handler.postDelayed(sampleRunnable, Config.SAMPLE_DELAY_MILLIS); //TODO: magic nr
		}
	};

	final Runnable stopSamplingRunnable = new Runnable() {
		@Override
		public void run() {
			stopSampling();
		}
	};

	private void checkDeviceFailed(final Iterator<StoredBleDevice> deviceIt) {
		_ble.disconnectAndClose(false, new IStatusCallback() {
			@Override
			public void onSuccess() {
				Log.d(TAG, "success");
//				checkNextDevice(deviceIt);
			}

			@Override
			public void onError(int error) {
				Log.d(TAG, "Error");
//				checkNextDevice(deviceIt);
			}
		});
		checkNextDevice(deviceIt);
	}

	private void checkDevices(final Iterator<StoredBleDevice> deviceIt) {
		_deviceCheckRunning = true;
		final StoredBleDevice device = deviceIt.next();
		Log.d(TAG, "checking device: " + device.getAddress() + "(" + device.getName() + ")");
		checkTemperature(device, new IStatusCallback() {

			@Override
			public void onError(int error) {
				checkDeviceFailed(deviceIt);
			}

			@Override
			public void onSuccess() {
				checkAlerts(device, new IStatusCallback() {

					@Override
					public void onError(int error) {
						checkDeviceFailed(deviceIt);
					}

					@Override
					public void onSuccess() {
						_ble.disconnectAndClose(false, new IStatusCallback() {
							@Override
							public void onSuccess() {
								checkNextDevice(deviceIt);
							}

							@Override
							public void onError(int error) {
								checkDeviceFailed(deviceIt);
							}
						});
					}
				});
			}
		});
	}

	private void checkNextDevice(final Iterator<StoredBleDevice> deviceIt) {
//		_handler.postDelayed(new Runnable() {
//			@Override
//			public void run() {
				Log.i(TAG, "checking next device");
				if (deviceIt.hasNext()) {
					checkDevices(deviceIt);
				} else {
					checkDeviceDone();
				}
//			}
//		}, 500);
	}

	private void checkAlerts(final StoredBleDevice device, final IStatusCallback callback) {
		Log.d(TAG, "checking alerts ...");
		_ble.readAlert(device.getAddress(), new IAlertCallback() {
			@Override
			public void onSuccess(BleAlertState result) {
				Log.d(TAG, "Current alerts of device " + device.getAddress() + "(" + device.getName() + ") = " + result);
				BleAlertState oldAlertState = device.getCurrentAlert();
				device.setCurrentAlert(result);
				sendAlertsToListeners(device, oldAlertState, result);
				callback.onSuccess();
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	private void checkDeviceDone() {
		Log.d(TAG, "... finished sample");
		_handler.postDelayed(sampleRunnable, 10000); //TODO: magic nr
		_deviceCheckRunning = false;
	}

	private void checkTemperature(final StoredBleDevice device, final IStatusCallback callback) {
		Log.d(TAG, "checking temperature ...");
		_ble.readTemperature(device.getAddress(), new IIntegerCallback() {
			@Override
			public void onSuccess(int result) {
				Log.d(TAG, "Current temperature of " + device.getAddress() + "(" + device.getName() + ") = " + result);
				device.setCurrentTemperature(result);
				sendTemperatureToListeners(device, result);
				callback.onSuccess();
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	private void resetDeviceAlertsDone() {
		startSampling();
	}

	public void resetDeviceAlerts() {
		stopSampling();

		if (_ble.getConnectionState() == BleDeviceConnectionState.initialized) {
			StoredBleDeviceList deviceList = FridgeFile.getInstance().getStoredDeviceList();
			Iterator<StoredBleDevice> deviceIt = deviceList.toList().iterator();
			if (deviceIt.hasNext()) {
				resetAlert(deviceIt);
				return;
			}
		} else {
			_ble.disconnectAndClose(false, new IStatusCallback() {
				@Override
				public void onSuccess() {
					resetDeviceAlertsDone();
				}

				@Override
				public void onError(int error) {
					resetDeviceAlertsDone();
				}
			});
		}

	}

	private void resetAlert(final Iterator<StoredBleDevice> deviceIt) {
		StoredBleDevice device = deviceIt.next();
		Log.d(TAG, "Reset Alert for device " + device.getAddress());
		_ble.resetAlert(device.getAddress(), new IStatusCallback() {
			@Override
			public void onSuccess() {
				_ble.disconnectAndClose(false, new IStatusCallback() {
					@Override
					public void onSuccess() {
						if (deviceIt.hasNext()) {
							resetAlert(deviceIt);
						} else {
							resetDeviceAlertsDone();
						}
					}

					@Override
					public void onError(int error) {
						resetDeviceAlertsDone();
					}
				});
			}

			@Override
			public void onError(int error) {
				resetDeviceAlertsDone();
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

	private void sendTemperatureToListeners(StoredBleDevice device, int temperature) {
		for (BleFridgeServiceListener listener : _listenerList) {
			listener.onTemperature(device, temperature);
		}
	}

	private void sendAlertsToListeners(StoredBleDevice device, BleAlertState oldAlertState, BleAlertState newAlertState) {
		for (BleFridgeServiceListener listener : _listenerList) {
			listener.onAlert(device, oldAlertState, newAlertState);
		}
	}
}
