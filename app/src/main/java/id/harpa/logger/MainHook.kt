package id.harpa.logger

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage
import de.robv.android.xposed.XposedBridge
import android.util.Log

class MainHook : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            if (lpparam.packageName != "com.harpamobilehr") return

            Log.e("HarpaLogger", "🎯 Hook loaded into Harpa: " + lpparam.packageName)
            XposedBridge.log("✅ HarpaLogger: Hook berhasil masuk ke Harpa")

            // Uji coba logging
            LogXposed.log("MainHook", "🎯 Berhasil hook Harpa Mobile")
        } catch (e: Throwable) {
            Log.e("HarpaLogger", "❌ Error: ${e.message}")
            XposedBridge.log("❌ HarpaLogger: ${e.stackTraceToString()}")
        }
    }
}
