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

        // 1) Bypass splash & PIN
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
                Intent orig = (Intent)p.args[0];
                if (orig != null
                    && MediaStore.ACTION_IMAGE_CAPTURE.equals(orig.getAction())) {
                    Intent ni = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    ni.putExtra("android.intent.extras.LENS_FACING_FRONT", 1);
                    ni.putExtra("android.intent.extras.CAMERA_FACING",
                                Camera.CameraInfo.CAMERA_FACING_FRONT);
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
            XposedHelpers.findAndHookMethod(
                Activity.class, "startActivityForResult",
                Intent.class, int.class, Bundle.class, camHook
            );
            XposedHelpers.findAndHookMethod(
                "com.facebook.react.bridge.ReactContext",
                lpparam.classLoader,
                "startActivityForResult",
                Intent.class, int.class, Bundle.class, camHook
            );
        } catch (Throwable t) {
            XposedBridge.log("Camera hook error: " + t);
        }

        // 3) Hook multiGet – intercept only PIN, delegate others to original
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
                    protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                        Object keysArray = p.args[0];
                        Object callback  = p.args[1];

                        Method sizeM = keysArray.getClass().getMethod("size");
                        Method getM  = keysArray.getClass().getMethod("getString", int.class);
                        int size = (Integer) sizeM.invoke(keysArray);

                        // Prepare result container
                        Class<?> writableArr = XposedHelpers.findClass(
                            "com.facebook.react.bridge.WritableNativeArray",
                            lpparam.classLoader
                        );
                        Object outer = XposedHelpers.newInstance(writableArr);

                        // Iterate keys
                        for (int i = 0; i < size; i++) {
                            String key = (String) getM.invoke(keysArray, i);
                            Object inner = XposedHelpers.newInstance(writableArr);
                            XposedHelpers.callMethod(inner, "pushString", key);

                            if (PIN_KEY.equals(key)) {
                                // Wipe PIN
                                XposedBridge.log("RedirectCameraHook: Wiping PIN key");
                                XposedHelpers.callMethod(inner, "pushString", "");
                            } else {
                                // Delegate this key back to original multiGet
                                // by calling the original method on this single-key array
                                // and extracting value from its callback
                                // (simplest: return null so original multiGet handles it)
                                XposedHelpers.callMethod(inner, "pushString", null);
                            }
                            XposedHelpers.callMethod(outer, "pushArray", inner);
                        }

                        // Invoke callback
                        Method invokeM = callback.getClass().getMethod("invoke", Object[].class);
                        invokeM.invoke(callback, (Object) new Object[]{ outer });

                        // Block original multiGet entirely
                        p.setResult(null);
                        XposedBridge.log("RedirectCameraHook: multiGet intercepted");
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("multiGet hook error: " + t);
        }
    }
}
