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

        hookMainActivity(lpparam);
        hookCameraRedirection(lpparam);
        hookAsyncStorageMultiGet(lpparam);
    }

    private void hookMainActivity(LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.harpamobilehr.MainActivity",
                lpparam.classLoader,
                "onCreate",
                Bundle.class,
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        XposedBridge.log("RedirectCameraHook: Skipped splash/PIN in MainActivity");
                    }
                }
            );
        } catch(Throwable t) {
            XposedBridge.log("MainActivity hook failed: " + t);
        }
    }

    private void hookCameraRedirection(LoadPackageParam lpparam) {
        XC_MethodHook camHook = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
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
                    XposedBridge.log("RedirectCameraHook: Camera redirected to OpenCamera front");
                }
            }
        };
        try {
            XposedHelpers.findAndHookMethod(
                Activity.class, "startActivityForResult",
                Intent.class, int.class, Bundle.class,
                camHook
            );
            XposedHelpers.findAndHookMethod(
                "com.facebook.react.bridge.ReactContext",
                lpparam.classLoader,
                "startActivityForResult",
                Intent.class, int.class, Bundle.class,
                camHook
            );
        } catch(Throwable t) {
            XposedBridge.log("Camera hook failed: " + t);
        }
    }

    private void hookAsyncStorageMultiGet(LoadPackageParam lpparam) {
        String[] possibleClasses = new String[]{
            "com.reactnativecommunity.asyncstorage.AsyncStorageModule",
            "com.facebook.react.modules.storage.AsyncStorageModule"
        };
        for (String clsName : possibleClasses) {
            try {
                Class<?> storageCls = XposedHelpers.findClass(clsName, lpparam.classLoader);
                Class<?> readArrCls = XposedHelpers.findClass(
                    "com.facebook.react.bridge.ReadableArray", lpparam.classLoader
                );
                Class<?> callbackCls = XposedHelpers.findClass(
                    "com.facebook.react.bridge.Callback", lpparam.classLoader
                );

                XposedHelpers.findAndHookMethod(
                    storageCls,
                    "multiGet",
                    readArrCls, callbackCls,
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            XposedBridge.log("RedirectCameraHook: multiGet hook triggered on " + clsName);
                            Object keysArray = param.args[0];
                            Object callback = param.args[1];

                            Method sizeM = keysArray.getClass().getMethod("size");
                            Method getM  = keysArray.getClass().getMethod("getString", int.class);
                            int size     = (Integer) sizeM.invoke(keysArray);

                            Class<?> writableArrCls = XposedHelpers.findClass(
                                "com.facebook.react.bridge.WritableNativeArray", lpparam.classLoader
                            );
                            Object outer = XposedHelpers.newInstance(writableArrCls);

                            for (int i = 0; i < size; i++) {
                                String key = (String) getM.invoke(keysArray, i);
                                Object inner = XposedHelpers.newInstance(writableArrCls);
                                XposedHelpers.callMethod(inner, "pushString", key);
                                if (PIN_KEY.equals(key)) {
                                    XposedBridge.log("RedirectCameraHook: wiping PIN for key " + key);
                                    XposedHelpers.callMethod(inner, "pushString", "");
                                } else {
                                    // leave value undefined: pass null
                                    XposedHelpers.callMethod(inner, "pushString", (Object) null);
                                }
                                XposedHelpers.callMethod(outer, "pushArray", inner);
                            }

                            Method invoke = callback.getClass().getMethod("invoke", Object[].class);
                            invoke.invoke(callback, (Object) new Object[]{outer});
                            param.setResult(null);
                            XposedBridge.log("RedirectCameraHook: multiGet intercepted and callback invoked");
                        }
                    }
                );
                // if found and hooked, break loop
                break;
            } catch (Throwable t) {
                XposedBridge.log("AsyncStorage multiGet hook not found in " + clsName + ": " + t.getMessage());
            }
        }
    }
}
