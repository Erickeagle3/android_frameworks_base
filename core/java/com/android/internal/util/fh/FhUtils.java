/*
 * Copyright (C) 2017 FireHound ROMs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.util.fh;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.input.InputManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.IWindowManager;
import android.view.WindowManagerGlobal;

import java.util.Arrays;
import java.util.List;

import java.util.Locale;

public class FhUtils {

    private static final String TAG = "FhUtils";

    public static final String INTENT_SCREENSHOT = "action_take_screenshot";
    public static final String INTENT_REGION_SCREENSHOT = "action_take_region_screenshot";

    public static boolean isChineseLanguage() {
       return Resources.getSystem().getConfiguration().locale.getLanguage().startsWith(
               Locale.CHINESE.getLanguage());
    }

    public static boolean isWifiOnly(Context context) {
        ConnectivityManager cm = (ConnectivityManager)context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        return (cm.isNetworkSupported(ConnectivityManager.TYPE_MOBILE) == false);
    }

   /**
     * Checks if a specific package is installed.
     *
     * @param context     The context to retrieve the package manager
     * @param packageName The name of the package
     * @return Whether the package is installed or not.
     */
    public static boolean isPackageInstalled(Context context, String packageName) {
        PackageManager pm = context.getPackageManager();
        try {
            if (pm != null) {
                List<ApplicationInfo> packages = pm.getInstalledApplications(0);
                for (ApplicationInfo packageInfo : packages) {
                    if (packageInfo.packageName.equals(packageName)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error: " + e.getMessage());
        }
        return false;
    }

    public static boolean isPackageEnabled(String packageName, PackageManager pm) {
        try {
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            return ai.enabled;
        } catch (PackageManager.NameNotFoundException notFound) {
            return false;
        }
    }

    /**
     * Checks if a specific service is running.
     *
     * @param context     The context to retrieve the activity manager
     * @param serviceName The name of the service
     * @return Whether the service is running or not
     */
    public static boolean isServiceRunning(Context context, String serviceName) {
        ActivityManager activityManager = (ActivityManager) context
                .getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> services = activityManager
                .getRunningServices(Integer.MAX_VALUE);

        if (services != null) {
            for (ActivityManager.RunningServiceInfo info : services) {
                if (info.service != null) {
                    if (info.service.getClassName() != null && info.service.getClassName()
                            .equalsIgnoreCase(serviceName)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Check if system has a camera.
     *
     * @param context
     * @return
     */
    public static boolean hasCamera(final Context context) {
        final PackageManager pm = context.getPackageManager();
        return pm != null && pm.hasSystemFeature(PackageManager.FEATURE_CAMERA);
    }

    /**
     * Check if system has a front camera.
     *
     * @param context
     * @return
     */
    public static boolean hasFrontCamera(final Context context) {
        final PackageManager pm = context.getPackageManager();
        return pm != null && pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT);
    }

    public static boolean deviceSupportsFlashLight(Context context) {
        CameraManager cameraManager = (CameraManager) context.getSystemService(
                Context.CAMERA_SERVICE);
        try {
            String[] ids = cameraManager.getCameraIdList();
            for (String id : ids) {
                CameraCharacteristics c = cameraManager.getCameraCharacteristics(id);
                Boolean flashAvailable = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer lensFacing = c.get(CameraCharacteristics.LENS_FACING);
                if (flashAvailable != null
                        && flashAvailable
                        && lensFacing != null
                        && lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                    return true;
                }
            }
        } catch (CameraAccessException e) {
            // Ignore
        }
        return false;
    }

    // Omni Switch Constants

    /**
     * Package name of the omnniswitch app
     */
    public static final String APP_PACKAGE_NAME = "org.omnirom.omniswitch";

    /**
     * Intent broadcast action for toogle the omniswitch overlay
     */
    private static final String ACTION_TOGGLE_OVERLAY2 = APP_PACKAGE_NAME + ".ACTION_TOGGLE_OVERLAY2";

    /**
     * Intent broadcast action for telling omniswitch to preload tasks
     */
    private static final String ACTION_PRELOAD_TASKS = APP_PACKAGE_NAME + ".ACTION_PRELOAD_TASKS";

    /**
     * Intent broadcast action for restoring the home stack
     */
    private static final String ACTION_RESTORE_HOME_STACK = APP_PACKAGE_NAME + ".ACTION_RESTORE_HOME_STACK";

    /**
     * Intent broadcast action for hide the omniswitch overlay
     */
    private static final String ACTION_HIDE_OVERLAY = APP_PACKAGE_NAME + ".ACTION_HIDE_OVERLAY";

    /**
     * Intent for launching the omniswitch settings actvity
     */
    public static Intent INTENT_LAUNCH_APP = new Intent(Intent.ACTION_MAIN)
            .setClassName(APP_PACKAGE_NAME, APP_PACKAGE_NAME + ".SettingsActivity");

    public static boolean isBillingBypassInstalled(final Context context) {
        boolean mBillingBypassInstalled = false;
        final String[] billingBypassPackageNames = {
            "com.dimonvideo.luckypatcher", // used by Lucky Patcher
            "com.chelpus.lackypatch", // used by Lucky Patcher
            "com.android.vending.billing.InAppBillingService.LACK", // used by Lucky Patcher
            "com.android.vending.billing.InAppBillingService.LOCK", // used by Lucky Patcher
            "com.android.vending.billing.InAppBillingService.CLON", // used by Lucky Patcher
            "com.android.vendinc", // used by Uret
            "uret.jasi2169.patcher" // used by Uret
        };
        for (String billingBypassPackageName : billingBypassPackageNames) {
            mBillingBypassInstalled = mBillingBypassInstalled || isPackageInstalled(context, billingBypassPackageName);
        }

        return mBillingBypassInstalled;
    }

    /**
     * @hide
     */
    public static void toggleOmniSwitchRecents(Context context, UserHandle user) {
        final Intent intent = new Intent(FhUtils.ACTION_TOGGLE_OVERLAY2);
        intent.setPackage(APP_PACKAGE_NAME);
        context.sendBroadcastAsUser(intent, user);
    }

    /**
     * @hide
     */
    public static void hideOmniSwitchRecents(Context context, UserHandle user) {
        final Intent intent = new Intent(FhUtils.ACTION_HIDE_OVERLAY);
        intent.setPackage(APP_PACKAGE_NAME);
        context.sendBroadcastAsUser(intent, user);
    }

    /**
     * @hide
     */
    public static void restoreHomeStack(Context context, UserHandle user) {
        final Intent intent = new Intent(FhUtils.ACTION_RESTORE_HOME_STACK);
        intent.setPackage(APP_PACKAGE_NAME);
        context.sendBroadcastAsUser(intent, user);
    }

    /**
     * @hide
     */
    public static void preloadOmniSwitchRecents(Context context, UserHandle user) {
        final Intent intent = new Intent(FhUtils.ACTION_PRELOAD_TASKS);
        intent.setPackage(APP_PACKAGE_NAME);
        context.sendBroadcastAsUser(intent, user);
    }

    public static int getBlendColorForPercent(int fullColor, int emptyColor, boolean reversed,
                                        int percentage) {
        float[] newColor = new float[3];
        float[] empty = new float[3];
        float[] full = new float[3];
        Color.colorToHSV(fullColor, full);
        int fullAlpha = Color.alpha(fullColor);
        Color.colorToHSV(emptyColor, empty);
        int emptyAlpha = Color.alpha(emptyColor);
        float blendFactor = percentage/100f;
        if (reversed) {
            if (empty[0] < full[0]) {
                empty[0] += 360f;
            }
            newColor[0] = empty[0] - (empty[0]-full[0])*blendFactor;
        } else {
            if (empty[0] > full[0]) {
                full[0] += 360f;
            }
            newColor[0] = empty[0] + (full[0]-empty[0])*blendFactor;
        }
        if (newColor[0] > 360f) {
            newColor[0] -= 360f;
        } else if (newColor[0] < 0) {
            newColor[0] += 360f;
        }
        newColor[1] = empty[1] + ((full[1]-empty[1])*blendFactor);
        newColor[2] = empty[2] + ((full[2]-empty[2])*blendFactor);
        int newAlpha = (int) (emptyAlpha + ((fullAlpha-emptyAlpha)*blendFactor));
        return Color.HSVToColor(newAlpha, newColor);
    }

    public static void switchScreenOff(Context ctx) {
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        if (pm!= null) {
            pm.goToSleep(SystemClock.uptimeMillis());
        }
    }

    public static void sendKeycode(int keycode) {
        long when = SystemClock.uptimeMillis();
        final KeyEvent evDown = new KeyEvent(when, when, KeyEvent.ACTION_DOWN, keycode, 0,
                0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY,
                InputDevice.SOURCE_KEYBOARD);
        final KeyEvent evUp = KeyEvent.changeAction(evDown, KeyEvent.ACTION_UP);

        final Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                InputManager.getInstance().injectInputEvent(evDown,
                        InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
            }
        });
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                InputManager.getInstance().injectInputEvent(evUp,
                        InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
            }
        }, 20);
    }

    public static void takeScreenshot(boolean full) {
        IWindowManager wm = WindowManagerGlobal.getWindowManagerService();
        try {
            wm.sendCustomAction(new Intent(full? INTENT_SCREENSHOT : INTENT_REGION_SCREENSHOT));
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public static boolean hasNavbarByDefault(Context context) {
        boolean needsNav = context.getResources().getBoolean(
                com.android.internal.R.bool.config_showNavigationBar);
        String navBarOverride = SystemProperties.get("qemu.hw.mainkeys");
        if ("1".equals(navBarOverride)) {
            needsNav = false;
        } else if ("0".equals(navBarOverride)) {
            needsNav = true;
        }
        return needsNav;
    }

    // Method to detect battery temperature
    public static String batteryTemperature(Context context, Boolean ForC) {
        Intent intent = context.registerReceiver(null, new IntentFilter(
                Intent.ACTION_BATTERY_CHANGED));
        float  temp = ((float) (intent != null ? intent.getIntExtra(
                BatteryManager.EXTRA_TEMPERATURE, 0) : 0)) / 10;
        // Round up to nearest number
        int c = (int) ((temp) + 0.5f);
        float n = temp + 0.5f;
        // Use boolean to determine celsius or fahrenheit
        return String.valueOf((n - c) % 2 == 0 ? (int) temp :
                ForC ? c * 9/5 + 32:c);
    }

    // Method to detect countries that use Fahrenheit
    public static boolean mccCheck(Context context) {
        // MCC's belonging to countries that use Fahrenheit
        String[] mcc = {"364", "552", "702", "346", "550", "376", "330",
                "310", "311", "312", "551"};

        TelephonyManager tel = (TelephonyManager) context.getSystemService(
                Context.TELEPHONY_SERVICE);
        String networkOperator = tel.getNetworkOperator();

        // Check the array to determine celsius or fahrenheit.
        // Default to celsius if can't access MCC
        return !TextUtils.isEmpty(networkOperator) && Arrays.asList(mcc).contains(
                networkOperator.substring(0, /*Filter only 3 digits*/ 3));
    }

    public static boolean deviceHasCompass(Context ctx) {
        SensorManager sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        return sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
                && sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null;
    }
}
