package com.bhakti.redirectcamera;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;

import java.util.List;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReadableArray;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class MainHook implements IXposedHookLoadPackage {
    private static final String PREFS = "redirectcamera_cache";
    private SharedPreferences prefs;

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (!"com.harpamobilehr".equals(lpparam.packageName)) return;

        Context app = (Context) XposedHelpers.callStaticMethod(
            XposedHelpers.findClass("android.app.ActivityThread", null),
            "currentApplication"
        );
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        XposedBridge.log("RedirectCameraHook: init for " + lpparam.packageName);
        hookAsyncStorageMultiSet(lpparam);
        hookMainActivityOnCreate(lpparam);
        hookCameraRedirect(lpparam);
    }

    // —— 1) Cache PIN dari AsyncStorage.multiSet(ReadableArray, Promise)
    private void hookAsyncStorageMultiSet(final LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.reactnativecommunity.asyncstorage.AsyncStorageModule",
                lpparam.classLoader,
                "multiSet",
                List.class,
                Promise.class,
                new XC_MethodHook() {
                    @SuppressWarnings("unchecked")
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        List<List<String>> pairs = (List<List<String>>) param.args[0];
                        for (List<String> kv : pairs) {
                            if ("userPin".equals(kv.get(0))) {
                                prefs.edit()
                                     .putString("userPin", kv.get(1))
                                     .putBoolean("bypassPin", true)
                                     .apply();
                                XposedBridge.log("Cached PIN=" + kv.get(1));
                            }
                        }
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("AsyncStorage.multiSet hook failed: " + t);
        }
    }

    // —— 2) Bypass PIN di MainActivity.onCreate
    private void hookMainActivityOnCreate(final LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.harpamobilehr.MainActivity",
                lpparam.classLoader,
                "onCreate",
                Bundle.class,
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        Activity act = (Activity) param.thisObject;
                        if (prefs.getBoolean("bypassPin", false)) {
                            XposedBridge.log("Bypassing PIN screen");
                            Class<?> home = XposedHelpers.findClass(
                                "com.harpamobilehr.ui.HomeActivity",
                                lpparam.classLoader
                            );
                            act.startActivity(new Intent(act, home));
                            act.finish();
                        }
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("MainActivity.onCreate hook failed: " + t);
        }
    }

    // —— 3) Redirect kamera front via your snippet
    private void hookCameraRedirect(final LoadPackageParam lpparam) {
        XC_MethodHook cam = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                Intent orig = null;
                String m = p.method.getName();
                if ("startActivityForResult".equals(m)) {
                    orig = (Intent) p.args[0];
                } else {
                    orig = (Intent) p.args[4];
                }
                if (orig != null && MediaStore.ACTION_IMAGE_CAPTURE.equals(orig.getAction())) {
                    XposedBridge.log("Intercept CAMERA intent");
                    orig.setPackage("net.sourceforge.opencamera");
                    orig.putExtra("android.intent.extras.CAMERA_FACING", 1);
                    if ("startActivityForResult".equals(m)) {
                        p.args[0] = orig;
                    } else {
                        p.args[4] = orig;
                    }
                }
            }
        };

        try {
            Class<?> act = XposedHelpers.findClass("android.app.Activity", null);
            XposedHelpers.findAndHookMethod(act,
                "startActivityForResult",
                Intent.class, int.class, cam
            );
            XposedHelpers.findAndHookMethod(act,
                "startActivityForResult",
                Intent.class, int.class, Bundle.class, cam
            );
        } catch (Throwable t) {
            XposedBridge.log("Hook startActivityForResult failed: " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Instrumentation",
                lpparam.classLoader,
                "execStartActivity",
                Context.class, IBinder.class, IBinder.class,
                Activity.class, Intent.class, int.class, Bundle.class, cam
            );
        } catch (Throwable t) {
            XposedBridge.log("Hook execStartActivity failed: " + t);
        }
    }
}
