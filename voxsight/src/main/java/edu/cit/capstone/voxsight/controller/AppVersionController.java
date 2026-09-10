package edu.cit.capstone.voxsight.controller;

import edu.cit.capstone.voxsight.config.AppVersionProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/app")
public class AppVersionController {

    private final AppVersionProperties appVersionProperties;

    public AppVersionController(AppVersionProperties appVersionProperties) {
        this.appVersionProperties = appVersionProperties;
    }

    @GetMapping("/version")
    public ResponseEntity<Map<String, Object>> getVersionInfo() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("minVersionCode", appVersionProperties.getMinVersionCode());
        response.put("minVersionName", appVersionProperties.getMinVersionName());
        response.put("latestVersionName", appVersionProperties.getLatestVersionName());
        response.put("downloadUrl", appVersionProperties.getDownloadUrl());
        response.put("releaseNotesUrl", appVersionProperties.getReleaseNotesUrl());
        response.put("forceUpdate", appVersionProperties.isForceUpdate());
        return ResponseEntity.ok(response);
    }
}
