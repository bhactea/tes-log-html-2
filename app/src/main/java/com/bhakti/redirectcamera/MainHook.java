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
        // Hanya untuk aplikasi Harpa
        if (!"com.harpamobilehr".equals(lpparam.packageName)) return;

        // Hook semua overload startActivity dan startActivityForResult
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Intent orig = (Intent) param.args[0];
                if (orig != null && MediaStore.ACTION_IMAGE_CAPTURE.equals(orig.getAction())) {
                    XposedBridge.log("RedirectCameraHook: CAMERA intent intercepted");
                    Intent ni = new Intent();
                    ni.setComponent(new ComponentName(
                        "net.sourceforge.opencamera",
                        "net.sourceforge.opencamera.MainActivity"
                    ));
                    param.args[0] = ni;
                    XposedBridge.log("RedirectCameraHook: Redirected to Open Camera");
                }
            }
        };

        ClassLoader cl = lpparam.classLoader;
        Class<?> activityClass = XposedHelpers.findClass("android.app.Activity", cl);

        // startActivity(Intent)
        XposedHelpers.findAndHookMethod(activityClass,
            "startActivity", Intent.class, hook);

        // startActivity(Intent, Bundle)
        XposedHelpers.findAndHookMethod(activityClass,
            "startActivity", Intent.class, Bundle.class, hook);

        // startActivityForResult(Intent, int)
        XposedHelpers.findAndHookMethod(activityClass,
            "startActivityForResult", Intent.class, int.class, hook);

        // startActivityForResult(Intent, int, Bundle)
        XposedHelpers.findAndHookMethod(activityClass,
            "startActivityForResult", Intent.class, int.class, Bundle.class, hook);

        // Juga hook Instrumentation.execStartActivity
        XposedHelpers.findAndHookMethod("android.app.Instrumentation",
            cl,
            "execStartActivity",
            Context.class, IBinder.class, IBinder.class,
            Activity.class, Intent.class, int.class, Bundle.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Intent orig = (Intent) param.args[4];  // Intent ada di index 4
                    if (orig != null && MediaStore.ACTION_IMAGE_CAPTURE.equals(orig.getAction())) {
                        XposedBridge.log("RedirectCameraHook: execStartActivity intercepted");
                        Intent ni = new Intent();
                        ni.setComponent(new ComponentName(
                            "net.sourceforge.opencamera",
                            "net.sourceforge.opencamera.MainActivity"
                        ));
                        param.args[4] = ni;
                        XposedBridge.log("RedirectCameraHook: execStartActivity redirected");
                    }
                }
            }
        );
    }
}
