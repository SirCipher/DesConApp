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
import android.widget.ExpandableListView;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.DefaultLabelFormatter;
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
import com.type2labs.dmm.utils.Value;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener, SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final boolean debugEnabled = true;

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int REQUEST_LAUNCH_EMAIL_APP = 3;
    private static final int MENU_SETTINGS = 4;

    private static final String SAVED_PENDING_REQUEST_ENABLE_BT = "PENDING_REQUEST_ENABLE_BT";
    private final StringBuilder recording = new StringBuilder();
    ExpandableListAdapter mMenuAdapter;
    ExpandableListView expandableList;
    List<ExpandedMenuModel> listDataHeader;
    HashMap<ExpandedMenuModel, List<String>> listDataChild;
    // Layout Views
    private TextView mStatusView;
    private EditText mOutEditText;
    private View mSendTextContainer;
    private ArrayAdapter<String> mConversationArrayAdapter;
    // Toolbar
    private ImageButton mToolbarConnectButton;
    private ImageButton mToolbarDisconnectButton;
    private ImageButton mToolbarPauseButton;
    private ImageButton mToolbarPlayButton;
    private DeviceConnector mDeviceConnector = new NullConnector();
    // State variables
    private boolean paused = false;
    private boolean connected = false;
    private boolean pendingRequestEnableBt = false;
    // controlled by user settings
    private boolean recordingEnabled = true;
    private boolean mockDevicesEnabled;
    private String deviceName;
    // Graphing
    private GraphView graph;
    private boolean graphEnabled = false;
    private LineGraphSeries<DataPoint> series;
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
    private long startTime;
    private Value value;
    private float resistance, voltage, current;
    private int currentMode = 0;
    private DrawerLayout mDrawerLayout;
    private TextView mCurrentView;
    private TextView mResistanceView;
    private TextView mVoltageView;

    // The Handler that gets information back from the BluetoothService
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MessageHandler.MSG_CONNECTED:
                    connected = true;
                    onBluetoothStateChanged();
                    mStatusView.setText(R.string.btstatus_connected_to_fmt);
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
    private boolean loggingEnabled = true;
    private boolean dataLogging = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "++onCreate");
        registerOnSharedPreferenceChangeListener();

        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Disable landscape
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        if (savedInstanceState != null) {
            pendingRequestEnableBt = savedInstanceState.getBoolean(SAVED_PENDING_REQUEST_ENABLE_BT);
        }

        /* to set the menu icon image*/
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        expandableList = (ExpandableListView) findViewById(R.id.navigationmenu);
        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);

        if (navigationView != null) {
            setupDrawerContent(navigationView);
        }

        prepareListData();
        mMenuAdapter = new ExpandableListAdapter(this, listDataHeader, listDataChild, expandableList);

        // setting list adapter
        expandableList.setAdapter(mMenuAdapter);
        expandableList.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
            @Override
            public boolean onChildClick(ExpandableListView expandableListView, View view, int groupPosition, int childPosition, long l) {
                String position = (String) mMenuAdapter.getChild(groupPosition, childPosition);
                if (groupPosition <= 1) changeMode(position);
                else changeSetting(position);
                return false;
            }


        });
        expandableList.setOnGroupClickListener(new ExpandableListView.OnGroupClickListener() {
            @Override
            public boolean onGroupClick(ExpandableListView expandableListView, View view, int i, long l) {

                return false;
            }
        });

        initUI();
        initGraph();
        onBluetoothStateChanged();
        updateParamsFromSettings();
        value = new Value();
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, mDrawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        mDrawerLayout.setDrawerListener(toggle);
        toggle.syncState();
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

    private void changeSetting(String position) {
        switch (position) {
            case "Email data":
                if (recording.length() > 0 && recordingEnabled) {
                    launchEmailApp(EmailUtils.prepareDeviceRecording(this, deviceName, recording.toString()));
                } else if (recordingEnabled) {
                    Toast.makeText(this, R.string.msg_nothing_recorded, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, R.string.msg_nothing_recorded_recording_disabled, Toast.LENGTH_LONG).show();
                }
                break;
            case "Data logging":
                break;
            case "Toggle Log":
                recordingEnabled = !recordingEnabled;
                setLogVisibility(recordingEnabled);
                break;
            case "Real-time graph":
                graphEnabled = !graphEnabled;
                graph.setVisibility(graphEnabled ? View.VISIBLE : View.GONE);

                break;
            case "Graph Settings":
                startActivityForResult(GraphSettingsActivity.class, MENU_SETTINGS);
                break;
        }

    }

    private void changeMode(String mode) {
        switch (mode) {
            case "Voltage":
                currentMode = 0;
                sendMessage("Volts");
                break;
            case "Current":
                currentMode = 1;
                sendMessage("Amps");
                break;
            case "Resistance":
                currentMode = 2;
                sendMessage("Res");
                break;
            case "Continuity":
                currentMode = 4;
                sendMessage("Cont");
                break;
            case "Transistor":
                currentMode = 5;
                sendMessage("Trans");
                break;
            case "Diode":
                currentMode = 6;
                sendMessage("Dio");
                break;
            case "Capacitance":
                currentMode = 7;
                sendMessage("Cap");
                break;
            case "Inductance":
                currentMode = 8;
                sendMessage("Ind");
                break;
            case "RMS":
                currentMode = 9;
                sendMessage("Rms");
                break;
            case "Frequency":
                currentMode = 10;
                sendMessage("Freq");
                break;
            case "Sig-Gen":
                currentMode = 11;
                break;
            default:
                break;
        }

        ((TextView) findViewById(R.id.current_mode)).setText("Current mode: " + Constants.MODES_STRING[currentMode]);
        Toast.makeText(this, "Mode changed to: " + Constants.MODES_STRING[currentMode], Toast.LENGTH_SHORT).show();
        mDrawerLayout.closeDrawers();
    }

    private void readBTData(Message msg) {
        if (paused) {
            return;
        }

        String receivedData = (String) msg.obj;

        if (debugEnabled) {
            Log.d(TAG, receivedData);
        }

        if (!value.getDaRegex(receivedData)) {
            if (receivedData.startsWith("m")) {
                changeMode(receivedData.substring(2));
            } else {
                mConversationArrayAdapter.add(receivedData);
            }

        } else {
            double receivedValue = value.getValue() * Math.pow(10.0, value.getScale());

            mConversationArrayAdapter.add(value.toString());

            if (recordingEnabled) {
                recording.append(receivedData).append("\n");
            }

            long time = (new Date()).getTime();

            series.appendData(new DataPoint((time - startTime), receivedValue), true, 10000);

            if (value.getUnits() == 'A') {
                mCurrentView.setText(Float.toString(value.getValue()) + "A");
                current = value.getValue();
            }

            if (value.getUnits() == 'V') {
                mVoltageView.setText(Float.toString(value.getValue()) + "V");
                voltage = value.getValue();
            }
            resistance = voltage / current;

            DecimalFormat df = new DecimalFormat("##.###");
            df.setRoundingMode(RoundingMode.HALF_UP);

            mResistanceView.setText(df.format(resistance) + "Ω");
        }
    }

    private void initGraph() {
        series = new LineGraphSeries<>();
        series.setBackgroundColor(Color.parseColor("#BCD2EE"));
        series.setDrawBackground(true);
        series.setThickness(8);

        graph = (GraphView) findViewById(R.id.graph);
        graph.getGridLabelRenderer().setLabelVerticalWidth(130);

        graph.getGridLabelRenderer().setLabelFormatter(new DefaultLabelFormatter() {
            @Override
            public String formatLabel(double value, boolean isValueX) {
                if (isValueX) {
                    return super.formatLabel((value / 1000), isValueX) + "s";
                } else {
                    return super.formatLabel(value, isValueX);
                }
            }
        });

        graph.addSeries(series);
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(10000);
        graph.getViewport().setMinY(10);
        graph.getViewport().setScalable(true);
    }

    private void initUI() {

        mCurrentView = (TextView) findViewById(R.id.view_current);
        mResistanceView = (TextView) findViewById(R.id.view_resistance);
        mVoltageView = (TextView) findViewById(R.id.view_voltage);

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

        // Enable logging layout.
        setLogVisibility(true);
    }

    private void setLogVisibility(boolean visibility) {
        if (visibility) {
            findViewById(R.id.group_measurements).setVisibility(View.GONE);
            findViewById(R.id.in).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.group_measurements).setVisibility(View.VISIBLE);
            findViewById(R.id.in).setVisibility(View.INVISIBLE);
        }
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
            startTime = (new Date()).getTime();
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

    private void prepareListData() {
        listDataHeader = new ArrayList<>();
        listDataChild = new HashMap<>();

        ExpandedMenuModel menu_modes = new ExpandedMenuModel();
        menu_modes.setIconName("Measurements");
        menu_modes.setIconImg(android.R.drawable.ic_menu_manage);
        // Adding data header
        listDataHeader.add(menu_modes);

        ExpandedMenuModel menu_continuity = new ExpandedMenuModel();
        menu_continuity.setIconName("Continuity");
        menu_continuity.setIconImg(android.R.drawable.ic_menu_edit);
        listDataHeader.add(menu_continuity);

        ExpandedMenuModel menu_settings = new ExpandedMenuModel();
        menu_settings.setIconName("Settings");
        menu_settings.setIconImg(R.drawable.ic_settings_black_24dp);
        listDataHeader.add(menu_settings);

        // Adding child data
        List<String> headings_measurements = new ArrayList<>();
        headings_measurements.add("Voltage");
        headings_measurements.add("Current");
        headings_measurements.add("Resistance");
        headings_measurements.add("Capacitance");
        headings_measurements.add("Inductance");
        headings_measurements.add("RMS");
        headings_measurements.add("Frequency");

        List<String> headings_continuity = new ArrayList<>();
        headings_continuity.add("Continuity");
        headings_continuity.add("Diode");
        headings_continuity.add("Transistor");

        List<String> headings_settings = new ArrayList<>();
        headings_settings.add("Email data");
        headings_settings.add("Data logging");
        headings_settings.add("Toggle Log");
        headings_settings.add("Real-time graph");
        headings_settings.add("Graph Settings");

        listDataChild.put(listDataHeader.get(0), headings_measurements);// Header, Child data
        listDataChild.put(listDataHeader.get(1), headings_continuity);
        listDataChild.put(listDataHeader.get(2), headings_settings);
    }

    private void setupDrawerContent(NavigationView navigationView) {
        //revision: this don't works, use setOnChildClickListener() and setOnGroupClickListener() above instead
        navigationView.setNavigationItemSelectedListener(
                new NavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(MenuItem menuItem) {
                        menuItem.setChecked(true);
                        mDrawerLayout.closeDrawers();
                        return true;
                    }
                });
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
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                mDrawerLayout.openDrawer(GravityCompat.START);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        return false;
    }


    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    }
}