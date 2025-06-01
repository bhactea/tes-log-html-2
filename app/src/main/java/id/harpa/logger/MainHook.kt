package id.harpa.logger

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import android.app.Application

class MainHook : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.harpamobilehr") return

        XposedHelpers.findAndHookMethod(
            "android.app.Application", 
            lpparam.classLoader, 
            "onCreate", 
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val context = param.thisObject as Application
                    LogXposed.init(context)
                    LogXposed.log("XPOSED", "🔥 Harpa Mobile Loaded")
                }
            }
        )
    }
}
