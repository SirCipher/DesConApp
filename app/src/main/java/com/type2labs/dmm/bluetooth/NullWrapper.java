package com.type2labs.dmm.bluetooth;


import android.bluetooth.BluetoothDevice;

import java.util.Collections;
import java.util.Set;

public class NullWrapper implements AdapterWrapper {
    @Override
    public void cancelDiscovery() {
        // nothing to cancel
    }

    @Override
    public Set<BluetoothDevice> getBondedDevices() {
        return Collections.emptySet();
    }

    @Override
    public boolean isDiscovering() {
        return false;
    }

    @Override
    public void startDiscovery() {
        // nothing to discover
    }
}
