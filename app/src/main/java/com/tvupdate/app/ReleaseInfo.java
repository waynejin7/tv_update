package com.tvupdate.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class ReleaseInfo {
    final String tag;
    final String title;
    final List<ApkAsset> apks;

    private ReleaseInfo(String tag, String title, List<ApkAsset> apks) {
        this.tag = tag;
        this.title = title;
        this.apks = apks;
    }

    static ReleaseInfo parse(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONArray raw = root.getJSONArray("assets");
        List<ApkAsset> apks = new ArrayList<>();
        for (int i = 0; i < raw.length(); i++) {
            JSONObject item = raw.getJSONObject(i);
            String name = item.optString("name", "");
            if (!name.toLowerCase(Locale.ROOT).endsWith(".apk")) continue;
            String url = item.optString("browser_download_url", "");
            if (!url.startsWith("https://github.com/")) continue;
            apks.add(new ApkAsset(name, url, item.optLong("size", -1),
                    item.optString("digest", "")));
        }
        return new ReleaseInfo(root.optString("tag_name", ""),
                root.optString("name", ""), apks);
    }

    static boolean isNewerTag(String latest, String installed) {
        int[] remote = versionParts(latest);
        int[] local = versionParts(installed);
        if (remote == null) return false;
        if (local == null) return true;
        for (int i = 0; i < 3; i++) {
            if (remote[i] > local[i]) return true;
            if (remote[i] < local[i]) return false;
        }
        return false;
    }

    private static int[] versionParts(String value) {
        if (value == null) return null;
        String plain = value.startsWith("v") ? value.substring(1) : value;
        if (!plain.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) return null;
        String[] pieces = plain.split("\\.");
        try {
            return new int[]{Integer.parseInt(pieces[0]), Integer.parseInt(pieces[1]),
                    Integer.parseInt(pieces[2])};
        } catch (NumberFormatException e) { return null; }
    }

    static final class ApkAsset {
        final String name;
        final String url;
        final long size;
        final String digest;

        ApkAsset(String name, String url, long size, String digest) {
            this.name = name;
            this.url = url;
            this.size = size;
            this.digest = digest;
        }

        String display() {
            if (size < 0) return name;
            return String.format(Locale.ROOT, "%s  ·  %.1f MB", name, size / 1048576.0);
        }
    }
}
