package com.type2labs.dmm.bluetooth;

public interface MessageHandler {

    int MSG_NOT_CONNECTED = 10;
    int MSG_CONNECTING = 11;
    int MSG_CONNECTED = 12;
    int MSG_CONNECTION_FAILED = 13;
    int MSG_CONNECTION_LOST = 14;
    int MSG_LINE_READ = 21;
    int MSG_BYTES_WRITTEN = 22;

    void sendBytesWritten(byte[] bytes);

    void sendConnectedTo(String deviceName);

    void sendConnectingTo(String deviceName);

    void sendConnectionFailed();

    void sendConnectionLost();

    void sendLineRead(String line);

    void sendNotConnected();

}
