package com.bhakti.redirectcamera;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
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

import java.util.HashMap;
import java.util.Map;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {
    // Cache untuk decodeResource
    private static final Map<Integer, Bitmap> bmpCache = new HashMap<>();

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // Hanya target package com.harpamobilehr
        if (!"com.harpamobilehr".equals(lpparam.packageName)) return;

        ClassLoader cl = lpparam.classLoader;

        // 1) Bypass PIN: hasPinCode() → selalu true
        try {
            XposedHelpers.findAndHookMethod(
                "com.harpamobilehr.storage.PinStorage",  // sesuaikan class if needed
                cl,
                "hasPinCode",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        param.setResult(true);
                        XposedBridge.log("PIN bypass: hasPinCode → true");
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("PIN bypass hook failed: " + t.getMessage());
        }

        // 2) Performance optimizations
        // 2.1 Percepat animasi kustom
        XC_MethodHook animHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                param.args[0] = 1L;  // durasi minimal 1ms
            }
        };
        Class<?> va = XposedHelpers.findClass("android.animation.ValueAnimator", cl);
        XposedHelpers.findAndHookMethod(va, "setDuration", long.class, animHook);
        Class<?> oa = XposedHelpers.findClass("android.animation.ObjectAnimator", cl);
        XposedHelpers.findAndHookMethod(oa, "setDuration", long.class, animHook);
        Class<?> vpa = XposedHelpers.findClass("android.view.ViewPropertyAnimator", cl);
        XposedHelpers.findAndHookMethod(vpa, "setDuration", long.class, animHook);

        // 2.2 Nonaktifkan logging berlebih
        Class<?> logClass = XposedHelpers.findClass("android.util.Log", cl);
        for (String lvl : new String[]{"d","i","v","w","e"}) {
            XposedHelpers.findAndHookMethod(
                logClass, lvl, String.class, String.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        param.setResult(0);
                    }
                }
            );
        }

        // 2.3 Cache decodeResource
        XposedHelpers.findAndHookMethod(
            BitmapFactory.class, "decodeResource",
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
            }
        );

        // 3) Camera hook → open front camera via ACTION_IMAGE_CAPTURE
        XC_MethodHook cameraHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                Intent orig = (Intent) param.args[0];
                if (orig != null && MediaStore.ACTION_IMAGE_CAPTURE.equals(orig.getAction())) {
                    XposedBridge.log("RedirectCameraHook: intercept CAMERA intent");
                    // pakai ACTION_IMAGE_CAPTURE + setPackage + front camera extra
                    orig.setPackage("net.sourceforge.opencamera");
                    orig.putExtra("android.intent.extras.CAMERA_FACING", 1);  // front
                    param.args[0] = orig;
                }
            }
        };

        Class<?> activityClass = XposedHelpers.findClass("android.app.Activity", cl);
        // hook startActivityForResult overloads
        XposedHelpers.findAndHookMethod(
            activityClass,
            "startActivityForResult",
            Intent.class, int.class,
            cameraHook
        );
        XposedHelpers.findAndHookMethod(
            activityClass,
            "startActivityForResult",
            Intent.class, int.class, Bundle.class,
            cameraHook
        );

        // hook execStartActivity
        XposedHelpers.findAndHookMethod(
            "android.app.Instrumentation",
            cl,
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
            }
        );
    }
}
