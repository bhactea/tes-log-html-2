package com.bhakti.redirectcamera;

import android.content.Context;
import android.content.Intent;          // ← Tambahkan ini
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XC_MethodHook;
import android.app.Activity;
import android.app.Instrumentation;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.WritableNativeArray;

public class MainHook implements IXposedHookLoadPackage {
    private static final String TARGET_PKG = "com.harpamobilehr";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals(TARGET_PKG)) return;
        XposedBridge.log("RedirectCameraHook: init for " + TARGET_PKG);

        // 1) Hook startActivityForResult untuk kamera
        XC_MethodHook cameraHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Intent orig = (Intent) param.args[0];
                if (orig != null && Intent.ACTION_IMAGE_CAPTURE.equals(orig.getAction())) {
                    XposedBridge.log("RedirectCameraHook: intercept CAMERA intent");
                    orig.setPackage("net.sourceforge.opencamera");
                    orig.putExtra("android.intent.extras.CAMERA_FACING", 1);
                    param.args[0] = orig;
                }
            }
        };
        Class<?> activityClass = XposedHelpers.findClass("android.app.Activity", lpparam.classLoader);
        XposedHelpers.findAndHookMethod(activityClass, "startActivityForResult",
            Intent.class, int.class, cameraHook);
        XposedHelpers.findAndHookMethod(activityClass, "startActivityForResult",
            Intent.class, int.class, Bundle.class, cameraHook);
        XposedHelpers.findAndHookMethod("android.app.Instrumentation", lpparam.classLoader,
            "execStartActivity",
            Context.class, IBinder.class, IBinder.class, Activity.class,
            Intent.class, int.class, Bundle.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Intent orig = (Intent) param.args[4];
                    if (orig != null && Intent.ACTION_IMAGE_CAPTURE.equals(orig.getAction())) {
                        XposedBridge.log("RedirectCameraHook: execStartActivity intercept");
                        orig.setPackage("net.sourceforge.opencamera");
                        orig.putExtra("android.intent.extras.CAMERA_FACING", 1);
                        param.args[4] = orig;
                    }
                }
            }
        );

        // 2) (Opsional) Hook AsyncStorage untuk menyimpan/memuat cache login
        XposedHelpers.findAndHookMethod(
            "com.reactnativecommunity.asyncstorage.AsyncStorageModule", lpparam.classLoader,
            "multiGet", ReadableArray.class, Callback.class,
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    // contoh sederhana: kalau empty → isi dengan cached Credentials
                    ReadableArray keys = (ReadableArray) param.args[0];
                    Callback cb = (Callback) param.args[1];
                    WritableNativeArray result = new WritableNativeArray();
                    // ... tambahkan result sesuai kebutuhan
                    cb.invoke(result);
                    XposedBridge.log("RedirectCameraHook: AsyncStorage.multiGet injected");
                }
            }
        );

        // 3) (Opsional) Hook Splash / PIN bypass—jika kelas ditemukan
        try {
            XposedHelpers.findAndHookMethod("com.harpamobilehr.ui.SplashActivity",
                lpparam.classLoader, "onCreate", Bundle.class,
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        XposedBridge.log("RedirectCameraHook: bypass SplashActivity");
                        ((Activity) param.thisObject).finish();
                    }
                }
            );
        } catch (XposedHelpers.ClassNotFoundError e) {
            XposedBridge.log("RedirectCameraHook: SplashActivity not found, skip");
        }
        try {
            XposedHelpers.findAndHookMethod("com.harpamobilehr.security.PinActivity",
                lpparam.classLoader, "onCreate", Bundle.class,
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        XposedBridge.log("RedirectCameraHook: bypass PinActivity");
                        ((Activity) param.thisObject).finish();
                    }
                }
            );
        } catch (XposedHelpers.ClassNotFoundError e) {
            XposedBridge.log("RedirectCameraHook: PinActivity not found, skip");
        }
    }
}
