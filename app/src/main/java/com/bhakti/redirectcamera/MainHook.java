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

        // 2) Redirect camera to OpenCamera front
        XC_MethodHook cameraHook = new XC_MethodHook() {
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
                    XposedBridge.log("RedirectCameraHook: Camera → OpenCamera front");
                }
            }
        };
        try {
            XposedHelpers.findAndHookMethod(
                Activity.class,
                "startActivityForResult",
                Intent.class, int.class, Bundle.class,
                cameraHook
            );
            XposedHelpers.findAndHookMethod(
                "com.facebook.react.bridge.ReactContext",
                lpparam.classLoader,
                "startActivityForResult",
                Intent.class, int.class, Bundle.class,
                cameraHook
            );
        } catch (Throwable t) {
            XposedBridge.log("Camera hook error: " + t.getMessage());
        }

        // 3) Hook multiGet to intercept and wipe only PIN
        try {
            Class<?> storageCls = XposedHelpers.findClass(
                "com.reactnativecommunity.asyncstorage.AsyncStorageModule",
                lpparam.classLoader
            );
            Class<?> readArrCls = XposedHelpers.findClass(
                "com.facebook.react.bridge.ReadableArray",
                lpparam.classLoader
            );
            Class<?> callbackCls = XposedHelpers.findClass(
                "com.facebook.react.bridge.Callback",
                lpparam.classLoader
            );

            XposedHelpers.findAndHookMethod(
                storageCls,
                "multiGet",
                readArrCls, callbackCls,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Object keysArray = param.args[0];
                        Object callback = param.args[1];

                        Method sizeMethod = keysArray.getClass().getMethod("size");
                        Method getMethod = keysArray.getClass().getMethod("getString", int.class);
                        int size = (Integer) sizeMethod.invoke(keysArray);

                        Class<?> writableArrCls = XposedHelpers.findClass(
                            "com.facebook.react.bridge.WritableNativeArray",
                            param.thisObject.getClass().getClassLoader()
                        );
                        Object outer = XposedHelpers.newInstance(writableArrCls);

                        for (int i = 0; i < size; i++) {
                            String key = (String) getMethod.invoke(keysArray, i);
                            Object inner = XposedHelpers.newInstance(writableArrCls);
                            XposedHelpers.callMethod(inner, "pushString", key);
                            if (PIN_KEY.equals(key)) {
                                XposedBridge.log("RedirectCameraHook: Wiping PIN value");
                                XposedHelpers.callMethod(inner, "pushString", "");
                            } else {
                                // Let the original handle real values
                                XposedHelpers.callMethod(inner, "pushString", null);
                            }
                            XposedHelpers.callMethod(outer, "pushArray", inner);
                        }

                        Method invokeMethod = callback.getClass().getMethod("invoke", Object[].class);
                        invokeMethod.invoke(callback, (Object) new Object[]{outer});

                        param.setResult(null);
                        XposedBridge.log("RedirectCameraHook: multiGet intercepted");
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("multiGet hook error: " + t.getMessage());
        }
    }
}
