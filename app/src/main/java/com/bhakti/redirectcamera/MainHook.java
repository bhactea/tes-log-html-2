package com.bhakti.redirectcamera;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"com.harpamobilehr".equals(lpparam.packageName)) return;

        XposedBridge.log("Hooking Harpa…");

        // Bypass splash screen (ganti nama kelas sesuai hasil decompile)
        try {
            XposedHelpers.findAndHookMethod(
                "com.harpamobilehr.ui.SplashActivity", 
                lpparam.classLoader,
                "onCreate", android.os.Bundle.class, 
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        android.app.Activity act = (android.app.Activity)param.thisObject;
                        // langsung ke MainActivity
                        android.content.Intent i = new android.content.Intent(act, 
                            XposedHelpers.findClass("com.harpamobilehr.ui.MainActivity", lpparam.classLoader));
                        act.startActivity(i);
                        act.finish();
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("Splash hook error: " + t.getMessage());
        }

        // Bypass PIN screen
        try {
            XposedHelpers.findAndHookMethod(
                "com.harpamobilehr.security.PinActivity",
                lpparam.classLoader,
                "onCreate", android.os.Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        ((android.app.Activity)param.thisObject).finish();
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("PIN hook error: " + t.getMessage());
        }

        // Redirect kamera
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Activity",
                lpparam.classLoader,
                "startActivityForResult", 
                android.content.Intent.class, int.class, android.os.Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        android.content.Intent orig = (android.content.Intent)param.args[0];
                        if (orig != null && android.provider.MediaStore.ACTION_IMAGE_CAPTURE.equals(orig.getAction())) {
                            Intent ni = new Intent(orig);
                            ni.setComponent(new android.content.ComponentName(
                                "net.sourceforge.opencamera", 
                                "net.sourceforge.opencamera.MainActivity"
                            ));
                            param.args[0] = ni;
                        }
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("Camera hook error: " + t.getMessage());
        }
    }
}
