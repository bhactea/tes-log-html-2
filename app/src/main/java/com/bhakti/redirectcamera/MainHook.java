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
        // Hanya jalankan untuk HarpaMobileHR
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
            XposedBridge.log("RedirectCameraHook: MainActivity hook error: " + t.getMessage());
        }

        // 2) Redirect kamera bawaan → OpenCamera front
        try {
            XC_MethodHook camHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Intent orig = (Intent) param.args[0];
                    if (orig != null && MediaStore.ACTION_IMAGE_CAPTURE.equals(orig.getAction())) {
                        Intent ni = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        // Standard extras untuk kamera depan
                        ni.putExtra("android.intent.extras.CAMERA_FACING",
                                    android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT);
                        ni.putExtra("android.intent.extras.LENS_FACING_FRONT", 1);
                        ni.putExtra("android.intent.extra.USE_FRONT_CAMERA", true);
                        // Target ke OpenCamera
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
            XposedHelpers.findAndHookMethod(
                Activity.class,
                "startActivityForResult",
                Intent.class, int.class, Bundle.class,
                camHook
            );
        } catch (Throwable t) {
            XposedBridge.log("RedirectCameraHook: Camera hook error: " + t.getMessage());
        }

        // 3) Hook AsyncStorage.multiGet (RN community AsyncStorage)
        try {
            Class<?> storageClass = XposedHelpers.findClass(
