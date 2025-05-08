package com.bhakti.redirectcamera;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.app.Activity;
import android.app.Instrumentation;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XC_MethodHook;

// Stubs for React Bridge
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.WritableNativeArray;

public class MainHook implements IXposedHookLoadPackage {
    private static final String TARGET = "com.harpamobilehr";
    private static SharedPreferences modPrefs;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals(TARGET)) return;
        Context appCtx = (Context) XposedHelpers.callMethod(
            lpparam.appInfo, "makeApplicationContext", false, null);
        modPrefs = appCtx.getSharedPreferences("redirect_cache", Context.MODE_PRIVATE);

        XposedBridge.log("RedirectCameraHook initialized for " + TARGET);

        hookCamera(appCtx, lpparam);
        hookAsyncStorage(lpparam);
        hookEncryptedStorage(lpparam);
    }

    private void hookCamera(Context ctx, XC_LoadPackage.LoadPackageParam lpparam) {
        XC_MethodHook cameraHook = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                Intent orig = (Intent) p.args[0];
                if (orig != null && MediaStore.ACTION_IMAGE_CAPTURE.equals(orig.getAction())) {
                    XposedBridge.log("HookCamera: redirecting capture to OpenCamera");
                    orig.setPackage("net.sourceforge.opencamera");
                    orig.putExtra("android.intent.extras.CAMERA_FACING", 1);
                    p.args[0] = orig;
                }
            }
        };
        Class<?> act = XposedHelpers.findClass("android.app.Activity", lpparam.classLoader);
        XposedHelpers.findAndHookMethod(act, "startActivityForResult",
            Intent.class, int.class, cameraHook);
        XposedHelpers.findAndHookMethod(act, "startActivityForResult",
            Intent.class, int.class, Bundle.class, cameraHook);
        XposedHelpers.findAndHookMethod(
            "android.app.Instrumentation", lpparam.classLoader,
            "execStartActivity",
            Context.class, IBinder.class, IBinder.class,
            Activity.class, Intent.class, int.class, Bundle.class,
            new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    Intent orig = (Intent)p.args[4];
                    if (orig != null && MediaStore.ACTION_IMAGE_CAPTURE.equals(orig.getAction())) {
                        XposedBridge.log("HookCamera: execStart redirect");
                        orig.setPackage("net.sourceforge.opencamera");
                        orig.putExtra("android.intent.extras.CAMERA_FACING", 1);
                        p.args[4] = orig;
                    }
                }
            }
        );
    }

    private void hookAsyncStorage(XC_LoadPackage.LoadPackageParam lpparam) {
        String cls = "com.reactnativecommunity.asyncstorage.AsyncStorageModule";
        // setItem
        XposedHelpers.findAndHookMethod(cls, lpparam.classLoader,
            "setItem", String.class, String.class, Promise.class,
            new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    String k = (String)p.args[0];
                    String v = (String)p.args[1];
                    modPrefs.edit().putString("AS:"+k, v).apply();
                    XposedBridge.log("Cached AS:"+k+"="+v);
                }
            }
        );
        // getItem
        XposedHelpers.findAndHookMethod(cls, lpparam.classLoader,
            "getItem", String.class, Promise.class,
            new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                    String k = (String)p.args[0];
                    String c = modPrefs.getString("AS:"+k, null);
                    if (c != null) {
                        Promise pr = (Promise)p.args[1];
                        pr.resolve(c);
                        p.setResult(null);
                        XposedBridge.log("Injected AS getItem:"+k+"="+c);
                    }
                }
            }
        );
    }

    private void hookEncryptedStorage(XC_LoadPackage.LoadPackageParam lpparam) {
        String cls = "com.emeraldsanto.encryptedstorage.RNEncryptedStorageModule";
        // setItem
        XposedHelpers.findAndHookMethod(cls, lpparam.classLoader,
            "setItem", String.class, String.class, Promise.class,
            new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    String k = (String)p.args[0];
                    String v = (String)p.args[1];
                    modPrefs.edit().putString("ENC:"+k, v).apply();
                    XposedBridge.log("Cached ENC:"+k+"="+v);
                }
            }
        );
        // getItem
        XposedHelpers.findAndHookMethod(cls, lpparam.classLoader,
            "getItem", String.class, Promise.class,
            new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                    String k = (String)p.args[0];
                    String c = modPrefs.getString("ENC:"+k, null);
                    if (c != null) {
                        Promise pr = (Promise)p.args[1];
                        pr.resolve(c);
                        p.setResult(null);
                        XposedBridge.log("Injected ENC getItem:"+k+"="+c);
                    }
                }
            }
        );
    }
}
