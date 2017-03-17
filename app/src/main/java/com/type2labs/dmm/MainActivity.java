/*
     Licensed under the Apache License, Version 2.0 (the "License");
     you may not use this file except in compliance with the License.
     You may obtain a copy of the License at

          http://www.apache.org/licenses/LICENSE-2.0

     Unless required by applicable law or agreed to in writing, software
     distributed under the License is distributed on an "AS IS" BASIS,
     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     See the License for the specific language governing permissions and
     limitations under the License.
*/

package com.type2labs.dmm;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.type2labs.dmm.bluetooth.BluetoothDeviceConnector;
import com.type2labs.dmm.bluetooth.DeviceConnector;
import com.type2labs.dmm.bluetooth.ListDevicesActivity;
import com.type2labs.dmm.bluetooth.MessageHandler;
import com.type2labs.dmm.bluetooth.MessageHandlerImpl;
import com.type2labs.dmm.bluetooth.NullConnector;
import com.type2labs.dmm.utils.EmailUtils;


public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final boolean D = true;

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int REQUEST_LAUNCH_EMAIL_APP = 3;
    private static final int MENU_SETTINGS = 4;

    private static final String SAVED_PENDING_REQUEST_ENABLE_BT = "PENDING_REQUEST_ENABLE_BT";
    private final StringBuilder recording = new StringBuilder();
    // Layout Views
    private TextView mStatusView;
    private EditText mOutEditText;
    private View mSendTextContainer;
    // Toolbar
    private ImageButton mToolbarConnectButton;
    private ImageButton mToolbarDisconnectButton;
    private ImageButton mToolbarPauseButton;
    private ImageButton mToolbarPlayButton;
    private ArrayAdapter<String> mConversationArrayAdapter;
    private DeviceConnector mDeviceConnector = new NullConnector();
    // State variables
    private boolean paused = false;
    private boolean connected = false;
    // do not resend request to enable Bluetooth
    // if there is a request already in progress
    // See: https://code.google.com/p/android/issues/detail?id=24931#c1
    private boolean pendingRequestEnableBt = false;
    // controlled by user settings
    private boolean recordingEnabled = false;
    private String defaultEmail;
    private boolean mockDevicesEnabled;
    private String deviceName;
    // The Handler that gets information back from the BluetoothService
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MessageHandler.MSG_CONNECTED:
                    connected = true;
                    mStatusView.setText(formatStatusMessage(R.string.btstatus_connected_to_fmt, msg.obj));
                    onBluetoothStateChanged();
                    recording.setLength(0);
                    deviceName = msg.obj.toString();
                    break;
                case MessageHandler.MSG_CONNECTING:
                    connected = false;
                    mStatusView.setText(formatStatusMessage(R.string.btstatus_connecting_to_fmt, msg.obj));
                    onBluetoothStateChanged();
                    break;
                case MessageHandler.MSG_NOT_CONNECTED:
                    connected = false;
                    mStatusView.setText(R.string.btstatus_not_connected);
                    onBluetoothStateChanged();
                    break;
                case MessageHandler.MSG_CONNECTION_FAILED:
                    connected = false;
                    mStatusView.setText(R.string.btstatus_not_connected);
                    onBluetoothStateChanged();
                    break;
                case MessageHandler.MSG_CONNECTION_LOST:
                    connected = false;
                    mStatusView.setText(R.string.btstatus_not_connected);
                    onBluetoothStateChanged();
                    break;
                case MessageHandler.MSG_BYTES_WRITTEN:
                    String written = new String((byte[]) msg.obj);
                    mConversationArrayAdapter.add(">>> " + written);
                    Log.i(TAG, "written = '" + written + "'");
                    break;
                case MessageHandler.MSG_LINE_READ:
                    if (paused) break;
                    String line = (String) msg.obj;
                    if (D) Log.d(TAG, line);
                    mConversationArrayAdapter.add(line);
                    if (recordingEnabled) {
                        recording.append(line).append("\n");
                    }
                    break;
            }
        }
    };

    private TextView.OnEditorActionListener mWriteListener =
            new TextView.OnEditorActionListener() {
                public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                    if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
                        sendMessage(view.getText());
                    }
                    return true;
                }
            };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "++onCreate");
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            pendingRequestEnableBt = savedInstanceState.getBoolean(SAVED_PENDING_REQUEST_ENABLE_BT);
        }

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

