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
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
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
 * Created on 7-9-15
 *
 * @author Bart van Vliet
 */

public class MainActivity extends AppCompatActivity
		implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {
	private static final String TAG = MainActivity.class.getCanonicalName();

	private ListView _deviceListView;
	private DeviceListAdapter _deviceListAdapter;
	private StoredBleDeviceList _deviceList;
	private List<StoredBleDevice> _deviceListCopy;
	private Handler _handler;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		FridgeFile.getInstance().start();
		FridgeFile.getInstance().addListener(_fridgeFileListener);

		_deviceList = FridgeFile.getInstance().getStoredDeviceList();
		_deviceListCopy = _deviceList.toList();
		_handler = new Handler();

		if (_fridgeService == null) {
			bindService(new Intent(this, BleFridgeService.class), _fridgeServiceConnection, Context.BIND_AUTO_CREATE);
		}

		initListView();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		Log.d(TAG, "onDestroy");
		if (_fridgeService != null) {
			_fridgeService.removeListener(_bleFridgeListener);
			unbindService(_fridgeServiceConnection);
		}
		FridgeFile.getInstance().removeListener(_fridgeFileListener);
		// Remove all callbacks and messages that were posted
		_handler.removeCallbacksAndMessages(null);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		switch (item.getItemId()) {
			case R.id.action_settings:
				startActivity(new Intent(this, SettingsActivity.class));
				return true;
			case R.id.action_device_select:
				startActivity(new Intent(this, DeviceSelectActivity.class));
				return true;
			case R.id.action_stop_app:
				finish();
				FridgeFile.getInstance().stop();
				return true;
		}

		return super.onOptionsItemSelected(item);
	}



	private void initListView() {
		_deviceListView = (ListView) findViewById(R.id.fridgeListView);
//		_deviceListAdapter = new DeviceListAdapter(this, R.layout.device_item ,ImBusyApp.getInstance().getStoredDeviceList());
		_deviceListAdapter = new DeviceListAdapter();
		Log.d(TAG, "device list:");
		for (StoredBleDevice dev : _deviceListCopy) {
			Log.d(TAG, dev.getAddress());
		}
		_deviceListView.setAdapter(_deviceListAdapter);
		// Activate the Click even of the List items
		_deviceListView.setOnItemClickListener(this);
		_deviceListView.setOnItemLongClickListener(this);
	}

	private void updateDeviceList() {
		_deviceListCopy = _deviceList.toList();
		_deviceListAdapter.notifyDataSetChanged();
	}

	private void updateDevice(StoredBleDevice device, int temperature) {
		Log.d(TAG, "update device");
		_deviceList = FridgeFile.getInstance().getStoredDeviceList();
		StoredBleDevice listedDevice = _deviceList.get(device);
		if (listedDevice != null) {
			Log.d(TAG, "update current temp");
			listedDevice.setCurrentTemperature(temperature);
		}
		updateDeviceList();
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		StoredBleDevice device = _deviceListCopy.get(position);
		Log.d(TAG, "clicked item " + position + " " + device.getAddress());
	}

	@Override
	public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
		Log.d(TAG, "Remove item " + position + " " + _deviceListCopy.get(position).getAddress());
		_deviceList.remove(_deviceListCopy.get(position));
		FridgeFile.getInstance().setStoredDeviceList(_deviceList);
//		updateDeviceList();
		return true;
	}

	private class DeviceListAdapter extends BaseAdapter {

		public DeviceListAdapter() {
		}

		@Override
		public int getCount() {
			return _deviceListCopy.size();
		}

		@Override
		public Object getItem(int position) {
			return _deviceListCopy.get(position);
		}

		@Override
		public long getItemId(int position) {
			// Here we can give each item a certain ID, if we want.
			return 0;
		}

		private class ViewHolder {
			protected TextView deviceNameView;
			protected TextView deviceInfoView;
			protected ImageView statusImageView;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
//			Log.d(TAG, "getView convertView=" + convertView + " position=" + position);
			if (convertView == null) {
				// LayoutInflater class is used to instantiate layout XML file into its corresponding View objects.
				LayoutInflater layoutInflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
				convertView = layoutInflater.inflate(R.layout.fridge_item, null);

				// ViewHolder prevents calling findViewById too often,
				// now it only gets called on creation of a new convertView
				ViewHolder viewHolder = new ViewHolder();
				viewHolder.deviceNameView = (TextView) convertView.findViewById(R.id.fridgeName);
				viewHolder.deviceInfoView = (TextView) convertView.findViewById(R.id.deviceInfo);
				viewHolder.statusImageView = (ImageView) convertView.findViewById(R.id.fridgeStatusImage);
				convertView.setTag(viewHolder);
			}

			final ViewHolder viewHolder = (ViewHolder) convertView.getTag();
			final StoredBleDevice device = (StoredBleDevice)getItem(position);

			if (device != null) {
				viewHolder.deviceNameView.setText(device.getName());
				String info = device.getAddress();
				info += "\nTemperature range: " + device.getMinTemperature() + " - " + device.getMaxTemperature();
				info += "\nLast temperature: ";
				int temperature = device.getCurrentTemperature();
				if (temperature != Integer.MIN_VALUE) {
					info += temperature + "°C";
					DateFormat dateFormat = new SimpleDateFormat("MMM d, HH:mm:ss");
//					String dateStr = dateFormat.format(Calendar.getInstance().getTime());
					String dateStr = dateFormat.format(new Date(device.getLastRefreshTime()));
					info += " at " + dateStr;
				}
				else {
					info += "unknown";
				}
				viewHolder.deviceInfoView.setText(info);
			}

			return convertView;
		}
	}

	//////////////////////////////////////////
	// Communication with FridgeFile
	//////////////////////////////////////////
	final FridgeFileListener _fridgeFileListener = new FridgeFileListener() {
		@Override
		public void onStoredDeviceList(StoredBleDeviceList list) {
			updateDeviceList();
		}
	};

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
			_handler.post(new Runnable() {
				@Override
				public void run() {
					updateDevice(device, temperature);
				}
			});
		}
	};
}
