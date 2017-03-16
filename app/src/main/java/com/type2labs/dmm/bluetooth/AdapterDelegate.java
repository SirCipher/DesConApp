package com.type2labs.dmm.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import java.util.Set;

public class AdapterDelegate implements AdapterWrapper {

    private final BluetoothAdapter adapter;

    public AdapterDelegate(BluetoothAdapter adapter) {
        assert adapter != null;
        this.adapter = adapter;
    }

    @Override
    public Set<BluetoothDevice> getBondedDevices() {
        return adapter.getBondedDevices();
    }

    @Override
    public void cancelDiscovery() {
        adapter.cancelDiscovery();
    }

    @Override
    public boolean isDiscovering() {
        return adapter.isDiscovering();
    }

    @Override
    public void startDiscovery() {
        adapter.startDiscovery();
    }
}