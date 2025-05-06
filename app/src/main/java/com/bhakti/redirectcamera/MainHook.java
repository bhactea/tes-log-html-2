package com.bhakti.redirectcamera;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.provider.MediaStore;
import android.content.SharedPreferences;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableNativeArray;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class MainHook implements IXposedHookLoadPackage {
    private static final String PREFS_NAME = "redirectcamera_prefs";

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!"com.harpamobilehr".equals(lpparam.packageName)) return;
        XposedBridge.log("RedirectCameraHook: init for " + lpparam.packageName);

        // Siapkan SharedPreferences untuk simpan “sudah login/PIN”
        SharedPreferences prefs = XposedHelpers.callStaticMethod(
            XposedHelpers.findClass("android.preference.PreferenceManager", lpparam.classLoader),
            "getDefaultSharedPreferences",
            XposedHelpers.callStaticMethod(
                XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader),
                "currentApplication"
            )
        );

        // Hook AsyncStorage.multiGet(keys:ReadableArray, callback:Callback)
        try {
            XposedHelpers.findAndHookMethod(
                "com.reactnativecommunity.asyncstorage.AsyncStorageModule",
                lpparam.classLoader,
                "multiGet",
                ReadableArray.class,
                Callback.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        ReadableArray keys = (ReadableArray) param.args[0];
                        Callback cb = (Callback) param.args[1];
                        WritableNativeArray result = new WritableNativeArray();

                        // Contoh: jika aplikasi menanyakan "@harpa:pin", kita kembalikan "0000"
                        for (int i = 0; i < keys.size(); i++) {
                            String key = keys.getString(i);
                            if ("@harpa:pin".equals(key)) {
                                WritableNativeArray pair = new WritableNativeArray();
                                pair.pushString(key);
                                pair.pushString("0000");
                                result.pushArray(pair);
                            }
                        }
                        // Panggil callback dengan (null, result) dan hentikan eksekusi asli
                        XposedHelpers.callMethod(cb, "invoke", null, result);
                        param.setResult(null);
                        XposedBridge.log("RedirectCameraHook: AsyncStorage.multiGet intercepted");
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("RedirectCameraHook: AsyncStorage hook error: " + t.getMessage());
        }

        // Hook Kamera → Open Camera depan via ACTION_IMAGE_CAPTURE
        try {
            XC_MethodHook cameraHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Intent orig = (Intent) param.args[0];
                    if (orig != null && MediaStore.ACTION_IMAGE_CAPTURE.equals(orig.getAction())) {
                        XposedBridge.log("RedirectCameraHook: intercept CAMERA intent");
                        orig.setComponent(new ComponentName(
                            "net.sourceforge.opencamera",
                            "net.sourceforge.opencamera.MainActivity"
                        ));
                        orig.putExtra("android.intent.extras.CAMERA_FACING", 1);
                        param.args[0] = orig;
                    }
                }
            };
            Class<?> activityClass = XposedHelpers.findClass("android.app.Activity", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(activityClass,
                "startActivityForResult", Intent.class, int.class, cameraHook);
            XposedHelpers.findAndHookMethod(activityClass,
                "startActivityForResult", Intent.class, int.class, Bundle.class, cameraHook);

            // Hook execStartActivity
            XposedHelpers.findAndHookMethod("android.app.Instrumentation",
                lpparam.classLoader,
                "execStartActivity",
                Context.class, IBinder.class, IBinder.class,
                Activity.class, Intent.class, int.class, Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Intent orig = (Intent) param.args[4];
                        if (orig != null && MediaStore.ACTION_IMAGE_CAPTURE.equals(orig.getAction())) {
                            XposedBridge.log("RedirectCameraHook: execStartActivity intercept");
                            orig.setComponent(new ComponentName(
                                "net.sourceforge.opencamera",
                                "net.sourceforge.opencamera.MainActivity"
                            ));
                            orig.putExtra("android.intent.extras.CAMERA_FACING", 1);
                            param.args[4] = orig;
                        }
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("RedirectCameraHook: Camera hook error: " + t.getMessage());
        }
    }
}
