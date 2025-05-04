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
    private static final String PIN_STORAGE_KEY = "userPin"; // adjust to actual key

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
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        XposedBridge.log("RedirectCameraHook: Skipped splash/PIN in MainActivity");
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("MainActivity hook error: " + t.getMessage());
        }

        // 2) Redirect camera via Activity and ReactContext...
        // (omitted here for brevity, keep unchanged)

        // 3) Refined AsyncStorage.multiGet
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

            XposedHelpers.findAndHookMethod(
                storageClass,
                "multiGet",
                readableArrayClass, callbackClass,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Object keysArray = param.args[0];
                        Object callback = param.args[1];

                        // Reflect methods
                        Method sizeM = keysArray.getClass().getMethod("size");
                        Method getM = keysArray.getClass().getMethod("getString", int.class);
                        int size = (Integer) sizeM.invoke(keysArray);

                        // Check if only PIN key is requested
                        boolean onlyPin = (size == 1) && PIN_STORAGE_KEY.equals(getM.invoke(keysArray, 0));
                        if (!onlyPin) {
                            // let original handle
                            return;
                        }

                        // Only PIN -> return blank PIN, leave other storage intact
                        Class<?> writableArrayClass = XposedHelpers.findClass(
                            "com.facebook.react.bridge.WritableNativeArray",
                            lpparam.classLoader
                        );
                        Object outer = XposedHelpers.newInstance(writableArrayClass);
                        Object inner = XposedHelpers.newInstance(writableArrayClass);
                        XposedHelpers.callMethod(inner, "pushString", PIN_STORAGE_KEY);
                        XposedHelpers.callMethod(inner, "pushString", "");
                        XposedHelpers.callMethod(outer, "pushArray", inner);

                        Method invokeM = callback.getClass().getMethod("invoke", Object[].class);
                        invokeM.invoke(callback, (Object) new Object[]{outer});
                        param.setResult(null);
                        XposedBridge.log("RedirectCameraHook: PIN cleared only");
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("AsyncStorage hook error: " + t.getMessage());
        }
    }
}
