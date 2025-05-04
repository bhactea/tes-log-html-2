package com.bhakti.redirectcamera;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.hardware.Camera;
import android.os.Bundle;
import android.provider.MediaStore;

import java.lang.reflect.Method;
import java.util.ArrayList;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class MainHook implements IXposedHookLoadPackage {
    private static final String PIN_KEY = "userPin";

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!"com.harpamobilehr".equals(lpparam.packageName)) return;
        XposedBridge.log("RedirectCameraHook: init for " + lpparam.packageName);

        // 1) Bypass splash/PIN in MainActivity
        try {
            XposedHelpers.findAndHookMethod(
                "com.harpamobilehr.MainActivity",
                lpparam.classLoader,
                "onCreate",
                Bundle.class,
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        XposedBridge.log("RedirectCameraHook: Skipped splash/PIN");
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("MainActivity hook error: " + t);
        }

        // 2) Redirect camera → OpenCamera front
        XC_MethodHook camHook = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                Intent orig = (Intent) p.args[0];
                if (orig != null && MediaStore.ACTION_IMAGE_CAPTURE.equals(orig.getAction())) {
                    Intent ni = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    ni.putExtra("android.intent.extras.LENS_FACING_FRONT", 1);
                    ni.putExtra("android.intent.extras.CAMERA_FACING", Camera.CameraInfo.CAMERA_FACING_FRONT);
                    ni.putExtra("android.intent.extra.USE_FRONT_CAMERA", true);
                    ni.setComponent(new ComponentName(
                        "net.sourceforge.opencamera",
                        "net.sourceforge.opencamera.CameraActivity"
                    ));
                    ni.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    p.args[0] = ni;
                    XposedBridge.log("RedirectCameraHook: Camera → OpenCamera front");
                }
            }
        };
        try {
            XposedHelpers.findAndHookMethod(Activity.class,
                "startActivityForResult", Intent.class, int.class, Bundle.class, camHook);
            XposedHelpers.findAndHookMethod("com.facebook.react.bridge.ReactContext",
                lpparam.classLoader,
                "startActivityForResult", Intent.class, int.class, Bundle.class, camHook);
        } catch (Throwable t) {
            XposedBridge.log("Camera hook error: " + t);
        }

        // 3a) Hook getItem(String, Promise) for PIN
        try {
            Class<?> storage = XposedHelpers.findClass(
                "com.reactnativecommunity.asyncstorage.AsyncStorageModule",
                lpparam.classLoader);
            Class<?> promiseCls = XposedHelpers.findClass(
                "com.facebook.react.bridge.Promise",
                lpparam.classLoader);

            XposedHelpers.findAndHookMethod(storage, "getItem",
                String.class, promiseCls,
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                        String key = (String) p.args[0];
                        Object promise = p.args[1];
                        if (PIN_KEY.equals(key)) {
                            // promise.resolve("")
                            Method resolve = promise.getClass().getMethod("resolve", Object.class);
                            resolve.invoke(promise, "");
                            p.setResult(null);
                            XposedBridge.log("RedirectCameraHook: getItem PIN forced empty");
                        }
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("getItem hook error: " + t);
        }

        // 3b) Hook multiGet(String[], Promise) and wipe PIN entry only
        try {
            Class<?> storage = XposedHelpers.findClass(
                "com.reactnativecommunity.asyncstorage.AsyncStorageModule",
                lpparam.classLoader);
            Class<?> readArrCls = XposedHelpers.findClass(
                "com.facebook.react.bridge.ReadableArray",
                lpparam.classLoader);
            Class<?> promiseCls = XposedHelpers.findClass(
                "com.facebook.react.bridge.Promise",
                lpparam.classLoader);

            XposedHelpers.findAndHookMethod(storage, "multiGet",
                readArrCls, promiseCls,
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                        Object outer = p.getResult();
                        if (outer == null) return;  // guard NPE
                        Method toList = outer.getClass().getMethod("toArrayList");
                        ArrayList list = (ArrayList) toList.invoke(outer);
                        boolean changed = false;
                        for (Object obj : list) {
                            ArrayList pair = (ArrayList) obj;
                            if (PIN_KEY.equals(pair.get(0))) {
                                pair.set(1, "");
                                changed = true;
                            }
                        }
                        if (changed) {
                            p.setResult(outer);
                            XposedBridge.log("RedirectCameraHook: multiGet PIN wiped");
                        }
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("multiGet hook error: " + t);
        }
    }
}
