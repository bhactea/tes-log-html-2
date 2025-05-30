package id.harpa.logger;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class EntryPoint implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        // contoh hook
        if (lpparam.packageName.equals("com.android.settings")) {
            android.util.Log.i("HarpaLogger", "Settings app loaded!");
        }
    }
}
