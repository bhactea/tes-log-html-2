package com.bhakti.redirectcamera;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class MainHook implements IXposedHookLoadPackage {
    private static final String PREFS_NAME = "redirectcamera_cache";

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!"com.harpamobilehr".equals(lpparam.packageName)) {
            return;
        }
        XposedBridge.log("RedirectCameraHook: init for " + lpparam.packageName);

        // 1) Cache setup values when first saved
        hookAsyncStorageSave(lpparam);
        // 2) Reload cache into AsyncStorage before UI
        hookAsyncStorageReload(lpparam);
        // 3) Bypass splash screen
        hookSplashBypass(lpparam);
        // 4) Bypass PIN screen
        hookPinBypass(lpparam);
        // 5) Camera redirect already working
    }

    // Helper to get SharedPreferences
    private SharedPreferences getGlobalPrefs() {
        Context app = (Context) XposedHelpers.callStaticMethod(
            XposedHelpers.findClass("android.app.ActivityThread", null),
            "currentApplication"
        );
        return app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private void hookAsyncStorageSave(final LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.reactnativecommunity.asyncstorage.AsyncStorageModule",
                lpparam.classLoader,
                "multiSet",
                List.class,
                Object.class,
                new XC_MethodHook() {
                    @SuppressWarnings("unchecked")
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        List<?> pairs = (List<?>) param.args[0];
                        SharedPreferences prefs = getGlobalPrefs();
                        for (Object o : pairs) {
                            List<?> kv = (List<?>) o;
                            String key = (String) kv.get(0);
                            String value = (String) kv.get(1);
                            if (Arrays.asList("domain", "port", "userId", "userPin").contains(key)) {
                                prefs.edit().putString(key, value).apply();
                                XposedBridge.log("Cached " + key + "=" + value);
                            }
                        }
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("hookAsyncStorageSave error: " + t);
        }
    }

    private void hookAsyncStorageReload(final LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.harpamobilehr.ui.SplashActivity",
                lpparam.classLoader,
                "onCreate",
                Bundle.class,
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        SharedPreferences prefs = getGlobalPrefs();
                        String domain = prefs.getString("domain", null);
                        String port   = prefs.getString("port",   null);
                        String userId = prefs.getString("userId", null);
                        String pin    = prefs.getString("userPin",null);
                        if (domain != null && port != null && userId != null && pin != null) {
                            Class<?> asmCls = XposedHelpers.findClass(
                                "com.reactnativecommunity.asyncstorage.AsyncStorageModule",
                                lpparam.classLoader
                            );
                            // invoke multiSet(List<List<String>>, Promise)
                            List<List<String>> data = Arrays.asList(
                                Arrays.asList("domain", domain),
                                Arrays.asList("port",   port),
                                Arrays.asList("userId", userId),
                                Arrays.asList("userPin",pin)
                            );
                            XposedHelpers.callStaticMethod(
                                asmCls,
                                "multiSet",
                                data,
                                null
                            );
                            XposedBridge.log("Reloaded AsyncStorage from cache");
                        }
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("hookAsyncStorageReload error: " + t);
        }
    }

    private void hookSplashBypass(final LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.harpamobilehr.ui.SplashActivity",
                lpparam.classLoader,
                "onCreate",
                Bundle.class,
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        Activity splash = (Activity) param.thisObject;
                        try {
                            Class<?> mainCls = XposedHelpers.findClass(
                                "com.harpamobilehr.ui.MainActivity",
                                lpparam.classLoader
                            );
                            Intent i = new Intent(splash, mainCls);
                            splash.startActivity(i);
                            splash.finish();
                            XposedBridge.log("Splash bypassed");
                        } catch (Throwable t) {
                            XposedBridge.log("Splash hook error: " + t);
                        }
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("hookSplashBypass error: " + t);
        }
    }

    private void hookPinBypass(final LoadPackageParam lpparam) {
        String pinCls = "com.harpamobilehr.security.PinActivity";
        try {
            XposedHelpers.findAndHookMethod(
                pinCls,
                lpparam.classLoader,
                "onCreate",
                Bundle.class,
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        Activity pin = (Activity) param.thisObject;
                        pin.finish();
                        XposedBridge.log("PinActivity auto-finished");
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("hookPinBypass error: " + t);
        }
    }
}
