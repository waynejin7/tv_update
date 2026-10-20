package com.tvupdate.app;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ApkDownloader {
    interface Listener {
        void onProgress(String text);
        void onSuccess(File file, String displayName, boolean selfUpdate);
        void onFailure(String message);
    }

    private static final long MAX_BYTES = 1024L * 1024L * 1024L;
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean busy;

    ApkDownloader(Context context) { this.context = context.getApplicationContext(); }
    boolean isBusy() { return busy; }

    synchronized boolean start(String url, String displayName, String digest, boolean selfUpdate,
                               Listener listener) {
        if (busy) return false;
        if (!Net.validApkUrl(url)) throw new IllegalArgumentException("下载链接须为 HTTP 或 HTTPS");
        busy = true;
        worker.execute(() -> download(url, displayName, digest, selfUpdate, listener));
        return true;
    }

    private void download(String rawUrl, String name, String digest, boolean selfUpdate,
                          Listener listener) {
        File folder = new File(context.getFilesDir(), "apks");
        folder.mkdirs();
        File partial = new File(folder, "pending-" + UUID.randomUUID() + ".apk");
        File result = new File(folder, UUID.randomUUID() + ".apk");
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(rawUrl).openConnection();
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("User-Agent", "tv-update-android");
            int code = connection.getResponseCode();
            if (code != 200) throw new IllegalStateException("下载服务器返回 HTTP " + code);
            long total = connection.getContentLengthLong();
            if (total > MAX_BYTES) throw new IllegalStateException("APK 超过 1 GB 限额");
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            long done = 0;
            long lastProgress = 0;
            try (InputStream in = connection.getInputStream();
                 FileOutputStream out = new FileOutputStream(partial)) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    done += count;
                    if (done > MAX_BYTES) throw new IllegalStateException("APK 超过 1 GB 限额");
                    out.write(buffer, 0, count);
                    sha.update(buffer, 0, count);
                    long now = android.os.SystemClock.elapsedRealtime();
                    if (now - lastProgress >= 300) {
                        lastProgress = now;
                        long current = done;
                        main.post(() -> listener.onProgress(progressText(current, total)));
                    }
                }
                out.getFD().sync();
            }
            if (total >= 0 && done != total) throw new IllegalStateException("文件未完整下载");
            if (done == 0) throw new IllegalStateException("下载到空文件");
            if (digest != null && digest.startsWith("sha256:")) {
                String actual = hex(sha.digest());
                if (!actual.equalsIgnoreCase(digest.substring(7)))
                    throw new IllegalStateException("APK SHA-256 校验失败");
            }
            PackageManager pm = context.getPackageManager();
            PackageInfo archive = pm.getPackageArchiveInfo(partial.getAbsolutePath(),
                    PackageManager.GET_SIGNING_CERTIFICATES);
            if (archive == null || archive.packageName == null)
                throw new IllegalStateException("下载的文件不是有效 APK");
            if (selfUpdate) verifySelfUpdate(pm, archive);
            if (!partial.renameTo(result)) throw new IllegalStateException("无法保存 APK");
            File completed = result;
            main.post(() -> listener.onSuccess(completed, name, selfUpdate));
        } catch (Exception e) {
            partial.delete();
            result.delete();
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            main.post(() -> listener.onFailure(message));
        } finally {
            if (connection != null) connection.disconnect();
            busy = false;
        }
    }

    private void verifySelfUpdate(PackageManager pm, PackageInfo archive) throws Exception {
        if (!context.getPackageName().equals(archive.packageName))
            throw new IllegalStateException("更新 APK 的包名不符");
        PackageInfo installed = pm.getPackageInfo(context.getPackageName(),
                PackageManager.GET_SIGNING_CERTIFICATES);
        if (archive.getLongVersionCode() <= installed.getLongVersionCode())
            throw new IllegalStateException("更新 APK 的版本号未高于当前版本");
        if (archive.signingInfo == null || installed.signingInfo == null
                || archive.signingInfo.getApkContentsSigners().length != 1
                || installed.signingInfo.getApkContentsSigners().length != 1
                || !archive.signingInfo.getApkContentsSigners()[0]
                        .equals(installed.signingInfo.getApkContentsSigners()[0]))
            throw new IllegalStateException("更新 APK 的签名与当前应用不一致");
    }

    private static String progressText(long done, long total) {
        if (total > 0) return String.format(Locale.ROOT, "下载中：%.1f / %.1f MB（%d%%）",
                done / 1048576.0, total / 1048576.0, Math.min(100, done * 100 / total));
        return String.format(Locale.ROOT, "下载中：%.1f MB", done / 1048576.0);
    }

    private static String hex(byte[] value) {
        StringBuilder out = new StringBuilder(value.length * 2);
        for (byte b : value) out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return out.toString();
    }
}
