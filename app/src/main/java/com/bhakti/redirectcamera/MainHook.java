package com.bhakti.redirectcamera;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.MediaStore;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XposedBridge;

public class MainHook implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.harpamobilehr")) return;
        XposedBridge.log("RedirectCameraHook: init for " + lpparam.packageName);

        // === 1) Bypass Splash & PIN (contoh sederhana: skip Activity tertentu) ===
        try {
            Class<?> splash = XposedHelpers.findClass(
                "com.harpamobilehr.ui.SplashActivity", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(splash, "onCreate", Bundle.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    ((Activity)param.thisObject).finish();
                    XposedBridge.log("RedirectCameraHook: splash bypassed");
                }
            });
        } catch (XposedHelpers.ClassNotFoundError e) {
            XposedBridge.log("RedirectCameraHook: SplashActivity not found");
        }

        try {
            Class<?> pin = XposedHelpers.findClass(
                "com.harpamobilehr.security.PinActivity", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(pin, "onCreate", Bundle.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    ((Activity)param.thisObject).finish();
                    XposedBridge.log("RedirectCameraHook: pin bypassed");
                }
            });
        } catch (XposedHelpers.ClassNotFoundError e) {
            XposedBridge.log("RedirectCameraHook: PinActivity not found");
        }

        // === 2) Hook startActivityForResult untuk kamera ===
        XC_MethodHook cameraHook = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                Intent orig = (Intent) param.args[0];
                if (orig != null && MediaStore.ACTION_IMAGE_CAPTURE.equals(orig.getAction())) {
                    XposedBridge.log("RedirectCameraHook: intercept CAMERA intent");
                    orig.setPackage("net.sourceforge.opencamera");
                    orig.putExtra("android.intent.extras.CAMERA_FACING", 1);
                    param.args[0] = orig;
                }
            }
        };

        // Hook metode Activity.startActivityForResult(...)
        Class<?> activityClass = XposedHelpers.findClass("android.app.Activity", lpparam.classLoader);
        XposedHelpers.findAndHookMethod(activityClass,
            "startActivityForResult",
            Intent.class, int.class, cameraHook);
        XposedHelpers.findAndHookMethod(activityClass,
            "startActivityForResult",
            Intent.class, int.class, Bundle.class, cameraHook);

        // Hook execStartActivity di Instrumentation
        XposedHelpers.findAndHookMethod(
            "android.app.Instrumentation",
            lpparam.classLoader,
            "execStartActivity",
            Activity.class, Intent.class, int.class, Bundle.class,
            new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    Intent orig = (Intent) param.args[1];
                    if (orig != null && MediaStore.ACTION_IMAGE_CAPTURE.equals(orig.getAction())) {
                        XposedBridge.log("RedirectCameraHook: execStartActivity intercept");
                        orig.setPackage("net.sourceforge.opencamera");
                        orig.putExtra("android.intent.extras.CAMERA_FACING", 1);
                        param.args[1] = orig;
                    }
                }
            });
    }
}
