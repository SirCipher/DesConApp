package com.type2labs.dmm.bluetooth;

import android.content.res.AssetManager;

import com.type2labs.dmm.utils.AssetUtils;

import java.util.List;

public class MockByLineConnector implements DeviceConnector {

    public static final String SAMPLES_SUBDIR = "samples/line-by-line";

    private static final int SLEEP_MILLIS = 1000;

    private final MessageHandler messageHandler;
    private final AssetManager assets;
    private final String filename;

    private boolean running = false;

    public MockByLineConnector(MessageHandler messageHandler, AssetManager assets, String filename) {
        this.messageHandler = messageHandler;
        this.assets = assets;
        this.filename = filename;
    }

    @Override
    public synchronized void connect() {
        if (running) {
            return;
        }
        running = true;
        new Thread(new Runnable() {
            private void loopLinesUntilStopped(List<String> lines) {
                messageHandler.sendConnectedTo(filename);

                while (running) {
                    for (String line : lines) {
                        if (!running) {
                            break;
                        }
                        messageHandler.sendLineRead(line);
                        try {
                            Thread.sleep(SLEEP_MILLIS);
                        } catch (InterruptedException e) {
                            // ok to be interrupted
                        }
                    }
                }
            }

            @Override
            public void run() {
                messageHandler.sendConnectingTo(filename);

                String mockFilePath = SAMPLES_SUBDIR + "/" + filename;
                List<String> lines = AssetUtils.readLinesFromStream(assets, mockFilePath);

                if (!lines.isEmpty()) {
                    loopLinesUntilStopped(lines);
                }

                messageHandler.sendConnectionLost();
            }
        }).start();
    }

    @Override
    public synchronized void disconnect() {
        running = false;
    }

    @Override
    public void sendAsciiMessage(CharSequence chars) {
        // do nothing
    }
}
