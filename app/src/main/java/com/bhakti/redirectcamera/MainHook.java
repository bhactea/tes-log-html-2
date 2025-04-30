package com.bhakti.redirectcamera;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.util.Log;
import android.view.ViewPropertyAnimator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;

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

        // ═══ 1. Bypass PIN: checkPin() di PinCodeModule → always true ═══
        try {
            XposedHelpers.findAndHookMethod(
                "com.harpamobilehr.modules.PinCodeModule",
                cl,
                "checkPin",
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        param.setResult(true);
                        XposedBridge.log("PIN bypass: checkPin → true");
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("PIN bypass hook failed: " + t.getMessage());
        }

        // ═══ 2. Bypass SplashScreen via MainActivity.onCreate ═══
        try {
            Class<?> mainAct = XposedHelpers.findClass("com.harpamobilehr.MainActivity", cl);
            XposedHelpers.findAndHookMethod(mainAct, "onCreate", Bundle.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Activity act = (Activity) param.thisObject;
                    try {
                        // langsung hide splash
                        XposedHelpers.callStaticMethod(
                            XposedHelpers.findClass(
                                "org.devio.rn.splashscreen.SplashScreen", cl),
                            "hide", act);
                        XposedBridge.log("SplashScreen auto-hide injected");
                    } catch (Throwable e) {
                        XposedBridge.log("Failed hiding splash: " + e.getMessage());
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("Splash hook failed: " + t.getMessage());
        }

        // ═══ 3. Performance tweaks ═══
        // 3.1 Animasi kustom → durasi minimal
        XC_MethodHook animHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                param.args[0] = 1L;
            }
        };
        XposedHelpers.findAndHookMethod(ValueAnimator.class, "setDuration", long.class, animHook);
        XposedHelpers.findAndHookMethod(ObjectAnimator.class, "setDuration", long.class, animHook);
        XposedHelpers.findAndHookMethod(ViewPropertyAnimator.class, "setDuration", long.class, animHook);

        // 3.2 Matikan Log.d/i/v/w/e
        Class<?> logClass = XposedHelpers.findClass("android.util.Log", cl);
        for (String lvl : new String[]{"d","i","v","w","e"}) {
            XposedHelpers.findAndHookMethod(logClass, lvl,
                String.class, String.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        param.setResult(0);
                    }
                });
        }

        // 3.3 Cache decodeResource
        XposedHelpers.findAndHookMethod(BitmapFactory.class, "decodeResource",
            Resources.class, int.class, BitmapFactory.Options.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    int resId = (Integer) param.args[1];
                    if (bmpCache.containsKey(resId)) {
                        param.setResult(bmpCache.get(resId));
                    }
                }
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    int resId = (Integer) param.args[1];
                    Bitmap bmp = (Bitmap) param.getResult();
                    bmpCache.put(resId, bmp);
                }
            });

        // ═══ 4. Camera hook → Open Camera depan ═══
        XC_MethodHook cameraHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                Intent orig = (Intent) param.args[0];
                if (orig != null && MediaStore.ACTION_IMAGE_CAPTURE.equals(orig.getAction())) {
                    XposedBridge.log("RedirectCameraHook: intercept CAMERA intent");
                    orig.setPackage("net.sourceforge.opencamera");
                    orig.putExtra("android.intent.extras.CAMERA_FACING", 1);
                    param.args[0] = orig;
                }
            }
        };
        Class<?> activityClass = XposedHelpers.findClass("android.app.Activity", cl);
        XposedHelpers.findAndHookMethod(activityClass,
            "startActivityForResult", Intent.class, int.class, cameraHook);
        XposedHelpers.findAndHookMethod(activityClass,
            "startActivityForResult", Intent.class, int.class, Bundle.class, cameraHook);
        XposedHelpers.findAndHookMethod("android.app.Instrumentation", cl,
            "execStartActivity",
            Context.class, IBinder.class, IBinder.class,
            Activity.class, Intent.class, int.class, Bundle.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Intent orig = (Intent) param.args[4];
                    if (orig != null && MediaStore.ACTION_IMAGE_CAPTURE.equals(orig.getAction())) {
                        XposedBridge.log("RedirectCameraHook: execStartActivity intercept");
                        orig.setPackage("net.sourceforge.opencamera");
                        orig.putExtra("android.intent.extras.CAMERA_FACING", 1);
                        param.args[4] = orig;
                    }
                }
            });
    }
}
