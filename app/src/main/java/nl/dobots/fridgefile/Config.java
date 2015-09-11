package nl.dobots.fridgefile;

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

public class Config {
	/** Background of selected and nonselected items in a listview */
	public static final int BACKGROUND_DEFAULT_COLOR = 0x00000000;
	public static final int BACKGROUND_SELECTED_COLOR = 0x660000FF;

	/** Time to wait before connecting to new device */
	public static final int BLE_DELAY_CONNECT_NEXT_DEVICE = 1000; // ms
	/** Time to wait before reading/writing next characteristic */
	public static final int BLE_DELAY_NEXT_CHAR = 1000; // ms
	/** Time to wait before disconnecting after reading/writing a characteristic */
	public static final int BLE_DELAY_DISCONNECT = 1000; // ms
	/** Time to wait to check the connection state again (sampling) */
	public static final int BLE_WAIT_STATE = 100; // ms
}
