package com.bhakti.redirectcamera;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.provider.MediaStore;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class MainHook implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        // Hanya untuk aplikasi HarpaMobileHR
        if (!"com.harpamobilehr".equals(lpparam.packageName)) return;
        XposedBridge.log("RedirectCameraHook: init for " + lpparam.packageName);

        // 1) Bypass Splash & PIN di MainActivity
        try {
            XposedHelpers.findAndHookMethod(
                "com.harpamobilehr.MainActivity",
                lpparam.classLoader,
                "onCreate",
                Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        // Di sini kita bisa melewatkan inisialisasi splash/PIN
                        XposedBridge.log("RedirectCameraHook: Skipped splash/PIN in MainActivity");
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("RedirectCameraHook: MainActivity hook error: " + t.getMessage());
        }

        // 2) Redirect Kamera Bawaan ke Open Camera (front)
        try {
            XC_MethodHook camHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Intent orig = (Intent) param.args[0];
                    if (orig != null && MediaStore.ACTION_IMAGE_CAPTURE.equals(orig.getAction())) {
                        Intent ni = new Intent(orig);
                        ni.setComponent(new ComponentName(
                            "net.sourceforge.opencamera",
                            "net.sourceforge.opencamera.MainActivity"
                        ));
                        ni.putExtra("net.sourceforge.opencamera.use_front", true);
                        param.args[0] = ni;
                        XposedBridge.log("RedirectCameraHook: Camera redirected to OpenCamera front");
                    }
                }
            };
            XposedHelpers.findAndHookMethod(
                Activity.class,
                "startActivityForResult",
                Intent.class,
                int.class,
                Bundle.class,
                camHook
            );
        } catch (Throwable t) {
            XposedBridge.log("RedirectCameraHook: Camera hook error: " + t.getMessage());
        }

        // 3) Hook AsyncStorage.multiGet di com.reactnativecommunity.asyncstorage.AsyncStorageModule
        try {
            Class<?> storageClass = XposedHelpers.findClass(
                "com.reactnativecommunity.asyncstorage.AsyncStorageModule",
                lpparam.classLoader
            );
            Class<?> readableArray = XposedHelpers.findClass(
                "com.facebook.react.bridge.ReadableArray",
                lpparam.classLoader
            );
            Class<?> callback = XposedHelpers.findClass(
                "com.facebook.react.bridge.Callback",
                lpparam.classLoader
            );
            XposedHelpers.findAndHookMethod(
                storageClass,
                "multiGet",
                readableArray,
                callback,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Object cb = param.args[1];
                        // Buat array kosong via reflection
                        Class<?> writableArray = XposedHelpers.findClass(
                            "com.facebook.react.bridge.WritableNativeArray",
                            lpparam.classLoader
                        );
                        Object empty = XposedHelpers.newInstance(writableArray);
                        XposedHelpers.callMethod(cb, "invoke", empty);
                        param.setResult(null);
                        XposedBridge.log("RedirectCameraHook: AsyncStorage.multiGet overridden");
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("RedirectCameraHook: AsyncStorage hook error: " + t.getMessage());
        }
    }
}
