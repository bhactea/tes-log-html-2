package com.bhakti.redirectcamera;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.hardware.Camera;
import android.os.Bundle;
import android.provider.MediaStore;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class MainHook implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        // 0) Pastikan hanya untuk HarpaMobileHR
        if (!"com.harpamobilehr".equals(lpparam.packageName)) return;
        XposedBridge.log("RedirectCameraHook: init for " + lpparam.packageName);

        // 1) Bypass splash & PIN di MainActivity
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

        // 2a) Redirect kamera via Activity.startActivityForResult
        try {
            XC_MethodHook camHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Intent orig = (Intent) param.args[0];
                    if (orig != null && MediaStore.ACTION_IMAGE_CAPTURE.equals(orig.getAction())) {
                        Intent ni = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        // Standard extras front camera :contentReference[oaicite:0]{index=0}
                        ni.putExtra("android.intent.extras.LENS_FACING_FRONT", 1);
                        ni.putExtra("android.intent.extras.CAMERA_FACING", Camera.CameraInfo.CAMERA_FACING_FRONT);
                        ni.putExtra("android.intent.extra.USE_FRONT_CAMERA", true);
                        // OpenCamera-specific component :contentReference[oaicite:1]{index=1}
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

        // 2b) Redirect kamera via ReactContext.startActivityForResult
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

        // 3) Hook AsyncStorage.multiGet untuk return [key,""] pairs
        try {
            Class<?> storageClass = XposedHelpers.findClass(
                "com.reactnativecommunity.asyncstorage.AsyncStorageModule",
                lpparam.classLoader
            );
            Class<?> readableArrayClass = XposedHelpers.findClass(
                "com.facebook.react.bridge.ReadableArray",
                lpparam.classLoader
            );
            Class<?> callbackClass = XposedHelpers.findClass(
                "com.facebook.react.bridge.Callback",
                lpparam.classLoader
            );
            Class<?> writableArrayClass = XposedHelpers.findClass(
                "com.facebook.react.bridge.WritableNativeArray",
                lpparam.classLoader
            );

            XposedHelpers.findAndHookMethod(
                storageClass,
                "multiGet",
                readableArrayClass, callbackClass,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Object keysArray = param.args[0];
                        Object callback = param.args[1];

                        // Build outer array
                        Object outer = XposedHelpers.newInstance(writableArrayClass);

                        Method sizeM = keysArray.getClass().getMethod("size");
                        Method getM = keysArray.getClass().getMethod("getString", int.class);
                        int size = (Integer) sizeM.invoke(keysArray);

                        for (int i = 0; i < size; i++) {
                            String key = (String) getM.invoke(keysArray, i);
                            Object inner = XposedHelpers.newInstance(writableArrayClass);
                            XposedHelpers.callMethod(inner, "pushString", key);
                            XposedHelpers.callMethod(inner, "pushString", "");
                            XposedHelpers.callMethod(outer, "pushArray", inner);
                        }

                        // Correct varargs reflection call 
                        Method invokeM = callback.getClass().getMethod("invoke", Object[].class);
                        invokeM.invoke(callback, (Object) new Object[]{outer});

                        param.setResult(null);
                        XposedBridge.log("RedirectCameraHook: AsyncStorage.multiGet returned empty values");
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("AsyncStorage hook error: " + t.getMessage());
        }
    }
}
