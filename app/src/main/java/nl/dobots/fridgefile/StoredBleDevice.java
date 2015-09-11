package nl.dobots.fridgefile;

import android.util.Log;

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

public class StoredBleDevice {
	private static final String TAG = StoredBleDevice.class.getCanonicalName();
	private String _address;
	private String _name;
	private int _minTemperature;
	private int _maxTemperature;
	private int _currentTemperature;


	public StoredBleDevice(String address, String name, int minTemperature, int maxTemperature) {
		_address = address;
		_name = name;
		_minTemperature = minTemperature;
		_maxTemperature = maxTemperature;
		_currentTemperature = Integer.MIN_VALUE;
	}
	public StoredBleDevice(String address, String name) {
		this(address, name, 0, 10); //TODO: magic number
	}
	public StoredBleDevice(String address) {
		this(address, "");
	}

	public String getAddress() {
		return _address;
	}

	public String getName() {
		return _name;
	}

	public int getMinTemperature() {
		return _minTemperature;
	}

	public int getMaxTemperature() {
		return _maxTemperature;
	}

	public int getCurrentTemperature() {
		return _currentTemperature;
	}

	public void setName(String name) {
		_name = name;
	}

	public void setMinTemperature(int minTemperature) {
		_minTemperature = minTemperature;
	}

	public void setMaxTemperature(int maxTemperature) {
		_maxTemperature = maxTemperature;
	}

	public void setCurrentTemperature(int currentTemperature) {
		_currentTemperature = currentTemperature;
	}
}
