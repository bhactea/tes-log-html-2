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
    private static final String PIN_STORAGE_KEY = "userPin";

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!"com.harpamobilehr".equals(lpparam.packageName)) return;
        XposedBridge.log("RedirectCameraHook: init for " + lpparam.packageName);

        // 1) Bypass splash & PIN in MainActivity
        try {
            XposedHelpers.findAndHookMethod(
                "com.harpamobilehr.MainActivity",
                lpparam.classLoader,
                "onCreate",
                Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        XposedBridge.log("RedirectCameraHook: Skipped splash/PIN in MainActivity");
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("MainActivity hook error: " + t.getMessage());
        }

        // 2a) Redirect camera via Activity.startActivityForResult
        try {
            XC_MethodHook camHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Intent orig = (Intent) param.args[0];
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
                        param.args[0] = ni;
                        XposedBridge.log("RedirectCameraHook: (Activity) Camera → OpenCamera front");
                    }
                }
            };
            XposedHelpers.findAndHookMethod(
                Activity.class,
                "startActivityForResult",
                Intent.class, int.class, Bundle.class,
                camHook
            );
        } catch (Throwable t) {
            XposedBridge.log("Activity camera hook error: " + t.getMessage());
        }

        // 2b) Redirect camera via ReactContext.startActivityForResult
        try {
            XposedHelpers.findAndHookMethod(
                "com.facebook.react.bridge.ReactContext",
                lpparam.classLoader,
                "startActivityForResult",
                Intent.class, int.class, Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Intent orig = (Intent) param.args[0];
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
                            param.args[0] = ni;
                            XposedBridge.log("RedirectCameraHook: (ReactContext) Camera → OpenCamera front");
                        }
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("ReactContext camera hook error: " + t.getMessage());
        }

        // 3a) Hook getItem for PIN only
        try {
            XposedHelpers.findAndHookMethod(
                "com.reactnativecommunity.asyncstorage.AsyncStorageModule",
                lpparam.classLoader,
                "getItem",
                String.class,
                XposedHelpers.findClass("com.facebook.react.bridge.Callback", lpparam.classLoader),
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        String key = (String) param.args[0];
                        Object cb = param.args[1];
                        if (PIN_STORAGE_KEY.equals(key)) {
                            Method invokeM = cb.getClass().getMethod("invoke", Object[].class);
                            invokeM.invoke(cb, (Object) new Object[]{""});
                            param.setResult(null);
                            XposedBridge.log("RedirectCameraHook: getItem PIN overridden");
                        }
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("AsyncStorage getItem hook error: " + t.getMessage());
        }

        // 3b) Hook multiGet to wipe only PIN in arrays
        try {
            Class<?> storageCls = XposedHelpers.findClass(
                "com.reactnativecommunity.asyncstorage.AsyncStorageModule",
                lpparam.classLoader
            );
            Class<?> readArrCls = XposedHelpers.findClass(
                "com.facebook.react.bridge.ReadableArray",
                lpparam.classLoader
            );
            Class<?> cbCls = XposedHelpers.findClass(
                "com.facebook.react.bridge.Callback",
                lpparam.classLoader
            );

            XposedHelpers.findAndHookMethod(
                storageCls,
                "multiGet",
                readArrCls, cbCls,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object outer = param.getResult();
                        Method toList = outer.getClass().getMethod("toArrayList");
                        ArrayList list = (ArrayList) toList.invoke(outer);
                        boolean modified = false;
                        for (Object pairObj : list) {
                            ArrayList pair = (ArrayList) pairObj;
                            if (PIN_STORAGE_KEY.equals(pair.get(0))) {
                                pair.set(1, "");
                                modified = true;
                            }
                        }
                        if (modified) {
                            param.setResult(outer);
                            XposedBridge.log("RedirectCameraHook: multiGet PIN wiped only");
                        }
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("AsyncStorage multiGet hook error: " + t.getMessage());
        }
    }
}
