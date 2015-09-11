package nl.dobots.fridgefile;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.util.List;

import nl.dobots.bluenet.BleDeviceConnectionState;
import nl.dobots.bluenet.BleTypes;
import nl.dobots.bluenet.callbacks.IDiscoveryCallback;
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
 * Created on 9-9-15
 *
 * @author Bart van Vliet
 */

public class SettingsActivity extends AppCompatActivity {
	private static final String TAG = SettingsActivity.class.getCanonicalName();

	private StoredBleDeviceList _deviceList;
	private Handler _handler;
	BleExt _ble;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_settings);

		_deviceList = FridgeFile.getInstance().getStoredDeviceList();
		_ble = FridgeFile.getInstance().getBle();
		_handler = new Handler();

		if (_fridgeService == null) {
			bindService(new Intent(this, BleFridgeService.class), _fridgeServiceConnection, Context.BIND_AUTO_CREATE);
		}

		initUI();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		Log.d(TAG, "onDestroy");
		if (_fridgeService != null) {
			_fridgeService.removeListener(_bleFridgeListener);
			unbindService(_fridgeServiceConnection);
		}
		// Remove all callbacks and messages that were posted
		_handler.removeCallbacksAndMessages(null);
	}

	private void initUI() {
		final Button applyButton = (Button) findViewById(R.id.applySettingsButton);
		final EditText editMinTemp = (EditText) findViewById(R.id.editMinTemp);
		final EditText editMaxTemp = (EditText) findViewById(R.id.editMaxTemp);
		applyButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				// Check filled in min and max temps
				boolean success = true;
				int minTemp = 0;
				int maxTemp = 0;
				try {
					minTemp = Integer.parseInt(editMinTemp.getText().toString());
					maxTemp = Integer.parseInt(editMaxTemp.getText().toString());
				} catch (NumberFormatException e) {
					success = false;
				}
				if (minTemp < -127 || minTemp > 127 || maxTemp < -127 || maxTemp > 127 || maxTemp <= minTemp) {
					success = false;
				}

				if (success) {
					List<StoredBleDevice> deviceList = FridgeFile.getInstance().getStoredDeviceList().toList();
					_fridgeService.stopSampling();
					setNextDevice(deviceList, 0, minTemp, maxTemp);
				} else {
					Toast toast = Toast.makeText(getApplicationContext(), "Invalid settings", Toast.LENGTH_SHORT);
					toast.show();
				}
			}
		});
	}

	void setNextDevice(final List<StoredBleDevice> deviceList, final int num, final int minTemp, final int maxTemp) {
		Log.d(TAG, "setNextDevice num=" + num + " minTemp=" + minTemp + " maxTemp=" + maxTemp);
		if (num >= deviceList.size()) {
			// Done setting all devices
			Log.d(TAG, "Done setting all devices");
			_fridgeService.startSampling();
			return;
		}

		// Wait for connection state to be ok
		Log.d(TAG, "connection state = " + _ble.getConnectionState());
		boolean retryLater = false;
		switch (_ble.getConnectionState()) {
			case connected:
				_ble.disconnect(null);
				retryLater = true;
				break;
			case connecting:
			case disconnecting:
				retryLater = true;
				break;
		}
		if (retryLater) {
			_handler.postDelayed(new Runnable() {
				@Override
				public void run() {
					setNextDevice(deviceList, num, minTemp, maxTemp);
				}
			}, Config.BLE_WAIT_STATE);
			return;
		}

		final StoredBleDevice device = deviceList.get(num);

		// Connect and discover services
		_ble.connectAndDiscover(device.getAddress(), new IDiscoveryCallback() {
			@Override
			public void onDiscovery(String serviceUuid, String characteristicUuid) {
			}

			@Override
			public void onSuccess() {
				Log.d(TAG, "Successfully connected and discovered");
				if (_ble.hasCharacteristic(BleTypes.CHAR_SET_CONFIGURATION_UUID, null)) {
					// Set min temp
//					_ble.setTxPower(minTemp, new IStatusCallback() {
					_ble.setMinEnvTemp(minTemp, new IStatusCallback() {
						@Override
						public void onSuccess() {
							Log.d(TAG, "Successfully set minTemp");

							_handler.postDelayed(new Runnable() {
								@Override
								public void run() {
									// Set max temp
									_ble.setMaxEnvTemp(maxTemp, new IStatusCallback() {
//									_ble.setTxPower(maxTemp, new IStatusCallback() {
										@Override
										public void onSuccess() {
											Log.d(TAG, "Successfully set maxTemp");
											disconnectAndSetNextDevice(deviceList, num, minTemp, maxTemp);
										}

										@Override
										public void onError(int error) {
											Log.d(TAG, "onError set maxTemp");
											disconnectAndSetNextDevice(deviceList, num, minTemp, maxTemp);
										}
									});
								}
							}, Config.BLE_DELAY_NEXT_CHAR);
						}

						@Override
						public void onError(int error) {
							Log.d(TAG, "onError set minTemp");
							disconnectAndSetNextDevice(deviceList, num, minTemp, maxTemp);
						}
					});
				}
				else {
					Log.d(TAG, "No config set characteristic found");
					disconnectAndSetNextDevice(deviceList, num, minTemp, maxTemp);
				}
			}

			@Override
			public void onError(int error) {
				Log.d(TAG, "onError connect and discover");
				disconnectAndSetNextDevice(deviceList, num, minTemp, maxTemp);
			}
		});
	}

	/** Delayed disconnect, then: delayed call setNextDevice()
	 */
	void disconnectAndSetNextDevice(final List<StoredBleDevice> deviceList, int num, final int minTemp, final int maxTemp) {
		Log.d(TAG, "disconnectAndSetNextDevice num=" + num + " minTemp=" + minTemp + " maxTemp=" + maxTemp);
		final int nextNum = num+1;
		final Runnable delayNext = new Runnable() {
			@Override
			public void run() {
				setNextDevice(deviceList, nextNum, minTemp, maxTemp);
			}
		};

		_handler.postDelayed(new Runnable() {
			@Override
			public void run() {
				// Disconnect
				_ble.disconnect(new IStatusCallback() {
					@Override
					public void onSuccess() {
						_handler.postDelayed(delayNext, Config.BLE_DELAY_CONNECT_NEXT_DEVICE);
					}

					@Override
					public void onError(int error) {
						_handler.postDelayed(delayNext, Config.BLE_DELAY_CONNECT_NEXT_DEVICE);
					}
				});
			}
		}, Config.BLE_DELAY_DISCONNECT);

	}


	//////////////////////////////////////////
	// Communication with the BleFridgeService
	//////////////////////////////////////////
	private BleFridgeService _fridgeService = null;
	private ServiceConnection _fridgeServiceConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			_fridgeService = ((BleFridgeService.BleFridgeBinder)service).getService();
			_fridgeService.addListener(_bleFridgeListener);
		}
		@Override
		public void onServiceDisconnected(ComponentName name) {
			_fridgeService = null;
		}
	};

	final private BleFridgeServiceListener _bleFridgeListener = new BleFridgeServiceListener() {
		@Override
		public void onTemperature(final StoredBleDevice device, final int temperature) {
		}
	};

//	@Override
//	public boolean onCreateOptionsMenu(Menu menu) {
//		// Inflate the menu; this adds items to the action bar if it is present.
//		getMenuInflater().inflate(R.menu.menu_settings, menu);
//		return true;
//	}
//
//	@Override
//	public boolean onOptionsItemSelected(MenuItem item) {
//		// Handle action bar item clicks here. The action bar will
//		// automatically handle clicks on the Home/Up button, so long
//		// as you specify a parent activity in AndroidManifest.xml.
//		int id = item.getItemId();
//
//		//noinspection SimplifiableIfStatement
//		if (id == R.id.action_settings) {
//			return true;
//		}
//
//		return super.onOptionsItemSelected(item);
//	}
}
