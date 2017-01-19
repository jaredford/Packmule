package com.jared.packmule;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
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
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("deprecation")
public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {
    private final static int RECEIVE_MESSAGE = 123;
    private final static StringBuilder sb = new StringBuilder();
    private static final UUID MY_UUID = UUID.fromString("01001101-0900-1100-8080-B1975F9B34AB");
    private static final String TAG = "MainActivity";
    private final MyHandler messageHandler = new MyHandler(this);
    private final String MAC = "98:D3:35:00:AA:83";
    private final int REQUEST_ENABLE_BT = 101;
    private final BroadcastReceiver mStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        fab.setVisibility(View.VISIBLE);
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        break;
                    case BluetoothAdapter.STATE_ON:
                        fab.setVisibility(View.INVISIBLE);
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        break;
                }
            }
        }
    };
    private final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            String muleName = prefs.getString("packmule_name", getResources().getString(R.string.pref_default_display_name));
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Discovery has found a device. Get the BluetoothDevice
                // object and its info from the Intent.
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device.getAddress().equals(MAC)) {
                    mBluetoothAdapter.cancelDiscovery();
                    Toast.makeText(MainActivity.this, "Found " + muleName, Toast.LENGTH_SHORT).show();
                }
            }
        }
    };
    private ConnectedThread mConnectedThread;
    private TextView directionText;
    private JoyStick js;
    private Boolean isSnackBarShown = false;
    private BluetoothSocket mBluetoothSocket = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        IntentFilter btFilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mStateReceiver, btFilter);
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, filter);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        if (!mBluetoothAdapter.isEnabled()) {
            fab.setVisibility(View.VISIBLE);
        } else {
            fab.setVisibility(View.INVISIBLE);
            packmuleConnect();
        }
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                turnOnBluetooth();
            }
        });

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        setupJoyStick();
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

    private void packmuleConnect() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Boolean pairedWithPackmule = false;
                Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
                if (pairedDevices.size() > 0) {
                    // There are paired devices. Get the name and address of each paired device.
                    for (BluetoothDevice device : pairedDevices) {
                        if (device.getAddress().equals(MAC)) {
                            pairedWithPackmule = true;
                        }
                    }
                }
                if (!pairedWithPackmule) {
                    mBluetoothAdapter.startDiscovery();
                }
            }
        }).start();
    }

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        try {
            final Method m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", UUID.class);
            return (BluetoothSocket) m.invoke(device, MY_UUID);
        } catch (Exception e) {
            Log.e(TAG, "Could not create Insecure RFComm Connection", e);
        }
        return device.createRfcommSocketToServiceRecord(MY_UUID);
    }

    private void setupJoyStick() {
        directionText = (TextView) findViewById(R.id.directionText);
        Button buttonStop = (Button) findViewById(R.id.button_stop);
        RelativeLayout layout_joystick = (RelativeLayout) findViewById(R.id.layout_joystick);

        js = new JoyStick(getApplicationContext(), layout_joystick);
        js.setStickSize();
        js.setLayoutAlpha();
        js.setStickAlpha();
        js.setOffset();
        js.setMinimumDistance();
        buttonStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                directionText.setText(getResources().getString(R.string.stopped));
                mConnectedThread.write("Stopped");
            }
        });
        layout_joystick.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View arg0, MotionEvent arg1) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                boolean manualMode = prefs.getBoolean("manual_mode", true);
                String muleName = prefs.getString("packmule_name", getResources().getString(R.string.pref_default_display_name));
                if (manualMode) {
                    js.drawStick(arg1);
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
                } else if (!isSnackBarShown) {
                    final Intent i = new Intent(getApplicationContext(), SettingsActivity.class);
                    i.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT, SettingsActivity.GeneralPreferenceFragment.class.getName());
                    i.putExtra(PreferenceActivity.EXTRA_NO_HEADERS, true);
                    View.OnClickListener listener = new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            startActivity(i);
                        }
                    };
                    Snackbar.make(arg0, muleName + " " + getResources().getString(R.string.joystick_disabled_reason), Snackbar.LENGTH_LONG)
                            .setCallback(new Snackbar.Callback() {
                                @Override
                                public void onDismissed(Snackbar snackbar, int event) {
                                    isSnackBarShown = false;
                                    super.onDismissed(snackbar, event);
                                }

                                @Override
                                public void onShown(Snackbar snackbar) {
                                    isSnackBarShown = true;
                                    super.onShown(snackbar);
                                }
                            })
                            .setAction("Fix", listener).show();
                }
                return true;
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_CANCELED) {
                showToast("Error enabling bluetooth!");
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
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent i = new Intent(this, SettingsActivity.class);
            i.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT, SettingsActivity.GeneralPreferenceFragment.class.getName());
            i.putExtra(PreferenceActivity.EXTRA_NO_HEADERS, true);
            startActivity(i);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_home) {

        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
        unregisterReceiver(mStateReceiver);
    }

    @Override
    public void onResume() {
        super.onResume();
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        if (!mBluetoothAdapter.isEnabled()) {
            fab.setVisibility(View.VISIBLE);
        } else {
            fab.setVisibility(View.INVISIBLE);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "...onResume - try connect...");

                    // Set up a pointer to the remote node using it's address.
                    BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(MAC);

                    // Two things are needed to make a connection:
                    //   A MAC address, which we got above.
                    //   A Service ID or UUID.  In this case we are using the
                    //     UUID for SPP.

                    try {
                        mBluetoothSocket = createBluetoothSocket(device);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    // Discovery is resource intensive.  Make sure it isn't going on
                    // when you attempt to connect and pass your message.
                    mBluetoothAdapter.cancelDiscovery();

                    // Establish the connection.  This will block until it connects.
                    Log.d(TAG, "...Connecting...");
                    try {
                        if (mBluetoothSocket != null) {
                            mBluetoothSocket.connect();
                            Log.d(TAG, "....Connection ok...");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        try {
                            mBluetoothSocket.close();
                        } catch (Exception e2) {
                            e2.printStackTrace();
                        }
                    }

                    // Create a data stream so we can talk to server.
                    Log.d(TAG, "...Create Socket...");

                    mConnectedThread = new ConnectedThread(mBluetoothSocket);
                    mConnectedThread.start();
                }
            }).start();
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        Log.d(TAG, "...In onPause()...");

        try {
            mBluetoothSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showToast(String text) {
        Toast.makeText(MainActivity.this, text, Toast.LENGTH_SHORT).show();
    }

    public static class MyHandler extends Handler {
        private final WeakReference<MainActivity> mActivity;

        private MyHandler(MainActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            MainActivity activity = mActivity.get();
            if (activity != null) {
                switch (msg.what) {
                    case RECEIVE_MESSAGE:
                        byte[] readBuf = (byte[]) msg.obj;
                        String strIncom = new String(readBuf, 0, msg.arg1);
                        sb.append(strIncom);
                        int endOfLineIndex = sb.indexOf("\r\n");
                        if (endOfLineIndex > 0) {
                            String sbprint = sb.substring(0, endOfLineIndex);
                            sb.delete(0, sb.length());
                            /*txtArduino.setText("Data from Arduino: " + sbprint);*/
                        }
                        break;
                }
            }
        }
    }

    private class ConnectedThread extends Thread {
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        ConnectedThread(BluetoothSocket socket) {
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (Exception e) {
                e.printStackTrace();
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[256];  // buffer store for the stream
            int bytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);        // Get number of bytes and message in "buffer"
                    messageHandler.obtainMessage(RECEIVE_MESSAGE, bytes, -1, buffer).sendToTarget();     // Send to message queue Handler
                } catch (Exception e) {
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        void write(String message) {
            Log.d(TAG, "...Data to send: " + message + "...");
            byte[] msgBuffer = message.getBytes();
            try {
                mmOutStream.write(msgBuffer);
            } catch (Exception e) {
                Log.d(TAG, "...Error data send: " + e.getMessage() + "...");
            }
        }
    }
}
