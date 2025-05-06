package com.bhakti.redirectcamera;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;

import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class MainHook implements IXposedHookLoadPackage {
    private static final String PREFS_NAME = "redirectcamera_cache";
    private static String cachedPin = null;

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!"com.harpamobilehr".equals(lpparam.packageName)) {
            return;
        }
        XposedBridge.log("RedirectCameraHook: init for " + lpparam.packageName);

        hookAsyncStorageMultiSet(lpparam);
        hookMainActivityOnCreate(lpparam);
        hookCameraRedirect(lpparam);
    }

    private void hookAsyncStorageMultiSet(final LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.reactnativecommunity.asyncstorage.AsyncStorageModule",
                lpparam.classLoader,
                "multiSet",
                List.class,
                Object.class,
                new XC_MethodHook() {
                    @SuppressWarnings("unchecked")
                    @Override protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        List<?> pairs = (List<?>) param.args[0];
                        for (Object o : pairs) {
                            List<?> kv = (List<?>) o;
                            String key = (String) kv.get(0);
                            String value = (String) kv.get(1);
                            if ("userPin".equals(key)) {
                                cachedPin = value;
                                XposedBridge.log("Cached PIN=" + value);
                            }
                        }
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("AsyncStorage.multiSet hook failed: " + t);
        }
    }

    private void hookMainActivityOnCreate(final LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.harpamobilehr.MainActivity",
                lpparam.classLoader,
                "onCreate",
                Bundle.class,
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Activity act = (Activity) param.thisObject;
                        SharedPreferences prefs = act.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

                        // Store cached PIN into prefs
                        if (cachedPin != null) {
                            prefs.edit()
                                 .putString("userPin", cachedPin)
                                 .putBoolean("bypassPin", true)
                                 .apply();
                            cachedPin = null;
                        }

                        // Bypass PIN
                        if (prefs.getBoolean("bypassPin", false)) {
                            XposedBridge.log("Bypassing PIN screen");
                            try {
                                Class<?> homeCls = XposedHelpers.findClass(
                                    "com.harpamobilehr.ui.HomeActivity",
                                    lpparam.classLoader
                                );
                                act.startActivity(new Intent(act, homeCls));
                                act.finish();
                            } catch (Throwable t) {
                                XposedBridge.log("Error launching HomeActivity: " + t);
                            }
                        }
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("MainActivity.onCreate hook failed: " + t);
        }
    }

    private void hookCameraRedirect(final LoadPackageParam lpparam) {
        XC_MethodHook camHook = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Intent orig;
                String methodName = param.method.getName();
                if ("startActivityForResult".equals(methodName)) {
                    orig = (Intent) param.args[0];
                    if (orig != null && MediaStore.ACTION_IMAGE_CAPTURE.equals(orig.getAction())) {
                        XposedBridge.log("Redirect to OpenCamera front via startActivityForResult");
                        Intent ni = new Intent(orig);
                        ni.setPackage("net.sourceforge.opencamera");
                        ni.putExtra("android.intent.extras.CAMERA_FACING", 1);
                        param.args[0] = ni;
                    }
                }
                // execStartActivity handled below
            }
        };

        try {
            Class<?> activityClass = XposedHelpers.findClass("android.app.Activity", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(
                activityClass,
                "startActivityForResult",
                Intent.class, int.class, camHook
            );
            XposedHelpers.findAndHookMethod(
                activityClass,
                "startActivityForResult",
                Intent.class, int.class, Bundle.class, camHook
            );
        } catch (Throwable t) {
            XposedBridge.log("Hook startActivityForResult failed: " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Instrumentation",
                lpparam.classLoader,
                "execStartActivity",
                Context.class, IBinder.class, IBinder.class,
                Activity.class, Intent.class, int.class, Bundle.class,
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Intent orig = (Intent) param.args[4];
                        if (orig != null && MediaStore.ACTION_IMAGE_CAPTURE.equals(orig.getAction())) {
                            XposedBridge.log("Redirect to OpenCamera front via execStartActivity");
                            Intent ni = new Intent(orig);
                            ni.setPackage("net.sourceforge.opencamera");
                            ni.putExtra("android.intent.extras.CAMERA_FACING", 1);
                            param.args[4] = ni;
                        }
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("Hook execStartActivity failed: " + t);
        }
    }
}
