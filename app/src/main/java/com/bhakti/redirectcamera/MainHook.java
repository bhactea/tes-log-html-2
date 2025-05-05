package com.bhakti.redirectcamera;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.hardware.Camera;
import android.os.Bundle;
import android.provider.MediaStore;

import java.util.ArrayList;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class MainHook implements IXposedHookLoadPackage {
    private static final String PIN_KEY = "userPin";
    private static final String DEFAULT_PIN = "0000";

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!"com.harpamobilehr".equals(lpparam.packageName)) return;
        XposedBridge.log("RedirectCameraHook: init for " + lpparam.packageName);

        hookSplashBypass(lpparam);
        hookCameraRedirect(lpparam);
        hookCallbackInvoke(lpparam);
    }

    private void hookSplashBypass(LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.harpamobilehr.MainActivity",
                lpparam.classLoader,
                "onCreate",
                Bundle.class,
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        XposedBridge.log("RedirectCameraHook: Skipped splash/PIN");
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("Splash bypass hook failed: " + t);
        }
    }

    private void hookCameraRedirect(LoadPackageParam lpparam) {
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
        } catch (Throwable t) {
            XposedBridge.log("Camera hook failed: " + t);
        }
    }

    private void hookCallbackInvoke(LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.facebook.react.bridge.CallbackImpl",
                lpparam.classLoader,
                "invoke",
                Object[].class,
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        Object[] args = (Object[]) param.args[0];
                        if (args != null && args.length > 0 && args[0] != null) {
                            Object first = args[0];
                            if (first.getClass().getSimpleName().equals("WritableNativeArray")) {
                                try {
                                    ArrayList list = (ArrayList)
                                        first.getClass().getMethod("toArrayList")
                                             .invoke(first);
                                    boolean modified = false;
                                    for (Object pairObj : list) {
                                        ArrayList pair = (ArrayList) pairObj;
                                        if (PIN_KEY.equals(pair.get(0))) {
                                            pair.set(1, DEFAULT_PIN);
                                            modified = true;
                                            XposedBridge.log(
                                              "RedirectCameraHook: Restored PIN to " + DEFAULT_PIN
                                            );
                                        }
                                    }
                                    if (modified) {
                                        // args[0] already modified in place
                                        param.args[0] = first;
                                    }
                                } catch (Throwable t) {
                                    XposedBridge.log("Error patching multiGet result: " + t);
                                }
                            }
                        }
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("CallbackImpl.invoke hook failed: " + t);
        }
    }
}
