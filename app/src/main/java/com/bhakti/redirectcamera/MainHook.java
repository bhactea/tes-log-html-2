package com.bhakti.redirectcamera;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.provider.MediaStore;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class MainHook implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        // Hanya untuk HarpaMobileHR
        if (!"com.harpamobilehr".equals(lpparam.packageName)) {
            return;
        }
        XposedBridge.log("RedirectCameraHook: initializing hooks for " + lpparam.packageName);

        // 1) Bypass SplashActivity → langsung ke MainActivity
        try {
            XposedHelpers.findAndHookMethod(
                "com.harpamobilehr.ui.SplashActivity",
                lpparam.classLoader,
                "onCreate",
                Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Activity splash = (Activity) param.thisObject;
                        Intent i = new Intent(splash,
                            XposedHelpers.findClass("com.harpamobilehr.ui.MainActivity",
                                lpparam.classLoader));
                        splash.startActivity(i);
                        splash.finish();
                        XposedBridge.log("RedirectCameraHook: Splash bypassed");
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("RedirectCameraHook: Splash hook error: " + t.getMessage());
        }

        // 2) Bypass PinActivity → onCreate() langsung finish()
        try {
            XposedHelpers.findAndHookMethod(
                "com.harpamobilehr.security.PinActivity",
                lpparam.classLoader,
                "onCreate",
                Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Activity pin = (Activity) param.thisObject;
                        pin.finish();
                        XposedBridge.log("RedirectCameraHook: PinActivity auto-finished");
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("RedirectCameraHook: Pin hook error: " + t.getMessage());
        }

        // 3) Redirect kamera bawaan ke Open Camera (front)
        try {
            XC_MethodHook camHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Intent orig = (Intent) param.args[0];
                    if (orig != null && MediaStore.ACTION_IMAGE_CAPTURE.equals(orig.getAction())) {
                        Intent ni = new Intent(orig);
                        ni.setComponent(new ComponentName(
                            "net.sourceforge.opencamera",
                            "net.sourceforge.opencamera.MainActivity"
                        ));
                        ni.putExtra("net.sourceforge.opencamera.use_front", true);
                        param.args[0] = ni;
                        XposedBridge.log("RedirectCameraHook: Camera redirected to front Open Camera");
                    }
                }
            };
            XposedHelpers.findAndHookMethod(
                Activity.class,
                "startActivityForResult",
                Intent.class,
                int.class,
                Bundle.class,
                camHook
            );
        } catch (Throwable t) {
            XposedBridge.log("RedirectCameraHook: Camera hook error: " + t.getMessage());
        }

        
