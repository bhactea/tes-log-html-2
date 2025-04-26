package com.bhakti.redirectcamera;

import android.content.ComponentName;
import android.content.Intent;
import android.provider.MediaStore;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!lpparam.packageName.equals("com.harpamobilehr")) return;
        XposedHelpers.findAndHookMethod(
            "android.app.Activity",
            lpparam.classLoader,
            "startActivity",
            Intent.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Intent orig = (Intent) param.args[0];
                    if (orig != null && MediaStore.ACTION_IMAGE_CAPTURE.equals(orig.getAction())) {
                        XposedBridge.log("RedirectCameraHook: CAMERA intent intercepted");
                        Intent ni = new Intent();
                        ni.setComponent(new ComponentName(
                            "net.sourceforge.opencamera",
                            "net.sourceforge.opencamera.MainActivity"
                        ));
                        param.args[0] = ni;
                        XposedBridge.log("RedirectCameraHook: Redirected to Open Camera");
                    }
                }
            }
        );
    }
}
