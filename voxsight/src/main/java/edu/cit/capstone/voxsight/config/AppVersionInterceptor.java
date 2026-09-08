package edu.cit.capstone.voxsight.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.PrintWriter;

@Component
public class AppVersionInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AppVersionInterceptor.class);
    public static final String HEADER_VERSION_CODE = "X-App-Version-Code";
    public static final String HEADER_VERSION_NAME = "X-App-Version";

    private final AppVersionProperties properties;

    public AppVersionInterceptor(AppVersionProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Skip CORS pre-flight requests
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String versionCodeHeader = request.getHeader(HEADER_VERSION_CODE);
        int clientVersionCode = -1;

        if (versionCodeHeader != null && !versionCodeHeader.isBlank()) {
            try {
                clientVersionCode = Integer.parseInt(versionCodeHeader.trim());
            } catch (NumberFormatException e) {
                log.warn("[AppVersionGate] Invalid {} header: '{}'", HEADER_VERSION_CODE, versionCodeHeader);
            }
        }

        int minRequired = properties.getMinVersionCode();

        // Reject if version code header is missing (e.g. v1.0.0 clients) or below minimum required
        if (clientVersionCode < minRequired) {
            String clientVersionName = request.getHeader(HEADER_VERSION_NAME);
            log.warn("[AppVersionGate] Blocking outdated client. Client versionCode: {}, versionName: '{}', minimum required: {}",
                    clientVersionCode, clientVersionName, minRequired);

            response.setStatus(426); // 426 Upgrade Required
            response.setContentType("application/json;charset=UTF-8");

            String jsonPayload = String.format(
                    "{\"success\":false,\"status\":426,\"error\":\"Update Required: Your version of VoxSight is outdated. Please update to version %s to scan sheet music.\",\"message\":\"Update Required: Your version of VoxSight is outdated. Please update to version %s to continue using VoxSight.\",\"latestVersion\":\"%s\",\"downloadUrl\":\"%s\"}",
                    properties.getMinVersionName(),
                    properties.getMinVersionName(),
                    properties.getLatestVersionName(),
                    properties.getDownloadUrl()
            );

            try (PrintWriter writer = response.getWriter()) {
                writer.write(jsonPayload);
                writer.flush();
            }
            return false;
        }

        return true;
    }
}
