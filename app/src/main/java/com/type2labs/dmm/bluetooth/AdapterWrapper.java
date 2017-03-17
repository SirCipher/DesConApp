package com.type2labs.dmm.bluetooth;

import android.bluetooth.BluetoothDevice;

import java.util.Set;

/**
 * Created by Mudkipz on 16/03/2017.
 */

public interface AdapterWrapper {
    void cancelDiscovery();

    Set<BluetoothDevice> getBondedDevices();

    boolean isDiscovering();

    void startDiscovery();
}
