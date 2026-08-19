package com.sametgkte.fnfporter;

import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * In-memory + file logger that also pushes lines to the WebView.
 */
public class AppLog {
    public interface Listener {
        void onLog(String line);
    }

    private static final List<String> LINES = new ArrayList<>();
    private static Listener listener;
    private static File currentLogFile;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm:ss", Locale.US);

    public static void setListener(Listener l) {
        listener = l;
    }

    public static void setup(File logsDir) {
        if (!logsDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            logsDir.mkdirs();
        }
        String name = "fnf-porter-" + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date()) + ".log";
        currentLogFile = new File(logsDir, name);
        info("Logger initialized!");
    }

    public static File getCurrentLogFile() {
        return currentLogFile;
    }

    public static synchronized List<String> snapshot() {
        return new ArrayList<>(LINES);
    }

    public static synchronized String dump() {
        StringBuilder sb = new StringBuilder();
        for (String line : LINES) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    public static void info(String msg) { log("INFO", msg); }
    public static void warn(String msg) { log("WARN", msg); }
    public static void error(String msg) { log("ERROR", msg); }

    public static void error(String msg, Throwable t) {
        log("ERROR", msg + ": " + (t == null ? "unknown" : t.getMessage()));
    }

    public static void banner(String text) {
        int length = Math.max(30, text.length() + 5);
        if (length % 2 != 0) length++;
        int pad = (length - text.length()) / 2;
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < length; i++) bar.append('=');
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < pad; i++) line.append(' ');
        line.append(text);
        info("\n" + bar + "\n" + line + "\n" + bar);
    }

    private static void log(String level, String msg) {
        String stamp = TIME.format(new Date());
        final String line = stamp + ": [" + level + "] " + msg;
        synchronized (AppLog.class) {
            LINES.add(line);
            if (LINES.size() > 4000) {
                LINES.subList(0, 500).clear();
            }
            if (currentLogFile != null) {
                try (FileWriter fw = new FileWriter(currentLogFile, true)) {
                    fw.write(line);
                    fw.write('\n');
                } catch (IOException ignored) {
                }
            }
        }
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) listener.onLog(line);
            }
        });
    }
}
