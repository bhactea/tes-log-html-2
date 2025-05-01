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
                        XposedBridge.log("PIN bypass");
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("PIN hook error: " + t.getMessage());
        }

        // 2) Hide SplashScreen in onCreate()
        try {
            Class<?> mainAct = XposedHelpers.findClass("com.harpamobilehr.MainActivity", cl);
            XposedHelpers.findAndHookMethod(mainAct, "onCreate", Bundle.class,
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        Activity act = (Activity)p.thisObject;
                        try {
                            Class<?> splash = XposedHelpers.findClass(
                                "org.devio.rn.splashscreen.SplashScreen", cl);
                            XposedHelpers.callStaticMethod(splash, "hide", act);
                            XposedBridge.log("Splash hidden");
                        } catch (Throwable ignored) {}
                    }
                });
        } catch (Throwable t) {
            XposedBridge.log("Splash hook error: " + t.getMessage());
        }

        // 3) Direct jump to HomeActivity on resume
        try {
            Class<?> mainAct = XposedHelpers.findClass("com.harpamobilehr.MainActivity", cl);
            XposedHelpers.findAndHookMethod(mainAct, "onResume",
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        Activity act = (Activity)p.thisObject;
                        try {
                            Class<?> home = XposedHelpers.findClass(
                                "com.harpamobilehr.ui.HomeActivity", cl);
                            Intent i = new Intent(act, home);
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            act.startActivity(i);
                            act.finish();
                            XposedBridge.log("Jump to Home");
                        } catch (Throwable ignored) {}
                    }
                });
        } catch (Throwable t) {
            XposedBridge.log("Home hook error: " + t.getMessage());
        }

        // 4) Bypass config via AsyncStorage.multiGet
        try {
            Class<?> asyncCls = XposedHelpers.findClass(
                "com.reactnativecommunity.asyncstorage.AsyncStorageModule", cl);
            Class<?> ra = XposedHelpers.findClass("com.facebook.react.bridge.ReadableArray", cl);
            Class<?> cb = XposedHelpers.findClass("com.facebook.react.bridge.Callback", cl);

            XposedHelpers.findAndHookMethod(asyncCls, "multiGet", ra, cb,
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        Object keysArr = p.args[0];
                        Object callback = p.args[1];
                        int n = (int)XposedHelpers.callMethod(keysArr, "size");
                        Object outK = XposedHelpers.callStaticMethod(
                            XposedHelpers.findClass("com.facebook.react.bridge.Arguments", cl),
                            "createArray");
                        Object outV = XposedHelpers.callStaticMethod(
                            XposedHelpers.findClass("com.facebook.react.bridge.Arguments", cl),
                            "createArray");
                        for (int i = 0; i < n; i++) {
                            String key = (String)XposedHelpers.callMethod(keysArr, "getString", i);
                            XposedHelpers.callMethod(outK, "pushString", key);
                            if ("baseUrl".equals(key) || "apiUrl".equals(key)) {
                                XposedHelpers.callMethod(outV, "pushString",
                                    "https://api.harpamobilehr.com");
                            } else {
                                XposedHelpers.callMethod(outV, "pushNull");
                            }
                        }
                        XposedHelpers.callMethod(callback, "invoke", outK, outV);
                        p.setResult(null);
                        XposedBridge.log("Config bypass");
                    }
                });
        } catch (Throwable t) {
            XposedBridge.log("Config hook error: " + t.getMessage());
        }

        // 5) Performance tweaks
        XC_MethodHook anim = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                p.args[0] = 1L;
            }
        };
        XposedHelpers.findAndHookMethod(ValueAnimator.class, "setDuration", long.class, anim);
        XposedHelpers.findAndHookMethod(ObjectAnimator.class, "setDuration", long.class, anim);
        XposedHelpers.findAndHookMethod(ViewPropertyAnimator.class, "setDuration", long.class, anim);

        Class<?> logCls = XposedHelpers.findClass("android.util.Log", cl);
        for (String lvl : new String[]{"d","i","v","w","e"}) {
            XposedHelpers.findAndHookMethod(logCls, lvl,
                String.class, String.class,
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        p.setResult(0);
                    }
                });
        }

        // 6) Camera hook → Open Camera depan
        XC_MethodHook cam = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                Intent orig = (Intent)p.args[0];
                if (orig != null && MediaStore.ACTION_IMAGE_CAPTURE.equals(orig.getAction())) {
                    orig.setPackage("net.sourceforge.opencamera");
                    orig.putExtra("android.intent.extras.CAMERA_FACING", 1);
                    XposedBridge.log("Camera → OpenCamera front");
                }
            }
        };
        Class<?> actCls = XposedHelpers.findClass("android.app.Activity", cl);
        XposedHelpers.findAndHookMethod(actCls, "startActivityForResult", Intent.class, int.class, cam);
        XposedHelpers.findAndHookMethod(actCls, "startActivityForResult", Intent.class, int.class, Bundle.class, cam);
        XposedHelpers.findAndHookMethod("android.app.Instrumentation", cl,
            "execStartActivity",
            Context.class, IBinder.class, IBinder.class,
            Activity.class, Intent.class, int.class, Bundle.class,
            cam);
    }
}