//        Switch recordingSwitch = (Switch) findViewById(R.id.toggle_logging);
//        recordingSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
//            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
//                recordingEnabled = isChecked;
//            }
//        });

        mStatusView = (TextView) findViewById(R.id.btstatus);
        mSendTextContainer = findViewById(R.id.send_text_container);

        mToolbarConnectButton = (ImageButton) findViewById(R.id.toolbar_btn_connect);
        mToolbarConnectButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                startDeviceListActivity();
            }
        });
        mToolbarDisconnectButton = (ImageButton) findViewById(R.id.toolbar_btn_disconnect);
        mToolbarDisconnectButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                disconnectDevices();
            }
        });
        mToolbarPauseButton = (ImageButton) findViewById(R.id.toolbar_btn_pause);
        mToolbarPauseButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                paused = true;
                onPausedStateChanged();
            }
        });

        mToolbarPlayButton = (ImageButton) findViewById(R.id.toolbar_btn_play);
        mToolbarPlayButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                paused = false;
                onPausedStateChanged();
            }
        });

        mConversationArrayAdapter = new ArrayAdapter<String>(this, R.layout.activity_message);
        ListView mConversationView = (ListView) findViewById(R.id.in);
        mConversationView.setAdapter(mConversationArrayAdapter);

        // Initialize the compose field with a listener for the return key
        mOutEditText = (EditText) findViewById(R.id.edit_text_out);
        mOutEditText.setOnEditorActionListener(mWriteListener);

        // Initialize the send button with a listener for click events
        Button mSendButton = (Button) findViewById(R.id.button_send);
        mSendButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                TextView view = (TextView) findViewById(R.id.edit_text_out);
                sendMessage(view.getText());
            }
        });

        onBluetoothStateChanged();
    }

    private void startDeviceListActivity() {
        Intent intent = new Intent(this, ListDevicesActivity.class);
        intent.putExtra(ListDevicesActivity.EXTRA_MOCK_DEVICES_ENABLED, mockDevicesEnabled);
        startActivityForResult(intent, REQUEST_CONNECT_DEVICE);
    }

    private void requestEnableBluetooth() {
        if (!isBluetoothAdapterEnabled() && !pendingRequestEnableBt) {
            pendingRequestEnableBt = true;
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }
    }

    private boolean isBluetoothAdapterEnabled() {
        return getBluetoothAdapter().isEnabled();
    }

    private BluetoothAdapter getBluetoothAdapter() {
        return BluetoothAdapter.getDefaultAdapter();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mDeviceConnector.disconnect();
    }

    private void sendMessage(CharSequence chars) {
        if (chars.length() > 0) {
            mDeviceConnector.sendAsciiMessage(chars);
            mOutEditText.setText("");
        }
    }

    private String formatStatusMessage(int formatResId, Object obj) {
        String deviceName = (String) obj;
        return getString(formatResId, deviceName);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:
                // When DeviceListActivity returns with connection info to connect devices
                // TODO it would be better to return a Parcelable instance of a DeviceConnector,
                //      as in the current approach both the sender and the receiver must know
                //      how to create a DeviceConnector from its pieces (constructor args).
                //      It would be better if that logic was in one place,
                //      and definitely not in this activity
                if (resultCode == Activity.RESULT_OK) {
                    String connectorTypeMsgId = ListDevicesActivity.Message.DeviceConnectorType.toString();
                    ListDevicesActivity.ConnectorType connectorType =
                            (ListDevicesActivity.ConnectorType) data.getSerializableExtra(connectorTypeMsgId);
                    MessageHandler messageHandler = new MessageHandlerImpl(mHandler);
                    String addressMsgId = ListDevicesActivity.Message.BluetoothAddress.toString();
                    String address = data.getStringExtra(addressMsgId);
                    mDeviceConnector = new BluetoothDeviceConnector(messageHandler, address);
                    mDeviceConnector.connect();
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                pendingRequestEnableBt = false;
                if (resultCode != Activity.RESULT_OK) {
                    Log.i(TAG, "BT not enabled");
                }
                break;
            case REQUEST_LAUNCH_EMAIL_APP:
                if (resultCode == Activity.RESULT_OK) {
                    Toast.makeText(this, R.string.msg_email_sent, Toast.LENGTH_LONG).show();
                } else {
                    // TODO resultCode is NEVER ok, even when email successfully sent :(
                    //Toast.makeText(this, R.string.msg_email_not_sent, Toast.LENGTH_LONG).show();
                }
                break;
        }
    }

    private void launchEmailApp(Intent intent) {
        try {
            startActivityForResult(Intent.createChooser(intent, getString(R.string.email_client_chooser)), REQUEST_LAUNCH_EMAIL_APP);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this, getString(R.string.no_email_client), Toast.LENGTH_SHORT).show();
        }
    }

    private void startActivityForResult(Class<?> cls, int requestCode) {
        Intent intent = new Intent(getApplicationContext(), cls);
        startActivityForResult(intent, requestCode);
    }

    private void disconnectDevices() {
        mDeviceConnector.disconnect();
        onBluetoothStateChanged();
    }

    private void onBluetoothStateChanged() {
        if (connected) {
            mToolbarConnectButton.setVisibility(View.GONE);
            mToolbarDisconnectButton.setVisibility(View.VISIBLE);
            mSendTextContainer.setVisibility(View.VISIBLE);
        } else {
            mToolbarConnectButton.setVisibility(View.VISIBLE);
            mToolbarDisconnectButton.setVisibility(View.GONE);
            mSendTextContainer.setVisibility(View.GONE);
        }
        paused = false;
        onPausedStateChanged();
    }

    private void onPausedStateChanged() {
        if (connected) {
            if (paused) {
                mToolbarPlayButton.setVisibility(View.VISIBLE);
                mToolbarPauseButton.setVisibility(View.GONE);
            } else {
                mToolbarPlayButton.setVisibility(View.GONE);
                mToolbarPauseButton.setVisibility(View.VISIBLE);
            }
        } else {
            mToolbarPlayButton.setVisibility(View.GONE);
            mToolbarPauseButton.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        Log.d(TAG, "++onSaveInstanceState");
        super.onSaveInstanceState(outState);
        outState.putBoolean(SAVED_PENDING_REQUEST_ENABLE_BT, pendingRequestEnableBt);
    }


    private SharedPreferences getSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.nav_current:
                sendMessage("Amps");
                break;
            case R.id.nav_resistance:
                sendMessage("Resistance");
                break;
            case R.id.nav_voltage:
                sendMessage("Volts");
                break;
            case R.id.menu_email_recorded_data:
                if (recording.length() > 0) {
                    launchEmailApp(EmailUtils.prepareDeviceRecording(this, defaultEmail, deviceName, recording.toString()));
                } else if (recordingEnabled) {
                    Toast.makeText(this, R.string.msg_nothing_recorded, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, R.string.msg_nothing_recorded_recording_disabled, Toast.LENGTH_LONG).show();
                }
                break;
            default:
                break;
        }

        DrawerLayout drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }
}
