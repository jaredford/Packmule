package com.jared.packmule;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import java.util.List;
import java.util.UUID;

import static java.lang.System.currentTimeMillis;

@SuppressWarnings("deprecation")
public class MainActivity extends AppCompatActivity {
    private Utilities utilities;
    private static final String TAG = "MainActivity";
    private final String MAC = "98:84:E3:D6:82:6F";//"98:D3:35:00:AA:83";
    private final int REQUEST_ENABLE_BT = 101;
    private final int REQUEST_COARSE_LOCATION = 404;
    private TextView directionText;
    private BluetoothGatt mBluetoothGatt;
    private Handler mmHandler;
    private Boolean isScanning = false, eStopEngaged = false;
    Menu mMenu;
    String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    TextView arduinoTxt;
    BluetoothManager bluetoothManager;
    ConstraintLayout layout_joystick;
    FloatingActionButton fab, horn, connect;
    public final static UUID UUID_BLUETOOTH =
            UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    public final static UUID UUID_BLUETOOTH_CHAR =
            UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");

    //region Application States
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putBoolean("manual_mode", true).apply();
        mmHandler = new Handler();
        bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        setContentView(R.layout.activity_main);
        layout_joystick = (ConstraintLayout) findViewById(R.id.layout_joystick);
        arduinoTxt = (TextView) findViewById(R.id.arduinoTxt);
        initializeFabs();
        initializeNavigation();
        setupJoyStick();
        utilities = new Utilities(getApplicationContext(), fab, horn, connect, arduinoTxt, layout_joystick);
        utilities.mBluetoothAdapter = bluetoothManager.getAdapter();

