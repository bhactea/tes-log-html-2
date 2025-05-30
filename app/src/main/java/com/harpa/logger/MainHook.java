package com.harpa.logger;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {
    private static final String TAG = "HarpaLogger";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            String line = "<p>[" + time + "] Loaded: <b>" + lpparam.packageName + "</b></p>\\n";

            File logFile = new File(Environment.getExternalStorageDirectory(), "harpa-log.html");
            if (!logFile.exists()) {
                FileWriter initWriter = new FileWriter(logFile);
                initWriter.write("<html><body><h2>Harpa Logger Log</h2>\\n");
                initWriter.close();
            }
            FileWriter writer = new FileWriter(logFile, true);
            writer.write(line);
            writer.close();
        } catch (Throwable t) {
            Log.e(TAG, "Error logging package", t);
        }
    }
}
