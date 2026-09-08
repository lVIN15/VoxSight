package edu.cit.capstone.voxsight;

import edu.cit.capstone.voxsight.config.AppVersionInterceptor;
import edu.cit.capstone.voxsight.config.AppVersionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class AppVersionInterceptorTest {

    private AppVersionInterceptor interceptor;
    private AppVersionProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AppVersionProperties();
        properties.setMinVersionCode(2);
        properties.setMinVersionName("1.1.0");
        properties.setLatestVersionName("1.1.0");
        properties.setDownloadUrl("https://github.com/lVIN15/VoxSight/releases/download/v1.1.0/voxsight-v1.1.0.apk");
        interceptor = new AppVersionInterceptor(properties);
    }

    @Test
    void shouldRejectRequestWhenVersionHeaderIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/omr/analyze");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertFalse(result, "Request without version code header must be rejected");
        assertEquals(426, response.getStatus());
        assertTrue(response.getContentAsString().contains("APP_UPDATE_REQUIRED"));
    }

    @Test
    void shouldRejectRequestWhenVersionCodeIsOld() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/omr/analyze");
        request.addHeader(AppVersionInterceptor.HEADER_VERSION_CODE, "1");
        request.addHeader(AppVersionInterceptor.HEADER_VERSION_NAME, "1.0.0");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertFalse(result, "Request with versionCode=1 must be rejected when minVersionCode=2");
        assertEquals(426, response.getStatus());
        assertTrue(response.getContentAsString().contains("APP_UPDATE_REQUIRED"));
    }

    @Test
    void shouldAllowRequestWhenVersionCodeMeetsRequirement() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/omr/analyze");
        request.addHeader(AppVersionInterceptor.HEADER_VERSION_CODE, "2");
        request.addHeader(AppVersionInterceptor.HEADER_VERSION_NAME, "1.1.0");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertTrue(result, "Request with versionCode=2 must be allowed");
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldAllowCorsOptionsRequestWithoutHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/omr/analyze");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertTrue(result, "OPTIONS pre-flight request must always pass");
    }
}
