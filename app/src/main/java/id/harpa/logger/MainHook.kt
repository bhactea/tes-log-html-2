package id.harpa.logger

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

class MainHook : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == "com.harpamobilehr") {
            XposedBridge.log("🔥 HarpaLogger aktif di ${lpparam.packageName}")
        }
    }
}
