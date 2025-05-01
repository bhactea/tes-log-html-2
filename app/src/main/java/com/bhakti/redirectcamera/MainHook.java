package com.bhakti.redirectcamera;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.view.ViewPropertyAnimator;
import android.animation.ValueAnimator;
import android.animation.ObjectAnimator;

import java.util.HashMap;
import java.util.Map;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {
    private static final Map<Integer, Bitmap> bmpCache = new HashMap<>();

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"com.harpamobilehr".equals(lpparam.packageName)) return;
        ClassLoader cl = lpparam.classLoader;

        // 1) Bypass PIN validation
        try {
            XposedHelpers.findAndHookMethod(
                "com.harpamobilehr.modules.PinCodeModule",
                cl,
                "checkPin",
                String.class,
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        param.setResult(true);
                        XposedBridge.log("PIN bypass: checkPin -> true");
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("PIN hook failed: " + t.getMessage());
        }

        // 2) Hide React Native Splash
        try {
            Class<?> mainAct = XposedHelpers.findClass("com.harpamobilehr.MainActivity", cl);
            XposedHelpers.findAndHookMethod(mainAct, "onCreate", Bundle.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    Activity act = (Activity) param.thisObject;
                    try {
                        Class<?> splash = XposedHelpers.findClass(
                            "org.devio.rn.splashscreen.SplashScreen", cl);
                        XposedHelpers.callStaticMethod(splash, "hide", act);
                        XposedBridge.log("Splash hidden");
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("Splash hook failed: " + t.getMessage());
        }

        // 3) Direct navigation to HomeActivity
        try {
            Class<?> mainAct = XposedHelpers.findClass("com.harpamobilehr.MainActivity", cl);
            XposedHelpers.findAndHookMethod(mainAct, "onResume", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    Activity act = (Activity) param.thisObject;
                    try {
                        Class<?> home = XposedHelpers.findClass(
                            "com.harpamobilehr.ui.HomeActivity", cl);
                        Intent i = new Intent(act, home);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        act.startActivity(i);
                        act.finish();
                        XposedBridge.log("Navigated to HomeActivity");
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("Home navigation hook failed: " + t.getMessage());
        }

        // 4) Bypass config via AsyncStorage
        try {
            Class<?> asyncCls = XposedHelpers.findClass(
                "com.reactnativecommunity.asyncstorage.AsyncStorageModule", cl);
            Class<?> readableArray = XposedHelpers.findClass(
                "com.facebook.react.bridge.ReadableArray", cl);
            Class<?> callback = XposedHelpers.findClass(
                "com.facebook.react.bridge.Callback", cl);
            XposedHelpers.findAndHookMethod(asyncCls,
                "multiGet",
                readableArray, callback,
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        Object keysArr = param.args[0];
                        Object cb      = param.args[1];
                        int size = (int) XposedHelpers.callMethod(keysArr, "size");
                        Object arrKeys = XposedHelpers.callStaticMethod(
                            XposedHelpers.findClass("com.facebook.react.bridge.Arguments", cl),
                            "createArray");
                        Object arrVals = XposedHelpers.callStaticMethod(
                            XposedHelpers.findClass("com.facebook.react.bridge.Arguments", cl),
                            "createArray");
                        for (int i = 0; i < size; i++) {
                            String key = (String) XposedHelpers.callMethod(keysArr, "getString", i);
                            XposedHelpers.callMethod(arrKeys, "pushString", key);
                            if ("baseUrl".equals(key) || "serverUrl".equals(key)
                                || "apiUrl".equals(key) || "config".equals(key)) {
                                XposedHelpers.callMethod(arrVals, "pushString", "https://api.harpamobilehr.com");
                            } else {
                                XposedHelpers.callMethod(arrVals, "pushNull");
                            }
                        }
                        XposedHelpers.callMethod(cb, "invoke", arrKeys, arrVals);
                        param.setResult(null);
                        XposedBridge.log("Config bypass via multiGet");
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("Config multiGet hook failed: " + t.getMessage());
        }

        // 5) Performance tweaks: animations & log
        XC_MethodHook animHook = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                param.args[0] = 1L;
            }
        };
        XposedHelpers.findAndHookMethod(ValueAnimator.class, "setDuration", long.class, animHook);
        XposedHelpers.findAndHookMethod(ObjectAnimator.class, "setDuration", long.class, animHook);
        XposedHelpers.findAndHookMethod(ViewPropertyAnimator.class, "setDuration", long.class, animHook);
        Class<?> logCls = XposedHelpers.findClass("android.util.Log", cl);
        for (String lvl : new String[]{"d","i","v","w","e"}) {
            XposedHelpers.findAndHookMethod(logCls, lvl, String.class, String.class,
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        param.setResult(0);
                    }
                }
            );
        }

        // 6) Camera hook → Open Camera depan
        XC_MethodHook camHook = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                Intent orig = (Intent) param.args[0];
                if (orig != null && MediaStore.ACTION_IMAGE_CAPTURE.equals(orig.getAction())) {
                    orig.setPackage("net.sourceforge.opencamera");
                    orig.putExtra("android.intent.extras.CAMERA_FACING", 1);
                    XposedBridge.log("Camera intent → OpenCamera front");
                }
            }
        };
        Class<?> actCls = XposedHelpers.findClass("android.app.Activity", cl);
        XposedHelpers.findAndHookMethod(actCls, "startActivityForResult", Intent.class, int.class, camHook);
        XposedHelpers.findAndHookMethod(actCls, "startActivityForResult", Intent.class, int.class, Bundle.class, camHook);
        XposedHelpers.findAndHookMethod("android.app.Instrumentation", cl,
            "execStartActivity",
            Context.class, IBinder.class, IBinder.class,
            Activity.class, Intent.class, int.class, Bundle.class,
            new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    Intent orig = (Intent) param.args[4];
                    if (orig != null && MediaStore.ACTION_IMAGE_CAPTURE.equals(orig.getAction())) {
                        orig.setPackage("net.sourceforge.opencamera");
                        orig.putExtra("android.intent.extras.CAMERA_FACING", 1);
                        XposedBridge.log("execStartActivity → OpenCamera front");
                    }
                }
            }
        );
    }
}