        if (utilities.mBluetoothAdapter != null && utilities.mBluetoothAdapter.isEnabled()) {
            if (bluetoothManager.getConnectedDevices(7).size() == 0) {
                utilities.setDisconnectedState(false);
            } else {
                mBluetoothGatt = bluetoothManager.getConnectedDevices(7).get(0).connectGatt(getApplicationContext(), true, mGattCallback);
                Boolean manualMode = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("manual_mode", true);
                writeCharacteristic(manualMode ? "m\n" : "a\n");
            }
        }
        registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }

    @Override
    public void onResume() {
        super.onResume();
        Boolean manualMode = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("manual_mode", false);
        if (utilities.mBluetoothAdapter != null && utilities.mBluetoothAdapter.isEnabled()) {
            if (bluetoothManager.getConnectedDevices(7).size() == 0) {
                utilities.setDisconnectedState(false);
            } else {
                mBluetoothGatt = bluetoothManager.getConnectedDevices(7).get(0).connectGatt(getApplicationContext(), true, mGattCallback);
                writeCharacteristic(manualMode ? "m\n" : "a\n");
                utilities.setDisconnectedState(false);
            }
        } else {
            utilities.disablePackmuleInputs(!utilities.inputsEnabled);
        }
        if (manualMode) {
            directionText.setText("Stopped");
            layout_joystick.setBackground(ContextCompat.getDrawable(getApplicationContext(), R.drawable.joystick_manual_enabled));
        } else {
            directionText.setText("Following");
            layout_joystick.setBackground(ContextCompat.getDrawable(getApplicationContext(), R.drawable.joystick_stop_disengaged));
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }
    //endregion

    //region Permissions
    protected void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_COARSE_LOCATION);
        } else {
            utilities.mBluetoothAdapter.startLeScan(mLeScanCallback);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_COARSE_LOCATION: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    utilities.mBluetoothAdapter.startLeScan(mLeScanCallback);
                } else {
                    checkLocationPermission();
                }
                break;
            }
        }
    }

    //endregion
    //region new Bluetooth
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                switch (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                    case BluetoothAdapter.STATE_OFF:
                        utilities.disablePackmuleInputs(true);
                        setMenuConnected(false);
                        break;
                    case BluetoothAdapter.STATE_ON:
                        utilities.setDisconnectedState(false);
                        setMenuConnected(false);
                        scanLeDevice(true);
                        break;
                }
            }
        }
    };
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (device.getAddress().equals(MAC)) {
                                scanLeDevice(false);
                                utilities.disablePackmuleInputs(false);
                                setMenuConnected(true);
                                mBluetoothGatt = device.connectGatt(getApplicationContext(), true, mGattCallback);
                            }
                        }
                    });
                }
            };

    private void scanLeDevice(final boolean enable) {
        if (mBluetoothGatt == null) {
            mBluetoothGatt = bluetoothManager.getConnectedDevices(7).size() > 0 ? bluetoothManager.getConnectedDevices(7).get(0).connectGatt(getApplicationContext(), true, mGattCallback) : null;
        }
        if (mBluetoothGatt != null) {
            utilities.disablePackmuleInputs(false);
            setMenuConnected(true);
        }
        if (isScanning == enable) { // If these have equivalent values, we know that there is nothing to do
            return;
        }
        if (!utilities.mBluetoothAdapter.isEnabled()) {
            utilities.disablePackmuleInputs(true);
            setMenuConnected(false);
            return;
        } else {
            utilities.setDisconnectedState(false);
            setMenuConnected(false);
        }
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mmHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    isScanning = false;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (mBluetoothGatt == null) {
                                utilities.connect.clearAnimation();
                                utilities.showToast(getResources().getString(R.string.discovery_failed) + " " + PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString("packmule_name", "Packmule"));
                            }
                            if (!utilities.mBluetoothAdapter.isEnabled()) {
                                utilities.disablePackmuleInputs(true);
                                setMenuConnected(false);
                            }
                        }
                    });
                    utilities.mBluetoothAdapter.stopLeScan(mLeScanCallback);
                }
            }, 5000);
            isScanning = true;
            utilities.setDisconnectedState(true);
            setMenuConnected(false);
            checkLocationPermission();
        } else {
            isScanning = false;
            utilities.mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    }

    public boolean writeCharacteristic(String value) {
        //check mBluetoothGatt is available
        if (!utilities.mBluetoothAdapter.isEnabled()) {
            utilities.disablePackmuleInputs(true);
            setMenuConnected(false);
            return false;
        }
        if (mBluetoothGatt == null) {
            mBluetoothGatt = bluetoothManager.getConnectedDevices(7).size() > 0 ? bluetoothManager.getConnectedDevices(7).get(0).connectGatt(getApplicationContext(), true, mGattCallback) : null;
        }
        if (mBluetoothGatt == null) {
            Log.e(TAG, "lost connection");
            return false;
        }
        List<BluetoothDevice> deviceList = bluetoothManager.getConnectedDevices(7);
        boolean connected = false;
        for (BluetoothDevice device : deviceList) {
            if (device.getName().equals("Packmule") && bluetoothManager.getConnectionState(device, 7) == BluetoothGatt.STATE_CONNECTED)
                connected = true;
        }
        if (!connected) {
            return false;
        }
        final long currentMillis = currentTimeMillis();
        try {
            while (mBluetoothGatt.getService(UUID_BLUETOOTH) == null) {
                if (currentTimeMillis() > (currentMillis + 500)) {
                    utilities.setDisconnectedState(false);
                    setMenuConnected(false);
                    Log.e(TAG, "service not found!");
                    return false;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        BluetoothGattService Service = mBluetoothGatt.getService(UUID_BLUETOOTH);
        while (Service.getCharacteristic(UUID_BLUETOOTH_CHAR) == null) {
            Log.e(TAG, "char not found!");
        }
        BluetoothGattCharacteristic characteristic = Service.getCharacteristic(UUID_BLUETOOTH_CHAR);
        characteristic.setValue(value);
        return mBluetoothGatt.writeCharacteristic(characteristic);
    }

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            // this will get called anytime you perform a read or write characteristic operation
            String temp = characteristic.getStringValue(0);
            if (!temp.equals("m") && !temp.equals("a") && characteristic.getStringValue(0).length() != 7) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        utilities.setArduinoTxt(characteristic.getStringValue(0));
                    }
                });
            } else {
                final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                boolean manualMode = prefs.getBoolean("manual_mode", true);
                if (manualMode && temp.equals("a")) {
                    writeCharacteristic("m");
                } else if (!manualMode && temp.equals("m")) {
                    writeCharacteristic("a");
                }
            }
        }

        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
            // this will get called when a device connects or disconnects
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        switch (newState) {
                            case BluetoothGatt.STATE_CONNECTED:
                                if (mBluetoothGatt == null) {
                                    mBluetoothGatt = bluetoothManager.getConnectedDevices(7).size() > 0 ? bluetoothManager.getConnectedDevices(7).get(0).connectGatt(getApplicationContext(), true, mGattCallback) : null;
                                }
                                utilities.disablePackmuleInputs(false);
                                setMenuConnected(true);
                                break;
                            case BluetoothGatt.STATE_DISCONNECTED:
                                utilities.setDisconnectedState(false);
                                setMenuConnected(false);
                                break;
                        }
                    }

                }
            });
            gatt.discoverServices();
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
            setCharacteristicNotification(gatt);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    utilities.disablePackmuleInputs(false);
                    setMenuConnected(true);
                }
            });
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "read characteristic");
            }
        }

        private void setCharacteristicNotification(BluetoothGatt gatt) {
            BluetoothGattCharacteristic characteristic = gatt.getService(UUID_BLUETOOTH).getCharacteristic(UUID_BLUETOOTH_CHAR);
            gatt.setCharacteristicNotification(characteristic, true);
            Log.i(TAG, characteristic.toString());
            if (UUID_BLUETOOTH_CHAR.equals(characteristic.getUuid())) {
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                        UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(descriptor);
                gatt.readCharacteristic(characteristic);
            }
        }
    };

    //endregion
    //region Bluetooth Functions
    private void setMenuConnected(boolean connected) {
        if (mMenu != null) {
            mMenu.findItem(R.id.action_connect).setVisible(!connected);
            mMenu.findItem(R.id.action_disconnect).setVisible(connected);
        }
    }

    private void turnOnBluetooth() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }).start();
    }

    //region Joystick
    public void setupJoyStick() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        directionText = (TextView) findViewById(R.id.directionText);
        final JoyStick js = new JoyStick(getApplicationContext(), layout_joystick);
        float density = getResources().getDisplayMetrics().density;
        js.setStickSize(density);
        js.setLayoutAlpha();
        js.setStickAlpha();
        js.setOffset(density);
        js.setMinimumDistance();
        layout_joystick.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View arg0, MotionEvent arg1) {
                try {
                    boolean manualMode = prefs.getBoolean("manual_mode", true);
                    String muleName = prefs.getString("packmule_name", getResources().getString(R.string.pref_default_display_name));
                    if (manualMode) {
                        js.drawStick(arg1);
                        String message;
                        try {
                            if (arg1.getAction() == MotionEvent.ACTION_DOWN
                                    || arg1.getAction() == MotionEvent.ACTION_MOVE) {
                                int direction = js.get8Direction();
                                if (direction == JoyStick.STICK_UP) {
                                    directionText.setText(getResources().getString(R.string.forward));
                                } else if (direction == JoyStick.STICK_UPRIGHT) {
                                    directionText.setText(getResources().getString(R.string.forward) + " " + getResources().getString(R.string.right));
                                } else if (direction == JoyStick.STICK_RIGHT) {
                                    directionText.setText(getResources().getString(R.string.right));
                                } else if (direction == JoyStick.STICK_DOWNRIGHT) {
                                    directionText.setText(getResources().getString(R.string.reverse) + " " + getResources().getString(R.string.right));
                                } else if (direction == JoyStick.STICK_DOWN) {
                                    directionText.setText(getResources().getString(R.string.reverse));
                                } else if (direction == JoyStick.STICK_DOWNLEFT) {
                                    directionText.setText(getResources().getString(R.string.reverse) + " " + getResources().getString(R.string.left));
                                } else if (direction == JoyStick.STICK_LEFT) {
                                    directionText.setText(getResources().getString(R.string.left));
                                } else if (direction == JoyStick.STICK_UPLEFT) {
                                    directionText.setText(getResources().getString(R.string.forward) + " " + getResources().getString(R.string.left));
                                } else if (direction == JoyStick.STICK_NONE) {
                                    directionText.setText(getResources().getString(R.string.stopped));
                                }
                            } else if (arg1.getAction() == MotionEvent.ACTION_UP) {
                                directionText.setText(getResources().getString(R.string.stopped));
                            }
                            message = utilities.createSendingMessageTankStyle(js.getAngle(), js.getY(), js.getDistance(), js.getWidth() / 2);
                            if (bluetoothManager.getConnectedDevices(7).size() > 0) {
                                writeCharacteristic(message);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else if (arg1.getAction() == MotionEvent.ACTION_DOWN) {
                        if (eStopEngaged) {
                            layout_joystick.setBackground(ContextCompat.getDrawable(getApplicationContext(), R.drawable.joystick_stop_engaged_press));
                        } else {
                            layout_joystick.setBackground(ContextCompat.getDrawable(getApplicationContext(), R.drawable.joystick_stop_disengaged_press));
                        }
                    } else if (arg1.getAction() == MotionEvent.ACTION_UP) {
                        if (arg1.getX() < layout_joystick.getWidth() && arg1.getY() < layout_joystick.getHeight() && arg1.getX() > 0 && arg1.getY() > 0) {
                            if (eStopEngaged) {
                                eStopEngaged = false;
                                layout_joystick.setBackground(ContextCompat.getDrawable(getApplicationContext(), R.drawable.joystick_stop_disengaged));
                                directionText.setText("Following");
                                if (bluetoothManager.getConnectedDevices(7).size() > 0) {
                                    writeCharacteristic("d\n");
                                }
                            } else {
                                eStopEngaged = true;
                                layout_joystick.setBackground(ContextCompat.getDrawable(getApplicationContext(), R.drawable.joystick_stop_engaged));
                                directionText.setText("Stopped");
                                if (bluetoothManager.getConnectedDevices(7).size() > 0) {
                                    writeCharacteristic("e\n");
                                }
                            }
                        } else if (eStopEngaged) {
                            layout_joystick.setBackground(ContextCompat.getDrawable(getApplicationContext(), R.drawable.joystick_stop_engaged));
                        } else {
                            layout_joystick.setBackground(ContextCompat.getDrawable(getApplicationContext(), R.drawable.joystick_stop_disengaged));
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return true;
            }
        });
    }

    //endregion

    //region Floating Action Buttons
    private void initializeFabs() {
        fab = (FloatingActionButton) findViewById(R.id.fab);
        horn = (FloatingActionButton) findViewById(R.id.horn);
        connect = (FloatingActionButton) findViewById(R.id.connect);
        horn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    writeCharacteristic("h\n");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (utilities.mBluetoothAdapter == null)
                    return;
                if (!utilities.mBluetoothAdapter.isEnabled()) {
                    turnOnBluetooth();
                }
            }
        });
        connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanLeDevice(true);
            }
        });
    }
    //endregion

    //region Navigation
    private void initializeNavigation() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close) {
            /**
             * Called when a drawer has settled in a completely closed state.
             */
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
                Boolean manualMode = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("manual_mode", false);
                if (manualMode) {
                    directionText.setText("Stopped");
                    layout_joystick.setBackground(ContextCompat.getDrawable(getApplicationContext(), R.drawable.joystick_manual_enabled));
                } else {
                    directionText.setText("Following");
                    layout_joystick.setBackground(ContextCompat.getDrawable(getApplicationContext(), R.drawable.joystick_stop_disengaged));
                }
            }

            /**
             * Called when a drawer has settled in a completely open state.
             */
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
            }
        };
        drawer.setDrawerListener(toggle);
        toggle.syncState();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_CANCELED) {
                utilities.showToast("Error enabling bluetooth!");
            }
        }
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        mMenu = menu;
        try {
            if (bluetoothManager.getConnectedDevices(7).size() > 0) {
                setMenuConnected(true);
            } else {
                setMenuConnected(false);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_disconnect) {
            if (mBluetoothGatt != null) {
                mBluetoothGatt.disconnect();
            }
        } else if (id == R.id.action_connect) {

            if (bluetoothManager.getConnectedDevices(7).size() > 0) {
                bluetoothManager.getConnectedDevices(7).get(0).connectGatt(getApplicationContext(), true, mGattCallback);
            } else if (utilities.mBluetoothAdapter == null || !utilities.mBluetoothAdapter.isEnabled()) {
                utilities.showToast("Bluetooth is turned off");
            } else {
                scanLeDevice(true);
            }
        }
        return super.onOptionsItemSelected(item);
    }
    //endregion
}