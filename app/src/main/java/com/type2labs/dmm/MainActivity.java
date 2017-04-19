package com.type2labs.dmm;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.type2labs.dmm.bluetooth.BluetoothDeviceConnector;
import com.type2labs.dmm.bluetooth.DeviceConnector;
import com.type2labs.dmm.bluetooth.ListDevicesActivity;
import com.type2labs.dmm.bluetooth.MessageHandler;
import com.type2labs.dmm.bluetooth.MessageHandlerImpl;
import com.type2labs.dmm.bluetooth.NullConnector;
import com.type2labs.dmm.utils.EmailUtils;
import com.type2labs.dmm.utils.ValueUtils;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener, SharedPreferences.OnSharedPreferenceChangeListener {

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
    private boolean pendingRequestEnableBt = false;

    // controlled by user settings
    private boolean recordingEnabled = false;
    private boolean mockDevicesEnabled;
    private String deviceName;

    // Graphing
    private GraphView graph;
    private boolean graphEnabled = false;
    private LineGraphSeries<DataPoint> series;
    private long startTime = SystemClock.currentThreadTimeMillis();
    // The Handler that gets information back from the BluetoothService
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MessageHandler.MSG_CONNECTED:
                    connected = true;
                    onBluetoothStateChanged();
                    recording.setLength(0);
                    deviceName = msg.obj.toString();
                    break;
                case MessageHandler.MSG_CONNECTING:
                    connected = false;
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
                    readBTData(msg);
                    break;
            }
        }
    };
    private long currentTime = 0;
    private double timePerDiv = 500;
    private double voltsPerDiv = 500;
    private DrawerLayout drawerLayout;
    private TextView.OnEditorActionListener mWriteListener =
            new TextView.OnEditorActionListener() {
                public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                    if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
                        sendMessage(view.getText());
                    }
                    return true;
                }
            };
    private boolean backgroundEnabled = false;
    private Handler handler = new Handler();
    private String graphXlabel;
    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            long millis = System.currentTimeMillis() - startTime;
            int seconds = (int) millis / 1000;
            int minutes = seconds / 60;

            handler.postDelayed(this, 500);
            graphXlabel = String.format("%d:%02d", minutes, seconds);
        }
    };

    private void readBTData(Message msg) {
        if (paused) {
            return;
        }

        String line = (String) msg.obj;

        if (D) {
            Log.d(TAG, line);
        }

        mConversationArrayAdapter.add(ValueUtils.getValue(line) + " " + ValueUtils.getUnits(line));

        if (recordingEnabled) {
            recording.append(line).append("\n");
        }

        // TODO: Tidy this up. Move to the Graph class.
        if (ValueUtils.isValue(line)) {
            try {
                Double data = Double.parseDouble(ValueUtils.getValue(line));

                Log.d("Graph: Adding: ", Double.toString(data));

                series.appendData(new DataPoint((int) (SystemClock.currentThreadTimeMillis() - startTime), data), true, 5000);
//                series.appendData(new DataPoint(graphXlabel, data), true, 500);
            } catch (NumberFormatException e) {
                // Die quietly
            }
        }
    }

    private void initGraph() {
        series = new LineGraphSeries<>();
        series.setBackgroundColor(Color.parseColor("#BCD2EE"));
        series.setDrawBackground(true);
        series.setThickness(8);

        graph = (GraphView) findViewById(R.id.graph);
        graph.getGridLabelRenderer().setLabelVerticalWidth(130);
        graph.addSeries(series);
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(1000);
        graph.getViewport().setMinY(10);
    }

    private void initUI() {
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
        mConversationArrayAdapter = new ArrayAdapter<>(this, R.layout.activity_message);
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
    }

    private void initNav() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        Menu menuNav = navigationView.getMenu();
        Switch toggleEnabled = (Switch) menuNav.findItem(R.id.toggle_logging).getActionView().findViewById(R.id.toggle_switch_item);
        toggleEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                recordingEnabled = isChecked;
                drawerLayout.closeDrawer(GravityCompat.START);
                Log.d("MainActivity", "Recording: " + recordingEnabled);
            }
        });
        Switch graphEnabledSwitch = (Switch) menuNav.findItem(R.id.toggle_graph).getActionView().findViewById(R.id.toggle_switch_item);
        graphEnabledSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                graphEnabled = isChecked;
                graph.setVisibility(graphEnabled ? View.VISIBLE : View.GONE);
                drawerLayout.closeDrawer(GravityCompat.START);
                Log.d("MainActivity", "Graphing: " + graphEnabled);
            }
        });
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

    private void launchEmailApp(Intent intent) {
        try {
            startActivityForResult(Intent.createChooser(intent, getString(R.string.email_client_chooser)), REQUEST_LAUNCH_EMAIL_APP);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this, getString(R.string.no_email_client), Toast.LENGTH_SHORT).show();
        }
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
        requestEnableBluetooth();

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

    private SharedPreferences getSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(this);
    }

    private void startActivityForResult(Class<?> cls, int requestCode) {
        Intent intent = new Intent(getApplicationContext(), cls);
        startActivityForResult(intent, requestCode);
    }

    private double checkParam(String toCheck, String key) {
        if (toCheck.equals("")) {
            toCheck = "500";
            SharedPreferences.Editor editor = getSharedPreferences().edit();
            editor.putFloat(key, 500);
            editor.apply();
        }
        return Double.parseDouble(toCheck);
    }

    private void updateParamsFromSettings() {
        graphEnabled = getSharedPreferences().getBoolean(getString(R.string.setting_background), false);

        String time, volts;
        try {
            time = getSharedPreferences().getString(getString(R.string.setting_time_div), "500");
        } catch (ClassCastException e) {
            time = "500";
        }
        try {
            volts = getSharedPreferences().getString(getString(R.string.setting_volts_div), "500");
        } catch (ClassCastException e) {
            volts = "500";
        }

        timePerDiv = checkParam(time, "setting_time_div");
        voltsPerDiv = checkParam(volts, "setting_volts_div");

        graph.setVisibility(graphEnabled ? View.VISIBLE : View.GONE);
    }

    private void registerOnSharedPreferenceChangeListener() {
        SharedPreferences.OnSharedPreferenceChangeListener listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                updateParamsFromSettings();
                Log.d(TAG, "++onSharedPreferenceChanged");
                Log.d(TAG, "" + backgroundEnabled + " " + timePerDiv + " " + voltsPerDiv);
            }
        };
        getSharedPreferences().registerOnSharedPreferenceChangeListener(listener);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:

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

    @Override
    protected void onPause() {
        super.onPause();
        getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "++onCreate");
        super.onCreate(savedInstanceState);
        registerOnSharedPreferenceChangeListener();

        // Disable landscape
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        if (savedInstanceState != null) {
            pendingRequestEnableBt = savedInstanceState.getBoolean(SAVED_PENDING_REQUEST_ENABLE_BT);
        }

        setContentView(R.layout.activity_main);

        initNav();
        initUI();
        initGraph();
        onBluetoothStateChanged();
        updateParamsFromSettings();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mDeviceConnector.disconnect();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        Log.d(TAG, "++onSaveInstanceState");
        super.onSaveInstanceState(outState);
        outState.putBoolean(SAVED_PENDING_REQUEST_ENABLE_BT, pendingRequestEnableBt);
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
            case R.id.nav_graph_settings:
                startActivityForResult(GraphSettingsActivity.class, MENU_SETTINGS);
                break;
            case R.id.menu_email_recorded_data:
                if (recording.length() > 0 && recordingEnabled) {
                    launchEmailApp(EmailUtils.prepareDeviceRecording(this, deviceName, recording.toString()));
                } else if (recordingEnabled) {
                    Toast.makeText(this, R.string.msg_nothing_recorded, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, R.string.msg_nothing_recorded_recording_disabled, Toast.LENGTH_LONG).show();
                }
                break;
            default:
                break;
        }
        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    }
}