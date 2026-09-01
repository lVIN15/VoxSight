package edu.cit.capstone.voxsight.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${voxsight.storage.outputs-dir:}")
    private String outputsDirConfig;

    @Value("${cors.allowed-origins:*}")
    private String allowedOrigins;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        File outputsDir;
        if (outputsDirConfig != null && !outputsDirConfig.isBlank()) {
            outputsDir = new File(outputsDirConfig);
        } else {
            String userDir = System.getProperty("user.dir");
            outputsDir = new File(userDir, "outputs");
        }

        if (!outputsDir.exists()) {
            outputsDir.mkdirs();
        }

        String resourceLocation = "file:" + outputsDir.getAbsolutePath() + File.separator;
        registry.addResourceHandler("/outputs/**")
                .addResourceLocations(resourceLocation);

        // Expose mobile assets to root url path for browser viewing and debugging (if present)
        String userDir = System.getProperty("user.dir");
        File assetsDir = new File(userDir, "../mobile/app/src/main/assets");
        if (assetsDir.exists()) {
            String assetsLocation = "file:" + assetsDir.getAbsolutePath() + File.separator;
            registry.addResourceHandler("/**")
                    .addResourceLocations(assetsLocation, "classpath:/static/", "classpath:/public/");
        }
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = allowedOrigins.split(",");
        registry.addMapping("/**")
                .allowedOriginPatterns(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false);
    }
}

