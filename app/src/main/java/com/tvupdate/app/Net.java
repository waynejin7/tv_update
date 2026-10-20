package com.tvupdate.app;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class Net {
    private Net() {}

    static String latestRelease(String repo) throws Exception {
        if (!validRepo(repo)) throw new IllegalArgumentException("GitHub 仓库须为 owner/repo");
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "https://api.github.com/repos/" + repo + "/releases/latest").openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "tv-update-android");
        try {
            int code = connection.getResponseCode();
            if (code == 403 && "0".equals(connection.getHeaderField("X-RateLimit-Remaining"))) {
                throw new IllegalStateException("GitHub API 查询额度已用完，请稍后重试");
            }
            if (code != 200) throw new IllegalStateException("GitHub 返回 HTTP " + code);
            try (InputStream in = connection.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    if (out.size() + n > 2 * 1024 * 1024) throw new IllegalStateException("Release 信息过大");
                    out.write(buffer, 0, n);
                }
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            connection.disconnect();
        }
    }

    static boolean validRepo(String repo) {
        return repo != null && repo.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
                && !repo.contains("..");
    }

    static boolean validApkUrl(String raw) {
        try {
            URL url = new URL(raw);
            String scheme = url.getProtocol();
            return ("https".equals(scheme) || "http".equals(scheme))
                    && url.getHost() != null && !url.getHost().isEmpty()
                    && url.getUserInfo() == null;
        } catch (Exception ignored) {
            return false;
        }
    }
}
