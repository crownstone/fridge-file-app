package nl.dobots.fridgefile;

import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import nl.dobots.bluenet.callbacks.IBleDeviceCallback;
import nl.dobots.bluenet.callbacks.IStatusCallback;
import nl.dobots.bluenet.extended.structs.BleDevice;

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

public class DeviceSelectActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {
	protected static final String TAG = DeviceSelectActivity.class.getCanonicalName();

	private ListView _deviceListView;
	private DeviceListAdapter _deviceListAdapter;
	private StoredBleDeviceList _storedDeviceList;
//	private BleDeviceMap _scannedDeviceList;
	private List<BleDevice> _scannedDeviceListCopy;
	private boolean _isScanning;

//	private Handler _handler = new Handler();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_device_select);
		_scannedDeviceListCopy = new ArrayList<>(0);
		_storedDeviceList = FridgeFile.getInstance().getStoredDeviceList();
		FridgeFile.getInstance().addListener(_fridgeFileListener);

		initListView();
		initButtons();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		Log.d(TAG, "onDestroy");
		FridgeFile.getInstance().removeListener(_fridgeFileListener);
		FridgeFile.getInstance().getBle().stopScan(new IStatusCallback() {
			@Override
			public void onSuccess() {

			}

			@Override
			public void onError(int error) {

			}
		});
	}

	//	@Override
//	public boolean onCreateOptionsMenu(Menu menu) {
//		// Inflate the menu; this adds items to the action bar if it is present.
//		getMenuInflater().inflate(R.menu.menu_device_select, menu);
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

	private void initButtons() {
		_isScanning = false;
		final Button doneButton = (Button) findViewById(R.id.scanButton);
		doneButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				if (!_isScanning) {
					_isScanning = true;
					doneButton.setText(R.string.scan_stop);
					FridgeFile.getInstance().getBle().startScan(new IBleDeviceCallback() {
						@Override
						public void onSuccess(BleDevice device) {
							updateDeviceList();
						}

						@Override
						public void onError(int error) {
							Log.d(TAG, "onError startScan");
							_isScanning = false;
							doneButton.setText(R.string.scan_start);
						}
					});
				}
				else {
					_isScanning = false;
					doneButton.setText(R.string.scan_start);
					FridgeFile.getInstance().getBle().stopScan(new IStatusCallback() {
						@Override
						public void onSuccess() {

						}

						@Override
						public void onError(int error) {

						}
					});
				}
			}
		});
	}

	private void initListView() {
		_deviceListView = (ListView) findViewById(R.id.deviceListView);
		_deviceListAdapter = new DeviceListAdapter();

		_deviceListView.setAdapter(_deviceListAdapter);
		// Activate the Click even of the List items
		_deviceListView.setOnItemClickListener(this);

//		// Update the list view every second with newly scanned devices
//		_handler.post(new Runnable() {
//			@Override
//			public void run() {
//				updateListView();
//				_handler.postDelayed(this, 1000);
//			}
//		});
	}

	private void updateDeviceList() {
		_scannedDeviceListCopy = new ArrayList<>(FridgeFile.getInstance().getBle().getDeviceMap().values());
		_deviceListAdapter.notifyDataSetChanged();
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		BleDevice device = _scannedDeviceListCopy.get(position);
		Log.d(TAG, "clicked item " + position + " " + device.getAddress());
		if (!_storedDeviceList.contains(device.getAddress())) {
			view.setBackgroundColor(Config.BACKGROUND_SELECTED_COLOR);
			_storedDeviceList.add(new StoredBleDevice(device.getAddress(), device.getName()));
			FridgeFile.getInstance().setStoredDeviceList(_storedDeviceList);
		}
		else {
			view.setBackgroundColor(Config.BACKGROUND_DEFAULT_COLOR);
			_storedDeviceList.remove(device.getAddress());
			FridgeFile.getInstance().setStoredDeviceList(_storedDeviceList);
		}
	}


	private class DeviceListAdapter extends BaseAdapter {

		public DeviceListAdapter() {
		}

		@Override
		public int getCount() {
			return _scannedDeviceListCopy.size();
		}

		@Override
		public Object getItem(int position) {
			return _scannedDeviceListCopy.get(position);
		}

		@Override
		public long getItemId(int position) {
			// Here we can give each item a certain ID, if we want.
			return 0;
		}

		private class ViewHolder {
			protected TextView deviceNameView;
			protected TextView deviceInfoView;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (convertView == null) {
				//LayoutInflater layoutInflater = LayoutInflater.from(getContext());
				LayoutInflater layoutInflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
				convertView = layoutInflater.inflate(R.layout.device_item, null);

				// ViewHolder prevents calling findViewById too often,
				// now it only gets called on creation of a new convertView
				ViewHolder viewHolder = new ViewHolder();
				viewHolder.deviceNameView = (TextView)convertView.findViewById(R.id.deviceName);
				viewHolder.deviceInfoView = (TextView)convertView.findViewById(R.id.deviceInfo);
				convertView.setTag(viewHolder);
			}

			ViewHolder viewHolder = (ViewHolder)convertView.getTag();
			BleDevice device = (BleDevice)getItem(position);

			if (device != null) {
				viewHolder.deviceNameView.setText(device.getName());
				String infoText = getResources().getString(R.string.address_prefix) + " " + device.getAddress();
				infoText += "\n" + getResources().getString(R.string.rssi_prefix) + " " + device.getRssi();
				viewHolder.deviceInfoView.setText(infoText);
				if (_storedDeviceList.contains(device.getAddress())) {
					convertView.setBackgroundColor(Config.BACKGROUND_SELECTED_COLOR);
				}
				else {
					convertView.setBackgroundColor(Config.BACKGROUND_DEFAULT_COLOR);
				}
			}

			return convertView;
		}
	}

	final FridgeFileListener _fridgeFileListener = new FridgeFileListener() {
		@Override
		public void onStoredDeviceList(StoredBleDeviceList list) {
			_deviceListAdapter.notifyDataSetChanged();
		}
	};
}
