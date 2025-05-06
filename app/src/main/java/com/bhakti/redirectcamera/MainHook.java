package com.bhakti.redirectcamera;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableNativeArray;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {
    private static final String TARGET_PKG = "com.harpamobilehr";
    private static final String STORAGE_KEY = "HARPA_ASYNC_KEYS";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!TARGET_PKG.equals(lpparam.packageName)) return;

        XposedBridge.log("RedirectCameraHook: init for " + lpparam.packageName);

        hookMainActivity(lpparam);
        hookAsyncStorage(lpparam);
        hookCameraIntents(lpparam);
    }

    private void hookMainActivity(final XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> mainAct = XposedHelpers.findClass("com.harpamobilehr.MainActivity", lpparam.classLoader);
        XposedHelpers.findAndHookMethod(mainAct, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                Activity act = (Activity) param.thisObject;
                XposedBridge.log("RedirectCameraHook: Skipped splash/PIN in MainActivity");
                // Do nothing because bypassed by not launching the splash or pin logic
            }
        });
    }

    private void hookAsyncStorage(final XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> asyncClass = XposedHelpers.findClass(
            "com.reactnativecommunity.asyncstorage.AsyncStorageModule", lpparam.classLoader);

        // 1) multiGet → return cached keys if present
        XposedHelpers.findAndHookMethod(asyncClass, "multiGet",
            ReadableArray.class, Callback.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                Context ctx = (Context) XposedHelpers.callMethod(param.thisObject, "getReactApplicationContext");
                String saved = PreferenceManager.getDefaultSharedPreferences(ctx)
                              .getString(STORAGE_KEY, null);
                if (saved != null) {
                    XposedBridge.log("RedirectCameraHook: AsyncStorage.multiGet → cached");
                    // return empty result so JS continues
                    WritableNativeArray out = new WritableNativeArray();
                    ((Callback) param.args[1]).invoke(out);
                    param.setResult(null);
                }
            }
        });

        // 2) multiSet → persist keys on save
        XposedHelpers.findAndHookMethod(asyncClass, "multiSet",
            ReadableArray.class, Callback.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                ReadableArray pairs = (ReadableArray) param.args[0];
                Context ctx = (Context) XposedHelpers.callMethod(param.thisObject, "getReactApplicationContext");
                PreferenceManager.getDefaultSharedPreferences(ctx)
                    .edit()
                    .putString(STORAGE_KEY, pairs.toString())
                    .apply();
                XposedBridge.log("RedirectCameraHook: AsyncStorage.multiSet → saved");
            }
        });
    }

    private void hookCameraIntents(final XC_LoadPackage.LoadPackageParam lpparam) {
        XC_MethodHook cameraHook = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                Intent orig = (Intent) param.args[0];
                if (orig != null && Intent.ACTION_IMAGE_CAPTURE.equals(orig.getAction())) {
                    XposedBridge.log("RedirectCameraHook: intercept CAMERA intent");
                    orig.setPackage("net.sourceforge.opencamera");
                    orig.putExtra("android.intent.extras.CAMERA_FACING", 1);
                    param.args[0] = orig;
                }
            }
        };

        Class<?> actClass = XposedHelpers.findClass("android.app.Activity", lpparam.classLoader);
        XposedHelpers.findAndHookMethod(actClass, "startActivityForResult",
            Intent.class, int.class, cameraHook);
        XposedHelpers.findAndHookMethod(actClass, "startActivityForResult",
            Intent.class, int.class, Bundle.class, cameraHook);

        XposedHelpers.findAndHookMethod("android.app.Instrumentation", lpparam.classLoader,
            "execStartActivity",
            Context.class, IBinder.class, IBinder.class,
            Activity.class, Intent.class, int.class, Bundle.class,
            cameraHook);
    }
}
