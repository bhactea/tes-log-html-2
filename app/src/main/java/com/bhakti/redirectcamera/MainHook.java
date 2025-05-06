package com.bhakti.redirectcamera;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
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
    private static final String PREFS_NAME     = "redirectcamera_cache";
    private static final String KEY_BYPASS_PIN = "bypassPin";
    private static final String KEY_PIN        = "userPin";
    private static final String DEFAULT_PIN    = "0000";

    private SharedPreferences prefs;

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!"com.harpamobilehr".equals(lpparam.packageName)) return;

        // init SharedPreferences modul
        Context appCtx = (Context) XposedHelpers.callStaticMethod(
            XposedHelpers.findClass("android.app.ActivityThread", null),
            "currentApplication"
        );
        prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        XposedBridge.log("RedirectCameraHook: init for " + lpparam.packageName);

        hookAsyncStorageMultiSet(lpparam);
        hookMainActivityOnCreate(lpparam);
        hookCameraRedirect(lpparam);
    }

    // 1) Cache PIN via AsyncStorage.multiSet
    private void hookAsyncStorageMultiSet(final LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.reactnativecommunity.asyncstorage.AsyncStorageModule",
                lpparam.classLoader,
                "multiSet",
                List.class,
                com.facebook.react.bridge.Callback.class,
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        List<?> pairs = (List<?>) param.args[0];
                        for (Object o : pairs) {
                            List<?> pair = (List<?>) o;
                            String key   = (String) pair.get(0);
                            String value = (String) pair.get(1);
                            if (KEY_PIN.equals(key)) {
                                prefs.edit()
                                     .putBoolean(KEY_BYPASS_PIN, true)
                                     .putString(KEY_PIN, value)
                                     .apply();
                                XposedBridge.log("RedirectCameraHook: Cached PIN=" + value);
                            }
                        }
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("AsyncStorage.multiSet hook failed: " + t);
        }
    }

    // 2) Bypass PIN in MainActivity.onCreate
    private void hookMainActivityOnCreate(final LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.harpamobilehr.MainActivity",
                lpparam.classLoader,
                "onCreate",
                Bundle.class,
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        Activity act = (Activity) param.thisObject;
                        if (prefs.getBoolean(KEY_BYPASS_PIN, false)) {
                            XposedBridge.log("RedirectCameraHook: Bypassing PIN via cache");
                            Intent i = new Intent(act,
                                XposedHelpers.findClass("com.harpamobilehr.ui.HomeActivity",
                                lpparam.classLoader));
                            act.startActivity(i);
                            act.finish();
                        }
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("MainActivity.onCreate hook failed: " + t);
        }
    }

    // 3) Redirect kamera via startActivityForResult & execStartActivity
    private void hookCameraRedirect(final LoadPackageParam lpparam) {
        XC_MethodHook cameraHook = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                Intent orig = null;
                String method = param.method.getName();
                if ("startActivityForResult".equals(method)) {
                    orig = (Intent) param.args[0];
                } else { // execStartActivity
                    orig = (Intent) param.args[4];
                }
                if (orig != null && MediaStore.ACTION_IMAGE_CAPTURE.equals(orig.getAction())) {
                    XposedBridge.log("RedirectCameraHook: intercept CAMERA intent");
                    orig.setPackage("net.sourceforge.opencamera");
                    orig.putExtra("android.intent.extras.CAMERA_FACING", 1);
                    if ("startActivityForResult".equals(method)) {
                        param.args[0] = orig;
                    } else {
                        param.args[4] = orig;
                    }
                }
            }
        };

        try {
            Class<?> activityClass = XposedHelpers.findClass("android.app.Activity", null);
            XposedHelpers.findAndHookMethod(
                activityClass,
                "startActivityForResult",
                Intent.class, int.class,
                cameraHook
            );
            XposedHelpers.findAndHookMethod(
                activityClass,
                "startActivityForResult",
                Intent.class, int.class, Bundle.class,
                cameraHook
            );
        } catch (Throwable t) {
            XposedBridge.log("Camera hook (startActivityForResult) failed: " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Instrumentation",
                lpparam.classLoader,
                "execStartActivity",
                Context.class, IBinder.class, IBinder.class,
                Activity.class, Intent.class, int.class, Bundle.class,
                cameraHook
            );
        } catch (Throwable t) {
            XposedBridge.log("Camera hook (execStartActivity) failed: " + t);
        }
    }
}
