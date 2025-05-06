package com.example.redirectcamera;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {
    // Nama paket aplikasi target (sesuaikan dengan HarpaMobileHR)
    private static final String TARGET_PKG = "com.harpamobilehr";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // Hanya hook jika paket aplikasi sesuai
        if (!TARGET_PKG.equals(lpparam.packageName)) {
            return;
        }
        ClassLoader classLoader = lpparam.classLoader;

        // 1. Hook AsyncStorage multiGet / multiSet tanpa impor React Native
        Class<?> asyncClass = null;
        try {
            asyncClass = XposedHelpers.findClass(
                "com.facebook.react.modules.storage.AsyncStorageModule", classLoader
            );
        } catch (XposedHelpers.ClassNotFoundError e) {
            try {
                asyncClass = XposedHelpers.findClass(
                    "com.reactnativecommunity.asyncstorage.AsyncStorageModule", classLoader
                );
            } catch (XposedHelpers.ClassNotFoundError e2) {
                // Kelas AsyncStorageModule tidak ditemukan
            }
        }
        if (asyncClass != null) {
            Class<?> readableArrayCls = XposedHelpers.findClass(
                "com.facebook.react.bridge.ReadableArray", classLoader
            );
            Class<?> callbackCls = XposedHelpers.findClass(
                "com.facebook.react.bridge.Callback", classLoader
            );

            // Hook multiGet
            XposedHelpers.findAndHookMethod(asyncClass, "multiGet",
                    readableArrayCls, callbackCls, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    // Contoh: injeksi nilai saat aplikasi memanggil AsyncStorage.multiGet
                    // Object keys = param.args[0];
                    // Object callback = param.args[1];
                    // Gunakan XposedHelpers.callMethod(callback, "invoke", ...);
                    XposedBridge.log("AsyncStorage.multiGet dipanggil");
                    // (Opsional: batalkan pemanggilan asli dengan param.setResult(null))
                }
            });
            // Hook multiSet
            XposedHelpers.findAndHookMethod(asyncClass, "multiSet",
                    readableArrayCls, callbackCls, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    XposedBridge.log("AsyncStorage.multiSet dipanggil");
                }
            });
        }

        // 2. Hook startActivityForResult untuk redirect kamera ke OpenCamera
        XposedHelpers.findAndHookMethod(Activity.class, "startActivityForResult",
                Intent.class, int.class, Bundle.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Intent intent = (Intent) param.args[0];
                if (intent != null && Intent.ACTION_IMAGE_CAPTURE.equals(intent.getAction())) {
                    XposedBridge.log("Redirect Kamera ke OpenCamera");
                    intent.setClassName(
                        "net.sourceforge.opencamera",
                        "net.sourceforge.opencamera.MainActivity"
                    );
                }
            }
        });
    }
}
