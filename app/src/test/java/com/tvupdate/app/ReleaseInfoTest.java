package com.tvupdate.app;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ReleaseInfoTest {
    @Test public void keepsEveryApkAndExcludesSourceArchives() throws Exception {
        String json = "{\"tag_name\":\"v0.9.40\",\"assets\":["
                + "{\"name\":\"BT-v0.9.40-universal-api19-22.apk\",\"size\":3400000,"
                + "\"browser_download_url\":\"https://github.com/chinasoul/BT/releases/download/v0.9.40/old.apk\"},"
                + "{\"name\":\"universal.apk\",\"size\":3200000,"
                + "\"browser_download_url\":\"https://github.com/chinasoul/BT/releases/download/v0.9.40/universal.apk\"},"
                + "{\"name\":\"source.zip\",\"browser_download_url\":\"https://github.com/a/b\"}]}";
        ReleaseInfo info = ReleaseInfo.parse(json);
        assertEquals("v0.9.40", info.tag);
        assertEquals(2, info.apks.size());
        assertEquals("BT-v0.9.40-universal-api19-22.apk", info.apks.get(0).name);
        assertEquals("universal.apk", info.apks.get(1).name);
    }

    @Test public void validatesRepoAndDownloadUrl() {
        assertTrue(Net.validRepo("chinasoul/BT"));
        assertFalse(Net.validRepo("https://github.com/chinasoul/BT"));
        assertFalse(Net.validRepo("a/../b"));
        assertTrue(Net.validApkUrl("https://example.com/download?id=1"));
        assertFalse(Net.validApkUrl("file:///tmp/app.apk"));
    }

    @Test public void onlyPromptsForNewerSelfRelease() {
        assertTrue(ReleaseInfo.isNewerTag("v1.2.0", "v1.1.9"));
        assertFalse(ReleaseInfo.isNewerTag("v1.1.9", "v1.2.0"));
        assertFalse(ReleaseInfo.isNewerTag("v1.2.0", "v1.2.0"));
        assertFalse(ReleaseInfo.isNewerTag("preview", "v1.2.0"));
    }
}
