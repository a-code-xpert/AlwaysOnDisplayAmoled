package com.tomer.alwayson.Receivers;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import com.tomer.alwayson.Globals;
import com.tomer.alwayson.Prefs;
import com.tomer.alwayson.Services.MainService;

import static android.content.Context.POWER_SERVICE;

public class ScreenReceiver extends BroadcastReceiver {

    private static final String TAG = ScreenReceiver.class.getSimpleName();
    private static final String WAKE_LOCK_TAG = "ScreenOnWakeLock";
    private Context context;
    Prefs prefs;

    public static void turnScreenOn(Context c, boolean stopService) {
        try {
            if (stopService) {
                c.stopService(new Intent(c, MainService.class));
                Globals.isShown = false;
            }
            @SuppressWarnings("deprecation")
            PowerManager.WakeLock wl = ((PowerManager) c.getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, WAKE_LOCK_TAG);
            wl.acquire();
            wl.release();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onReceive(final Context context, Intent intent) {
        prefs = new Prefs(context);
        prefs.apply();

        this.context = context;

        if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
            Globals.sensorIsScreenOff = true;
            Log.i(TAG, "Screen turned off\nShown:" + Globals.isShown);
            if (Globals.isShown) {
                // Screen turned off with service running, wake up device
                turnScreenOn(context, true);
            } else {
                // Start service when screen is off
                if (!Globals.inCall && prefs.getBoolByKey("enabled", true)) {
                    if (shouldStart()) {
                        KeyguardManager myKM = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
                        if (myKM.inKeyguardRestrictedInputMode()) {
                            //Screen is locked, start the service
                            context.startService(new Intent(context, MainService.class));
                            Globals.isShown = true;
                        } else {
                            //Screen is unlocked, wait until the lock timeout is over before starting the service.
                            int startDelay;
                            try {
                                startDelay = Settings.Secure.getInt(context.getContentResolver(), "lock_screen_lock_after_timeout", 0);
                            } catch (Exception settingNotFound) {
                                startDelay = 0;
                            }
                            new Handler().postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    context.startService(new Intent(context, MainService.class));
                                    Globals.isShown = true;
                                }
                            }, startDelay);
                        }
                    }
                }
            }
        } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
            Log.i(TAG, "Screen turned on\nShown:" + Globals.isShown);
        }
    }

    private boolean isConnected() {
        Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        assert intent != null;
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        return plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB;
    }

    private boolean shouldStart() {
        prefs.apply();
        if (prefs.rules.equals("charging")) {
            return isConnected();
        } else if (prefs.rules.equals("discharging")) {
            return !isConnected();
        }
        return true;
    }
}
