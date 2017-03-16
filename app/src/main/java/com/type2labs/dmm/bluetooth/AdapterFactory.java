package com.type2labs.dmm.bluetooth;

import android.bluetooth.BluetoothAdapter;

/**
 * Created by Mudkipz on 16/03/2017.
 */

public class AdapterFactory {
    private AdapterFactory() {
        // utility class
    }

    public static AdapterWrapper getBluetoothAdapterWrapper() {
        BluetoothAdapter defaultAdapter = BluetoothAdapter.getDefaultAdapter();
        return defaultAdapter != null ? new AdapterDelegate(defaultAdapter) : new NullWrapper();
    }
}
