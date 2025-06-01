package com.harpa.logger;

import android.os.Environment;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import de.robv.android.xposed.XposedBridge;

public class MainHook implements IXposedHookLoadPackage {

    private void logToHtml(String packageName) {
        try {
            File dir = new File(Environment.getExternalStorageDirectory(), "harpa_log.html");
            FileWriter fw = new FileWriter(dir, true);
            String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            String html = "<p>[" + time + "] Loaded: <b>" + packageName + "</b></p>\n";
            fw.write(html);
            fw.close();
        } catch (Exception e) {
            XposedBridge.log("Logger error: " + e.getMessage());
        }
    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        logToHtml(lpparam.packageName);
    }
}
