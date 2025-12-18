package com.moko.bxp.nordic;

import android.app.Application;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;

import com.elvishew.xlog.LogConfiguration;
import com.elvishew.xlog.LogLevel;
import com.elvishew.xlog.XLog;
import com.elvishew.xlog.flattener.PatternFlattener;
import com.elvishew.xlog.printer.AndroidPrinter;
import com.elvishew.xlog.printer.Printer;
import com.elvishew.xlog.printer.file.FilePrinter;
import com.elvishew.xlog.printer.file.naming.ChangelessFileNameGenerator;
import com.moko.ble.lib.log.ClearLogBackStrategy;
import com.moko.bxp.nordic.utils.IOUtils;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;

public class BaseApplication extends Application {

    private static final String TAG = "TagTastic";
    private static final String LOG_FILE = "TagTastic.txt";
    private static final String LOG_FOLDER = "TagTastic";
    public static String PATH_LOGCAT;

    // 0 = idle
    // 1 = entra slot
    // 2 = cambia password
    private static int mtAutoWriteStatus = 0;

    public int GetMTAutoWriteStatus() {
        return mtAutoWriteStatus;
    }

    public void SetMTAutoWriteStatus(int val) {
        mtAutoWriteStatus = val;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        initXLog();
        mtAutoWriteStatus = 0;
        Thread.setDefaultUncaughtExceptionHandler(new BTUncaughtExceptionHandler());
    }

    private void initXLog() {
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                PATH_LOGCAT = getExternalFilesDir(null).getAbsolutePath() + File.separator + LOG_FOLDER;
            } else {
                PATH_LOGCAT = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + LOG_FOLDER;
            }
        } else {
            PATH_LOGCAT = getFilesDir().getAbsolutePath() + File.separator + LOG_FOLDER;
        }

        Printer filePrinter = new FilePrinter.Builder(PATH_LOGCAT)
                .fileNameGenerator(new ChangelessFileNameGenerator(LOG_FILE))
                .backupStrategy(new ClearLogBackStrategy())
                .flattener(new PatternFlattener("{d yyyy-MM-dd HH:mm:ss} {l}/{t}: {m}"))
                .build();

        LogConfiguration config = new LogConfiguration.Builder()
                .tag(TAG)
                .logLevel(LogLevel.ALL)
                .build();

        XLog.init(config, new AndroidPrinter(), filePrinter);
    }

    public class BTUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {
        @Override
        public void uncaughtException(Thread thread, Throwable ex) {
            final Writer result = new StringWriter();
            ex.printStackTrace(new PrintWriter(result));

            StringBuilder report = new StringBuilder();
            try {
                PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
                report.append(info.versionName).append("\n");
            } catch (Exception ignored) {}

            report.append(result.toString());
            IOUtils.setCrashLog(report.toString(), getApplicationContext());
            XLog.e(report.toString());

            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }
}
