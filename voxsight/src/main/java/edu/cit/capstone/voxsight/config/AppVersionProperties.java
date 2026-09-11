package edu.cit.capstone.voxsight.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "voxsight.app")
public class AppVersionProperties {

    /**
     * Minimum required Android versionCode (1 = v1.0.0, 2 = v1.1.0).
     * Requests from versions below this will be rejected with HTTP 426 only if forceUpdate is true.
     */
    private int minVersionCode = 1;

    /**
     * Human-readable minimum required version name.
     */
    private String minVersionName = "1.0.0";

    /**
     * Current latest available version name.
     */
    private String latestVersionName = "1.2";

    /**
     * Direct download URL for the latest APK.
     */
    private String downloadUrl = "https://github.com/lVIN15/VoxSight/releases/download/v1.2/voxsight-v1.2.apk";

    /**
     * URL to the release notes on GitHub.
     */
    private String releaseNotesUrl = "https://github.com/lVIN15/VoxSight/releases/tag/v1.2";

    /**
     * Whether force update enforcement is active. Default false.
     */
    private boolean forceUpdate = false;

    public boolean isForceUpdate() {
        return forceUpdate;
    }

    public void setForceUpdate(boolean forceUpdate) {
        this.forceUpdate = forceUpdate;
    }

    public int getMinVersionCode() {
        return minVersionCode;
    }

    public void setMinVersionCode(int minVersionCode) {
        this.minVersionCode = minVersionCode;
    }

    public String getMinVersionName() {
        return minVersionName;
    }

    public void setMinVersionName(String minVersionName) {
        this.minVersionName = minVersionName;
    }

    public String getLatestVersionName() {
        return latestVersionName;
    }

    public void setLatestVersionName(String latestVersionName) {
        this.latestVersionName = latestVersionName;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public String getReleaseNotesUrl() {
        return releaseNotesUrl;
    }

    public void setReleaseNotesUrl(String releaseNotesUrl) {
        this.releaseNotesUrl = releaseNotesUrl;
    }
}
