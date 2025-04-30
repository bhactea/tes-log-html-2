package com.bhakti.redirectcamera;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReadableArray;
import com.reactnativecommunity.netinfo.NetInfoModule;

import java.io.IOException;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class MainHook implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"com.harpamobilehr".equals(lpparam.packageName)) return;
        ClassLoader cl = lpparam.classLoader;

        // 1) Bypass checkPin di PinCodeModule
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
        } catch(Throwable t) {
            XposedBridge.log("Failed hook checkPin: " + t.getMessage());
        }

        // 2) Fake network response untuk /validatePin via OkHttp
        try {
            XposedHelpers.findAndHookMethod(
                "okhttp3.RealCall", cl,
                "execute",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Request req = (Request) XposedHelpers.getObjectField(param.thisObject, "originalRequest");
                        String url = req.url().toString();
                        if (url.contains("/validatePin")) {
                            XposedBridge.log("Bypass network validatePin for URL: " + url);
                            Response fake = new Response.Builder()
                                .request(req)
                                .protocol(Protocol.HTTP_1_1)
                                .code(200).message("OK")
                                .body(ResponseBody.create(
                                    MediaType.parse("application/json"),
                                    "{\"status\":\"success\",\"data\":{}}"
                                ))
                                .build();
                            param.setResult(fake);
                        }
                    }
                }
            );
        } catch(Throwable t) {
            XposedBridge.log("Failed hook OkHttp execute: " + t.getMessage());
        }

        // 3) Force NetInfo.isConnected → false (spinner dimatikan)
        try {
            XposedHelpers.findAndHookMethod(
                NetInfoModule.class,
                "getCurrentConnectivity",
                Promise.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        // langsung resolve dengan isConnected=false
                        Bundle info = new Bundle();
                        info.putBoolean("isConnected", false);
                        ((Promise)param.args[0]).resolve(info);
                        param.setResult(null);
                        XposedBridge.log("NetInfo override: set isConnected=false");
                    }
                }
            );
        } catch(Throwable t) {
            XposedBridge.log("Failed hook NetInfo: " + t.getMessage());
        }

        // 4) (Opsional) Hook AsyncStorage.getItem untuk PIN langsung kembalikan 0000
        try {
            XposedHelpers.findAndHookMethod(
                "com.reactnativecommunity.asyncstorage.AsyncStorageModule",
                cl,
                "getItem", String.class, Promise.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        String key = (String)param.args[0];
                        if ("pinCode".equals(key)) {
                            ((Promise)param.args[1]).resolve("0000");
                            param.setResult(null);
                            XposedBridge.log("AsyncStorage getItem pinCode → 0000");
                        }
                    }
                }
            );
        } catch(Throwable t) {
            XposedBridge.log("Failed hook AsyncStorage: " + t.getMessage());
        }

        // 5) Camera intent hook - open front camera
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
        XposedHelpers.findAndHookMethod(
            "android.app.Instrumentation", cl,
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

        // 4) Splash screen bypass
        try {
            Class<?> splashCls = XposedHelpers.findClass(
                "com.harpamobilehr.MainActivity", cl
            );
            XposedHelpers.findAndHookMethod(splashCls, "onCreate", Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            XposedHelpers.callStaticMethod(
                                XposedHelpers.findClass(
                                    "org.devio.rn.splashscreen.SplashScreen", cl
                                ),
                                "hide", param.thisObject
                            );
                            XposedBridge.log("SplashScreen auto-hide injected");
                        } catch(Throwable e) {
                            XposedBridge.log("Splash hide failed: " + e.getMessage());
                        }
                    }
                }
            );
        } catch(Throwable t) {
            XposedBridge.log("Splash hook failed: " + t.getMessage());
        }
    }
}
