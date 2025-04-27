package com.bhakti.redirectcamera;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // Hanya jalankan untuk Harpa
        if (!"com.harpamobilehr".equals(lpparam.packageName)) return;

        ClassLoader cl = lpparam.classLoader;
        Class<?> activityClass = XposedHelpers.findClass("android.app.Activity", cl);

        // Hook semua overload startActivityForResult
        XC_MethodHook hookForResult = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Intent orig = (Intent) param.args[0];
                int requestCode = (int) param.args[1];  // requestCode harus dipertahankan :contentReference[oaicite:6]{index=6}
                if (orig != null && MediaStore.ACTION_IMAGE_CAPTURE.equals(orig.getAction())) {
                    XposedBridge.log("RedirectCameraHook: Intercept startActivityForResult");
                    Intent ni = new Intent(orig);
                    ni.setComponent(new ComponentName(
                        "net.sourceforge.opencamera",
                        "net.sourceforge.opencamera.MainActivity"
                    ));
                    param.args[0] = ni;
                    // param.args[1] = requestCode; // tidak diubah
                    XposedBridge.log("RedirectCameraHook: Camera intent redirected with requestCode " + requestCode);
                }
            }
        };
        // startActivityForResult(Intent, int)
        XposedHelpers.findAndHookMethod(activityClass,
            "startActivityForResult", Intent.class, int.class, hookForResult);
        // startActivityForResult(Intent, int, Bundle)
        XposedHelpers.findAndHookMethod(activityClass,
            "startActivityForResult", Intent.class, int.class, Bundle.class, hookForResult);

        // Juga hook execStartActivity untuk catching semua panggilan internal
        XposedHelpers.findAndHookMethod("android.app.Instrumentation",
            cl,
            "execStartActivity",
            Context.class, IBinder.class, IBinder.class,
            Activity.class, Intent.class, int.class, Bundle.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Intent orig = (Intent) param.args[4];
                    int requestCode = (int) param.args[5];
                    if (orig != null && MediaStore.ACTION_IMAGE_CAPTURE.equals(orig.getAction())) {
                        XposedBridge.log("RedirectCameraHook: execStartActivity intercepted");
                        Intent ni = new Intent(orig);
                        ni.setComponent(new ComponentName(
                            "net.sourceforge.opencamera",
                            "net.sourceforge.opencamera.MainActivity"
                        ));
                        param.args[4] = ni;
                        // param.args[5] = requestCode; // tetap sama
                        XposedBridge.log("RedirectCameraHook: execStartActivity redirected");
                    }
                }
            }
        );
    }
}
