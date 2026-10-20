package com.tvupdate.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private static final String DEFAULT_REPO = "chinasoul/BT";
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private ApkDownloader downloader;
    private LocalApiServer api;
    private LinearLayout root;
    private LinearLayout assetList;
    private TextView sourceView;
    private TextView apiView;
    private TextView statusView;
    private TextView selfView;
    private Button installButton;
    private volatile String status = "就绪";
    private volatile String sourceRepo;
    private File readyFile;
    private String readyName;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("tv_update", MODE_PRIVATE);
        sourceRepo = prefs.getString("source_repo", DEFAULT_REPO);
        downloader = new ApkDownloader(this);
        api = new LocalApiServer(this::handleApi);
        makeScreen();
        restoreReady();
        checkSelfUpdate(false);
    }

    @Override protected void onStart() {
        super.onStart();
        try {
            api.start();
            apiView.setText("局域网推送：http://" + localIp() + ":8765  ·  仅应用打开时可用");
        } catch (Exception e) {
            apiView.setText("局域网推送启动失败：" + e.getMessage());
        }
    }

    @Override protected void onStop() {
        api.stop();
        super.onStop();
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            new AlertDialog.Builder(this).setTitle("使用方法")
                    .setMessage("上下左右选择，OK 确认，返回键关闭页面。\n\n"
                            + "电脑或手机可向屏幕显示的地址 POST /api/v1/download 推送 APK 链接，"
                            + "也可 PUT /api/v1/source 更改 GitHub 仓库。局域网接口未设置密码。")
                    .setPositiveButton("知道了", null).show();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void makeScreen() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(48), dp(24), dp(48), dp(24));
        root.setBackgroundColor(0xff101b2c);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(root);
        setContentView(scroll);

        label("电视 APK 更新", 30, 0xffffffff);
        label("方向键移动 · OK 确认 · 菜单查看接口 · 返回退出", 17, 0xffa7b8d2);
        sourceView = label("", 20, 0xffffffff);
        updateSourceView();
        action("查看目标项目最新 Release", this::checkTargetRelease);
        assetList = new LinearLayout(this);
        assetList.setOrientation(LinearLayout.VERTICAL);
        root.addView(assetList);
        selfView = label("本程序：" + BuildConfig.VERSION_NAME, 18, 0xffd7e5fa);
        action("检查本程序更新", () -> checkSelfUpdate(true));
        apiView = label("局域网推送：正在启动…", 18, 0xffd7e5fa);
        statusView = label("状态：就绪", 20, 0xfff4d182);
        installButton = action("安装已下载 APK", this::installReady);
        installButton.setVisibility(View.GONE);
    }

    private TextView label(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setPadding(0, dp(8), 0, dp(8));
        root.addView(view);
        return view;
    }

    private Button action(String text, Runnable onClick) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(19);
        button.setAllCaps(false);
        button.setFocusable(true);
        button.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        root.addView(button, params);
        return button;
    }

    private void updateSourceView() {
        sourceView.setText("目标 GitHub 仓库：" + sourceRepo);
    }

    private void setStatus(String text) {
        status = text;
        statusView.setText("状态：" + text);
    }

    private void checkTargetRelease() {
        String repo = sourceRepo;
        setStatus("正在查询 " + repo + " 的最新 Release…");
        assetList.removeAllViews();
        network.execute(() -> {
            try {
                ReleaseInfo release = ReleaseInfo.parse(Net.latestRelease(repo));
                runOnUiThread(() -> showRelease(repo, release));
            } catch (Exception e) {
                runOnUiThread(() -> setStatus("查询失败：" + message(e)));
            }
        });
    }

    private void showRelease(String repo, ReleaseInfo release) {
        if (!repo.equals(sourceRepo)) return;
        assetList.removeAllViews();
        if (release.apks.isEmpty()) {
            setStatus("最新 Release " + release.tag + " 没有 APK 附件");
            return;
        }
        setStatus("最新 Release：" + release.tag + "，请选择要下载的 APK");
        TextView heading = new TextView(this);
        heading.setText("可选 APK（" + release.apks.size() + " 个）");
        heading.setTextSize(20);
        heading.setTextColor(0xffffffff);
        assetList.addView(heading);
        for (ReleaseInfo.ApkAsset asset : release.apks) {
            Button button = new Button(this);
            button.setAllCaps(false);
            button.setTextSize(18);
            button.setText(asset.display());
            button.setFocusable(true);
            button.setOnClickListener(v -> startDownload(asset.url, asset.name, asset.digest, false));
            assetList.addView(button);
        }
    }

    private void checkSelfUpdate(boolean forced) {
        String repo = BuildConfig.SELF_REPO;
        if (repo.isEmpty()) {
            selfView.setText("本程序：" + BuildConfig.VERSION_NAME + "（尚未绑定 GitHub 仓库）");
            return;
        }
        long now = System.currentTimeMillis();
        if (!forced && now - prefs.getLong("last_self_check", 0) < 6 * 60 * 60 * 1000L) return;
        selfView.setText("本程序：" + BuildConfig.VERSION_NAME + " · 正在检查更新…");
        network.execute(() -> {
            try {
                ReleaseInfo release = ReleaseInfo.parse(Net.latestRelease(repo));
                prefs.edit().putLong("last_self_check", System.currentTimeMillis()).apply();
                runOnUiThread(() -> showSelfRelease(release));
            } catch (Exception e) {
                runOnUiThread(() -> selfView.setText("本程序更新检查失败：" + message(e)));
            }
        });
    }

    private void showSelfRelease(ReleaseInfo release) {
        if (!ReleaseInfo.isNewerTag(release.tag, BuildConfig.VERSION_NAME)) {
            selfView.setText("本程序：" + BuildConfig.VERSION_NAME + " · 已是最新 Release");
            return;
        }
        ReleaseInfo.ApkAsset update = null;
        for (ReleaseInfo.ApkAsset asset : release.apks) {
            if (asset.name.equals("tv-update.apk")) { update = asset; break; }
        }
        if (update == null) {
            selfView.setText("本程序：Release " + release.tag + " 缺少 tv-update.apk");
            return;
        }
        selfView.setText("本程序：" + BuildConfig.VERSION_NAME + " · 有新 Release " + release.tag);
        ReleaseInfo.ApkAsset selected = update;
        new AlertDialog.Builder(this).setTitle("发现本程序更新")
                .setMessage("当前版本 " + BuildConfig.VERSION_NAME + "，GitHub Release " + release.tag
                        + "。现在下载更新 APK？")
                .setNegativeButton("稍后", null)
                .setPositiveButton("下载", (dialog, which) -> startDownload(selected.url,
                        selected.name, selected.digest, true))
                .show();
    }

    private boolean startDownload(String url, String name, String digest, boolean selfUpdate) {
        try {
            boolean accepted = downloader.start(url, name, digest, selfUpdate,
                    new ApkDownloader.Listener() {
                        @Override public void onProgress(String text) { setStatus(text); }
                        @Override public void onSuccess(File file, String displayName, boolean self) {
                            readyFile = file;
                            readyName = displayName;
                            prefs.edit().putString("ready_path", file.getAbsolutePath())
                                    .putString("ready_name", displayName).apply();
                            installButton.setVisibility(View.VISIBLE);
                            installButton.setText("安装 " + displayName);
                            setStatus("下载完成：" + displayName + "，按安装按钮继续");
                            installButton.requestFocus();
                        }
                        @Override public void onFailure(String error) { setStatus("下载失败：" + error); }
                    });
            if (!accepted) {
                setStatus("已有下载任务正在进行，请稍候");
                return false;
            }
            setStatus("开始下载：" + name);
            return true;
        } catch (Exception e) {
            setStatus("无法下载：" + message(e));
            return false;
        }
    }

    private void restoreReady() {
        String path = prefs.getString("ready_path", "");
        File file = new File(path);
        File folder = new File(getFilesDir(), "apks");
        if (!path.isEmpty() && file.isFile() && file.getParentFile().equals(folder)) {
            readyFile = file;
            readyName = prefs.getString("ready_name", "APK");
            installButton.setText("安装 " + readyName);
            installButton.setVisibility(View.VISIBLE);
        }
    }

    private void installReady() {
        if (readyFile == null || !readyFile.isFile()) {
            setStatus("已下载的 APK 不存在，请重新下载");
            installButton.setVisibility(View.GONE);
            return;
        }
        if (!getPackageManager().canRequestPackageInstalls()) {
            setStatus("请先允许本应用安装未知来源，然后回来再次按安装");
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
            startActivity(settings);
            return;
        }
        Intent install = new Intent(Intent.ACTION_VIEW);
        install.setDataAndType(ApkProvider.uriFor(readyFile),
                "application/vnd.android.package-archive");
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivity(install); }
        catch (Exception e) { setStatus("无法打开系统安装界面：" + message(e)); }
    }

    private LocalApiServer.Reply handleApi(String method, String path, String body) throws Exception {
        if ("GET".equals(method) && "/api/v1/status".equals(path)) {
            return new LocalApiServer.Reply(200, new JSONObject()
                    .put("status", status).put("repository", sourceRepo)
                    .put("downloading", downloader.isBusy()));
        }
        if ("PUT".equals(method) && "/api/v1/source".equals(path)) {
            String repo = new JSONObject(body).getString("repository").trim();
            if (!Net.validRepo(repo)) throw new IllegalArgumentException("repository 须为 owner/repo");
            prefs.edit().putString("source_repo", repo).commit();
            sourceRepo = repo;
            runOnUiThread(() -> {
                updateSourceView();
                assetList.removeAllViews();
                setStatus("目标仓库已改为 " + repo);
            });
            return new LocalApiServer.Reply(200, new JSONObject().put("repository", repo));
        }
        if ("POST".equals(method) && "/api/v1/download".equals(path)) {
            JSONObject payload = new JSONObject(body);
            String url = payload.getString("url");
            if (!Net.validApkUrl(url)) throw new IllegalArgumentException("url 须为 HTTP 或 HTTPS 链接");
            if (downloader.isBusy())
                return new LocalApiServer.Reply(409, new JSONObject().put("error", "download busy"));
            String name = new URL(url).getPath();
            name = name.substring(name.lastIndexOf('/') + 1);
            if (name.isEmpty()) name = "推送的 APK";
            String digest = payload.optString("sha256", "");
            if (!digest.isEmpty() && !digest.matches("(?i)[0-9a-f]{64}"))
                throw new IllegalArgumentException("sha256 须为 64 位十六进制值");
            String finalName = name;
            String finalDigest = digest.isEmpty() ? "" : "sha256:" + digest;
            CountDownLatch started = new CountDownLatch(1);
            AtomicBoolean accepted = new AtomicBoolean(false);
            runOnUiThread(() -> {
                try { accepted.set(startDownload(url, finalName, finalDigest, false)); }
                finally { started.countDown(); }
            });
            if (!started.await(5, TimeUnit.SECONDS))
                return new LocalApiServer.Reply(503, new JSONObject().put("error", "TV UI unavailable"));
            if (!accepted.get())
                return new LocalApiServer.Reply(409, new JSONObject().put("error", "download not started"));
            return new LocalApiServer.Reply(202, new JSONObject().put("accepted", true));
        }
        return new LocalApiServer.Reply(404, new JSONObject().put("error", "not found"));
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static String message(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private static String localIp() {
        try {
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (java.net.InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (address instanceof Inet4Address && address.isSiteLocalAddress())
                        return address.getHostAddress();
                }
            }
        } catch (Exception ignored) {}
        return "电视的局域网 IP";
    }
}
