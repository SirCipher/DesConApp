package com.type2labs.dmm.bluetooth;

public class NullConnector implements DeviceConnector {
    @Override
    public void connect() {
        // do nothing
    }

    @Override
    public void disconnect() {
        // do nothing
    }

    @Override
    public void sendAsciiMessage(CharSequence chars) {
        // do nothing
    }
}
