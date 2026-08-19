package com.sametgkte.fnfporter;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Comparator;

public class MainActivity extends Activity {
    private static final int REQ_TREE = 91;
    private WebView webView;
    private SharedPreferences prefs;
    private String pendingPick = "mod";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("fnfporter", MODE_PRIVATE);

        AppLog.setup(new File(getExternalFilesDir(null), "logs"));
        loadScriptAsset();

        webView = findViewById(R.id.webview);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setTextZoom(100);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                pushPermissionState();
                String mod = prefs.getString("modPath", "");
                String out = prefs.getString("outPath", "");
                view.evaluateJavascript("onRestore(" + JSONObject.quote(mod) + "," + JSONObject.quote(out) + ")", null);
            }
        });
        webView.addJavascriptInterface(new Bridge(), "Android");
        AppLog.setListener(new AppLog.Listener() {
            @Override
            public void onLog(String line) {
                if (webView != null) {
                    webView.evaluateJavascript("onLog(" + JSONObject.quote(line) + ")", null);
                }
            }
        });
        webView.loadUrl("file:///android_asset/www/index.html");
    }

    @Override
    protected void onResume() {
        super.onResume();
        pushPermissionState();
    }

    private void loadScriptAsset() {
        try {
            InputStream in = getAssets().open("ChangeCharacterEvent.hxc");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            ChangeCharacterScript.CONTENTS = bos.toString("UTF-8");
        } catch (Exception e) {
            AppLog.warn("ChangeCharacterEvent.hxc asset missing; event script will be empty.");
        }
    }

    private boolean hasAllFiles() {
        if (Build.VERSION.SDK_INT >= 30) {
            return Environment.isExternalStorageManager();
        }
        if (Build.VERSION.SDK_INT >= 23) {
            return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void pushPermissionState() {
        if (webView == null) return;
        final boolean ok = hasAllFiles();
        webView.post(new Runnable() {
            @Override
            public void run() {
                webView.evaluateJavascript("onPermission(" + (ok ? "true" : "false") + ")", null);
            }
        });
    }

    private void openDocumentTree() {
        try {
            Intent intent = null;
            if (Build.VERSION.SDK_INT >= 29) {
                try {
                    StorageManager sm = (StorageManager) getSystemService(STORAGE_SERVICE);
                    if (sm != null) {
                        StorageVolume volume = sm.getPrimaryStorageVolume();
                        if (volume != null) {
                            intent = volume.createOpenDocumentTreeIntent();
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            if (intent == null) {
                intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            }
            if (intent.getAction() == null) {
                intent.setAction(Intent.ACTION_OPEN_DOCUMENT_TREE);
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            if (Build.VERSION.SDK_INT >= 26) {
                Uri start = initialDocumentUri();
                if (start != null) intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, start);
            }
            startActivityForResult(intent, REQ_TREE);
            return;
        } catch (Exception e) {
            AppLog.error("Could not open Files app", e);
        }
        Toast.makeText(this, "Files app unavailable — using built-in picker", Toast.LENGTH_SHORT).show();
        showBuiltInPicker();
    }

    private Uri initialDocumentUri() {
        String last = prefs.getString(pendingPick + "Path", prefs.getString("lastBrowse", ""));
        File root = Environment.getExternalStorageDirectory();
        String rootPath = root == null ? "/storage/emulated/0" : root.getAbsolutePath();
        String rel = "";
        if (last != null && last.startsWith(rootPath)) {
            rel = last.substring(rootPath.length());
            if (rel.startsWith("/")) rel = rel.substring(1);
        }
        String encoded = "primary:" + rel;
        try {
            return Uri.parse("content://com.android.externalstorage.documents/document/"
                    + Uri.encode(encoded));
        } catch (Exception e) {
            return Uri.parse("content://com.android.externalstorage.documents/document/primary%3A");
        }
    }

    private void showBuiltInPicker() {
        if (webView == null) return;
        webView.evaluateJavascript("openBuiltInPicker(" + JSONObject.quote(pendingPick) + ")", null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_TREE || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception ignored) {
        }
        prefs.edit().putString(pendingPick + "Uri", uri.toString()).apply();
        String path = treeUriToPath(uri);
        if (path == null || path.length() == 0) {
            Toast.makeText(this, "Could not read that folder. Pick it from Internal storage.", Toast.LENGTH_LONG).show();
            return;
        }
        final String which = pendingPick;
        final String chosen = path;
        SharedPreferences.Editor ed = prefs.edit()
                .putString(pendingPick + "Uri", uri.toString())
                .putString(pendingPick + "Path", chosen)
                .putString("lastBrowse", chosen);
        if ("out".equals(pendingPick)) ed.putString("outPath", chosen);
        else ed.putString("modPath", chosen);
        ed.apply();
        if (webView != null) {
            webView.evaluateJavascript(
                    "setPath(" + JSONObject.quote(which) + "," + JSONObject.quote(chosen) + ")",
                    null);
        }
    }

    private String treeUriToPath(Uri uri) {
        if (uri == null) return null;
        try {
            String docId = null;
            if (DocumentsContract.isTreeUri(uri)) {
                docId = DocumentsContract.getTreeDocumentId(uri);
            } else if (DocumentsContract.isDocumentUri(this, uri)) {
                docId = DocumentsContract.getDocumentId(uri);
            }
            if (docId == null) return null;
            if (docId.startsWith("raw:")) {
                return docId.substring(4);
            }
            String[] parts = docId.split(":", 2);
            String type = parts[0];
            String rel = parts.length > 1 ? parts[1] : "";
            if ("primary".equalsIgnoreCase(type) || "home".equalsIgnoreCase(type)) {
                File root = Environment.getExternalStorageDirectory();
                if (rel == null || rel.length() == 0) return root.getAbsolutePath();
                return new File(root, rel).getAbsolutePath();
            }
            if (type != null && type.length() > 0) {
                if (rel == null || rel.length() == 0) return "/storage/" + type;
                return new File("/storage/" + type, rel).getAbsolutePath();
            }
        } catch (Exception e) {
            AppLog.warn("Folder URI could not be resolved: " + uri);
        }
        return null;
    }

    private void requestStorage() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                i.setData(Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Exception e) {
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                } catch (Exception e2) {
                    Toast.makeText(this, "Open Settings > Apps > FNF Porter For Mobile > All files access", Toast.LENGTH_LONG).show();
                }
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, 42);
        }
    }

    public class Bridge {

        @JavascriptInterface
        public void rememberPath(String which, String path) {
            if (path == null || path.length() == 0) return;
            SharedPreferences.Editor ed = prefs.edit().putString("lastBrowse", path);
            if ("out".equals(which)) ed.putString("outPath", path);
            else ed.putString("modPath", path);
            if (which != null) ed.putString(which + "Path", path);
            ed.apply();
        }

        @JavascriptInterface
        public String lastBrowse() {
            return prefs.getString("lastBrowse", storageRoot());
        }

        @JavascriptInterface
        public String version() {
            return Constants.VERSION;
        }

        @JavascriptInterface
        public boolean hasPermission() {
            return hasAllFiles();
        }

        @JavascriptInterface
        public void pickFolder(String which) {
            pendingPick = (which == null || which.length() == 0) ? "mod" : which;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    openDocumentTree();
                }
            });
        }

        @JavascriptInterface
        public void requestPermission() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    requestStorage();
                }
            });
        }

        @JavascriptInterface
        public String storageRoot() {
            File ext = Environment.getExternalStorageDirectory();
            return ext == null ? "/storage/emulated/0" : ext.getAbsolutePath();
        }

        @JavascriptInterface
        public String listDir(String path) {
            JSONArray arr = new JSONArray();
            try {
                File dir = new File(path == null || path.length() == 0 ? storageRoot() : path);
                if (!dir.exists() || !dir.isDirectory()) {
                    JSONObject err = new JSONObject();
                    err.put("error", "Could not read folder: " + dir.getAbsolutePath());
                    return err.toString();
                }
                File[] kids = dir.listFiles();
                if (kids == null) {
                    JSONObject err = new JSONObject();
                    err.put("error", "Access denied. Grant All files access.");
                    return err.toString();
                }
                Arrays.sort(kids, new Comparator<File>() {
                    @Override
                    public int compare(File a, File b) {
                        if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                        return a.getName().compareToIgnoreCase(b.getName());
                    }
                });
                for (File f : kids) {
                    if (f.getName().startsWith(".")) continue;
                    JSONObject o = new JSONObject();
                    o.put("name", f.getName());
                    o.put("path", f.getAbsolutePath());
                    o.put("isDir", f.isDirectory());
                    arr.put(o);
                }
                JSONObject wrap = new JSONObject();
                wrap.put("path", dir.getAbsolutePath());
                wrap.put("parent", dir.getParent() == null ? dir.getAbsolutePath() : dir.getParent());
                wrap.put("entries", arr);
                return wrap.toString();
            } catch (Exception e) {
                return "{\"error\":" + JSONObject.quote(String.valueOf(e.getMessage())) + "}";
            }
        }

        @JavascriptInterface
        public void convert(String modPath, String outPath, String optionsJson) {
            convertMode("p2v", modPath, outPath, optionsJson);
        }

        @JavascriptInterface
        public void convertMode(final String mode, final String modPath, final String outPath, final String optionsJson) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                try {
                    if (modPath == null || modPath.trim().isEmpty() || outPath == null || outPath.trim().isEmpty()) {
                        AppLog.warn("Select an input folder or output folder first!");
                        done(false, "Select both folders.");
                        return;
                    }
                    File mod = new File(modPath);
                    File out = new File(outPath);
                    if (!mod.exists() || !mod.isDirectory()) {
                        AppLog.error("Mod folder not found: " + modPath);
                        done(false, "Mod folder missing");
                        return;
                    }
                    if (!out.exists()) {
                        //noinspection ResultOfMethodCallIgnored
                        out.mkdirs();
                    }
                    if (out.exists()) {
                        AppLog.warn("Folder " + outPath + " already existed before porting, files may have been overwritten.");
                    }
                    prefs.edit().putString("modPath", modPath).putString("outPath", outPath).apply();
                    JSONObject options = new JSONObject(optionsJson);
                    if ("v2p".equals(mode)) {
                        ReverseConverter.convert(mod.getAbsolutePath(), out.getAbsolutePath(), options);
                    } else {
                        Converter.convert(mod.getAbsolutePath(), out.getAbsolutePath(), options);
                    }
                    done(true, "Done");
                } catch (Exception e) {
                    AppLog.error("Conversion failed", e);
                    done(false, String.valueOf(e.getMessage()));
                }
                }
            }, "fnf-convert").start();
        }

        private void done(boolean ok, String msg) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    webView.evaluateJavascript(
                            "onConvertDone(" + (ok ? "true" : "false") + "," + JSONObject.quote(msg) + ")", null);
                }
            });
        }

        @JavascriptInterface
        public String logDump() {
            return AppLog.dump();
        }

        @JavascriptInterface
        public void copyLog() {
            final String text = AppLog.dump();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        if (cm != null) {
                            cm.setPrimaryClip(ClipData.newPlainText("FNF Porter log", text));
                        }
                    } catch (Exception ignored) {
                    }
                }
            });
        }

        @JavascriptInterface
        public String logPath() {
            File f = AppLog.getCurrentLogFile();
            return f == null ? "" : f.getAbsolutePath();
        }

        @JavascriptInterface
        public void openUrl(String url) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public String shortcuts() {
            JSONArray a = new JSONArray();
            try {
                String last = prefs.getString("lastBrowse", "");
                if (last.length() > 0 && new File(last).isDirectory()) {
                    addShortcut(a, "Last used", last);
                }
                addShortcut(a, "Internal", storageRoot());
                addIfExists(a, "Download", storageRoot() + "/Download");
                addIfExists(a, "Downloads", storageRoot() + "/Downloads");
                addIfExists(a, "Documents", storageRoot() + "/Documents");
                addIfExists(a, "mods", storageRoot() + "/mods");
                addIfExists(a, "PsychEngine", storageRoot() + "/PsychEngine");
                addIfExists(a, "funkin", storageRoot() + "/funkin");
                File ext = getExternalFilesDir(null);
                if (ext != null) addShortcut(a, "App folder", ext.getAbsolutePath());
            } catch (Exception ignored) {
            }
            return a.toString();
        }

        private void addIfExists(JSONArray a, String name, String path) throws Exception {
            if (new File(path).isDirectory()) addShortcut(a, name, path);
        }

        private void addShortcut(JSONArray a, String name, String path) throws Exception {
            JSONObject o = new JSONObject();
            o.put("name", name);
            o.put("path", path);
            a.put(o);
        }
    }
}
