package com.jared.packmule;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.animation.LinearOutSlowInInterpolator;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

// Class for storing extra utilities as to not clutter up main activity
public class Utilities extends AppCompatActivity {
    public BluetoothAdapter mBluetoothAdapter;
    TextView arduinoTxt;
    FloatingActionButton fab, horn, connect;
    RelativeLayout js;
    Context context;
    RotateAnimation rotateAnimation = new RotateAnimation(0, 360, Animation.RELATIVE_TO_SELF,
            .5f, Animation.RELATIVE_TO_SELF, .5f);
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
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.getBoolean("test_mode", false))
            return;
        js.setBackground(ContextCompat.getDrawable(context, R.drawable.image_button_bg_disabled));
        fab.setVisibility(View.INVISIBLE);
        horn.setVisibility(View.INVISIBLE);
        connect.setVisibility(View.VISIBLE);
        inputsEnabled = false;
        rotateConnectFab();
    }

    private void rotateConnectFab() {
        rotateAnimation.setRepeatMode(Animation.RESTART);
        rotateAnimation.setDuration(1750);
        rotateAnimation.setRepeatCount(Animation.INFINITE);
        rotateAnimation.setInterpolator(new LinearOutSlowInInterpolator());
        connect.startAnimation(rotateAnimation);
    }

    public void disablePackmuleInputs(boolean disable) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Boolean testMode = prefs.getBoolean("test_mode", false);
        if (disable && !testMode) {
            js.setBackground(ContextCompat.getDrawable(context, R.drawable.image_button_bg_disabled));
            fab.setVisibility(View.VISIBLE);
            horn.setVisibility(View.INVISIBLE);
        } else {
            js.setBackground(ContextCompat.getDrawable(context, R.drawable.image_button_bg));
            fab.setVisibility(View.INVISIBLE);
            rotateAnimation.cancel();
            connect.setVisibility(View.INVISIBLE);
            horn.setVisibility(View.VISIBLE);
        }
        inputsEnabled = !disable || testMode;
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

    public String createSendingMessageTankStyle(float angle, float y, float distance, float maxDistance) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        int maxSpeed = Integer.parseInt(prefs.getString("speed_scale", "3"));
        String value;
        double speed, direction;
        speed = distance > maxDistance ? maxDistance : distance;
        angle = angle < 180 ? (180 - angle) : (angle - 180);
        angle -= 90;
        // scale back both speed and direction so the fall between -127 and 127
        speed = Math.ceil(speed * 127 * maxSpeed / (maxDistance * 100));
        direction = Math.ceil((angle) * 127 * maxSpeed / (90 * 100));
        // if y is greater than 0, we know we are going reverse direction
        // since bitmaps use a reverse coordinate system
        if (y > 0) {
            speed *= -1;
        }
        // add 127 to speed and direction, so we don't send any negative numbers!
        speed += 127;
        direction += 127;
        direction = speed == 127 ? 127 : direction;
        value = String.format(Locale.ENGLISH, "%03d", (int) speed) + String.format(Locale.ENGLISH, "%03d", (int) direction) + "\n";
        return value;
    }

    public void showToast(String text) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
    }

    public void setArduinoTxt(String text) {
        arduinoTxt.setText(text);
    }
}

