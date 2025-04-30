package com.bhakti.redirectcamera;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.util.Log;
import android.view.ViewPropertyAnimator;
import android.animation.ValueAnimator;
import android.animation.ObjectAnimator;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

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
        // 1) Target hanya Harpa
        if (!"com.harpamobilehr".equals(lpparam.packageName)) return;
        ClassLoader cl = lpparam.classLoader;

        // ─── Bypass PIN ───
        try {
            XposedHelpers.findAndHookMethod(
                "com.harpamobilehr.modules.PinCodeModule", // hasil smali
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
            XposedBridge.log("PIN hook failed: " + t.getMessage());
        }

        // ─── Bypass SplashScreen ───
        try {
            Class<?> actCls = XposedHelpers.findClass("com.harpamobilehr.MainActivity", cl);
            XposedHelpers.findAndHookMethod(actCls, "onCreate", Bundle.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Activity act = (Activity) param.thisObject;
                    try {
                        // Panggil SplashScreen.hide(act)
                        Class<?> splashCls = XposedHelpers.findClass(
                            "org.devio.rn.splashscreen.SplashScreen", cl);
                        XposedHelpers.callStaticMethod(splashCls, "hide", act);
                        XposedBridge.log("SplashScreen hidden");
                    } catch (Throwable e) {
                        XposedBridge.log("Splash hide failed: " + e.getMessage());
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("Splash hook failed: " + t.getMessage());
        }

        // ─── Performance Tweaks ───
        XC_MethodHook animHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                param.args[0] = 1L;  // durasi animasi jadi 1ms
            }
        };
        XposedHelpers.findAndHookMethod(ValueAnimator.class, "setDuration", long.class, animHook);
        XposedHelpers.findAndHookMethod(ObjectAnimator.class, "setDuration", long.class, animHook);
        XposedHelpers.findAndHookMethod(ViewPropertyAnimator.class, "setDuration", long.class, animHook);

        Class<?> logCls = XposedHelpers.findClass("android.util.Log", cl);
        for (String lvl : new String[]{"d","i","v","w","e"}) {
            XposedHelpers.findAndHookMethod(logCls, lvl, String.class, String.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    param.setResult(0);
                }
            });
        }

        XposedHelpers.findAndHookMethod(BitmapFactory.class, "decodeResource",
            Resources.class, int.class, BitmapFactory.Options.class, new XC_MethodHook() {
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

        // ─── Camera Hook → Open Camera depan ───
        XC_MethodHook camHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                Intent orig = (Intent) param.args[0];
                if (orig != null && MediaStore.ACTION_IMAGE_CAPTURE.equals(orig.getAction())) {
                    orig.setPackage("net.sourceforge.opencamera");
                    orig.putExtra("android.intent.extras.CAMERA_FACING", 1);
                    XposedBridge.log("Camera intent redirected front");
                }
            }
        };

        Class<?> actCls0 = XposedHelpers.findClass("android.app.Activity", cl);
        XposedHelpers.findAndHookMethod(actCls0, "startActivityForResult",
            Intent.class, int.class, camHook);
        XposedHelpers.findAndHookMethod(actCls0, "startActivityForResult",
            Intent.class, int.class, Bundle.class, camHook);

        XposedHelpers.findAndHookMethod("android.app.Instrumentation", cl,
            "execStartActivity",
            Context.class, IBinder.class, IBinder.class,
            Activity.class, Intent.class, int.class, Bundle.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Intent orig = (Intent) param.args[4];
                    if (orig != null && MediaStore.ACTION_IMAGE_CAPTURE.equals(orig.getAction())) {
                        orig.setPackage("net.sourceforge.opencamera");
                        orig.putExtra("android.intent.extras.CAMERA_FACING", 1);
                        XposedBridge.log("execStartActivity camera redir");
                    }
                }
            });
    }
}
