package nl.dobots.fridgefile;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.util.Iterator;
import java.util.List;

import nl.dobots.bluenet.ble.base.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.cfg.BleErrors;
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
 * Created on 9-9-15
 *
 * @author Bart van Vliet
 */

public class SettingsActivity extends AppCompatActivity {
	private static final String TAG = SettingsActivity.class.getCanonicalName();

	private Handler _handler;
	private BleExt _ble;

	private ProgressDialog _progressDialog;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_settings);

		_ble = FridgeFile.getInstance().getBle();
		_handler = new Handler();

		initUI();
		_progressDialog = new ProgressDialog(this);
		_progressDialog.setIndeterminate(true);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		Log.d(TAG, "onDestroy");
		// Remove all callbacks and messages that were posted
		_handler.removeCallbacksAndMessages(null);
	}

	private void initUI() {
		final Button applyButton = (Button) findViewById(R.id.applySettingsButton);
		final EditText editMinTemp = (EditText) findViewById(R.id.editMinTemp);
		editMinTemp.setText(String.valueOf(Config.DEFAULT_MIN_TEMPERATURE));
		final EditText editMaxTemp = (EditText) findViewById(R.id.editMaxTemp);
		editMaxTemp.setText(String.valueOf(Config.DEFAULT_MAX_TEMPERATURE));
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
					_progressDialog.setTitle("Applying settings");
					_progressDialog.setMessage("please wait ...");
					_progressDialog.show();

					List<StoredBleDevice> deviceList = FridgeFile.getInstance().getStoredDeviceList().toList();
					final Iterator<StoredBleDevice> deviceIt = deviceList.iterator();
					if (deviceIt.hasNext()) {
						FridgeFile.getInstance().stopSampling();
						setNextDevice(deviceIt, minTemp, maxTemp);
					} else {
						Toast.makeText(getApplicationContext(), "No devices selected!", Toast.LENGTH_SHORT).show();
					}
				} else {
					Toast toast = Toast.makeText(getApplicationContext(), "Invalid settings", Toast.LENGTH_SHORT);
					toast.show();
				}
			}
		});
	}

	void setNextDevice(final Iterator<StoredBleDevice> deviceIt, final int minTemp, final int maxTemp) {

		if (!deviceIt.hasNext()) {
			// Done setting all devices
			Log.d(TAG, "Done setting all devices");
			FridgeFile.getInstance().startSampling();
			_progressDialog.dismiss();
			return;
		}
		final StoredBleDevice device = deviceIt.next();
		Log.d(TAG, "setNextDevice dev=" + device.getName() + " minTemp=" + minTemp + " maxTemp=" + maxTemp);

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
					setNextDevice(deviceIt, minTemp, maxTemp);
				}
			}, Config.BLE_WAIT_STATE);
			return;
		}

		// Set min temp
		_ble.setMinEnvTemp(device.getAddress(), minTemp, new IStatusCallback() {
			@Override
			public void onSuccess() {
				Log.d(TAG, "Successfully set minTemp");
				device.setMinTemperature(minTemp);

				// Set max temp
				_ble.setMaxEnvTemp(maxTemp, new IStatusCallback() {
					//									_ble.setTxPower(maxTemp, new IStatusCallback() {
					@Override
					public void onSuccess() {
						Log.d(TAG, "Successfully set maxTemp");
						device.setMaxTemperature(maxTemp);
						disconnectAndSetNextDevice(deviceIt, minTemp, maxTemp);
					}

					@Override
					public void onError(int error) {
						if (error == BleErrors.ERROR_CHARACTERISTIC_NOT_FOUND) {
							Log.d(TAG, "No config set characteristic found");
						} else {
							Log.d(TAG, "onError set maxTemp");
						}
						disconnectAndSetNextDevice(deviceIt, minTemp, maxTemp);
					}
				});
			}

			@Override
			public void onError(int error) {
				if (error == BleErrors.ERROR_CHARACTERISTIC_NOT_FOUND) {
					Log.d(TAG, "No config set characteristic found");
				} else {
					Log.d(TAG, "onError set minTemp");
				}
				disconnectAndSetNextDevice(deviceIt, minTemp, maxTemp);
			}
		});
	}

	/** disconnect, then call setNextDevice()
	 */
	void disconnectAndSetNextDevice(final Iterator<StoredBleDevice> deviceIt, final int minTemp, final int maxTemp) {
		Log.d(TAG, "disconnectAndSetNextDevice");

		// Disconnect
		_ble.disconnectAndClose(false, new IStatusCallback() {
			@Override
			public void onSuccess() {
				setNextDevice(deviceIt, minTemp, maxTemp);
			}

			@Override
			public void onError(int error) {
				setNextDevice(deviceIt, minTemp, maxTemp);
			}
		});

	}

}
