package com.jared.packmule;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

// Class for storing extra utilities as to not clutter up main activity
public class Utilities extends AppCompatActivity {
    public final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    TextView arduinoTxt;
    FloatingActionButton fab, horn, connect;
    RelativeLayout js;
    Context context;
    public Boolean inputsEnabled = true;

    public Utilities(Context context, FloatingActionButton fab, FloatingActionButton horn,
                     FloatingActionButton connect, TextView arduinoTxt, RelativeLayout js) {
        this.context = context;
        this.fab = fab;
        this.horn = horn;
        this.connect = connect;
        this.arduinoTxt = arduinoTxt;
        this.js = js;
        initializeButtonState();
    }

    private void initializeButtonState() {
        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
            setDisconnectedState();
        } else {
            disablePackmuleInputs(true);
        }
    }

    // This function gets called when bluetooth is on, but we are disconnected
    // from the packmule system
    public void setDisconnectedState() {
        js.setBackground(ContextCompat.getDrawable(context, R.drawable.image_button_bg_disabled));
        fab.setVisibility(View.INVISIBLE);
        horn.setVisibility(View.INVISIBLE);
        connect.setVisibility(View.VISIBLE);
        inputsEnabled = false;
    }

    public void disablePackmuleInputs(boolean disable) {
        if (disable) {
            js.setBackground(ContextCompat.getDrawable(context, R.drawable.image_button_bg_disabled));
            fab.setVisibility(View.VISIBLE);
            horn.setVisibility(View.INVISIBLE);
            connect.setVisibility(View.INVISIBLE);
            inputsEnabled = false;
        } else {
            js.setBackground(ContextCompat.getDrawable(context, R.drawable.image_button_bg));
            fab.setVisibility(View.INVISIBLE);
            horn.setVisibility(View.VISIBLE);
            connect.setVisibility(View.INVISIBLE);
            inputsEnabled = true;
        }
    }

    public String createSendingMessage(float angle, float distance, float y, float maxDistance) {
        double l, r = 0;
        String value;
        if (angle == 0) {
            l = distance;
        } else if (angle == 180) {
            l = distance;
            r = l;
        } else if (angle <= 90 || angle >= 270) {
            r = Math.sqrt(Math.pow(distance, 2) / (1 + (1 / Math.pow(Math.sin(Math.toRadians(angle)), 2))));
            l = Math.sqrt(1 / Math.pow(Math.sin(Math.toRadians(angle)), 2)) * r;
        } else {
            l = Math.sqrt(Math.pow(distance, 2) / (1 + (1 / Math.pow(Math.sin(Math.toRadians(angle)), 2))));
            r = Math.sqrt(1 / Math.pow(Math.sin(Math.toRadians(angle)), 2)) * l;
        }
        int offset = 127;
        l = Math.ceil(127 * l / maxDistance);
        r = Math.ceil(127 * r / maxDistance);
        if (y > 0) {
            r = offset - Math.ceil(63 * r / 127);
            l = offset - Math.ceil(63 * l / 127);
        } else {
            r = offset + r;
            l = offset + l;
        }
        value = String.format(Locale.ENGLISH, "%03d", (int) l) + String.format(Locale.ENGLISH, "%03d", (int) r) + "\n";
        return value;
    }

    public void showToast(String text) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
    }

    public void setArduinoTxt(String text) {
        arduinoTxt.setText(text);
    }
}

