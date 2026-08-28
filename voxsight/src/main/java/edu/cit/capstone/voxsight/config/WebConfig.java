package edu.cit.capstone.voxsight.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Expose outputs directory in project root to /outputs/** url path
        String userDir = System.getProperty("user.dir");
        File outputsDir = new File(userDir, "outputs");
        if (!outputsDir.exists()) {
            outputsDir.mkdirs();
        }
        
        String resourceLocation = "file:" + outputsDir.getAbsolutePath() + File.separator;
        registry.addResourceHandler("/outputs/**")
                .addResourceLocations(resourceLocation);

        // Expose mobile assets to root url path for browser viewing and debugging
        File assetsDir = new File(userDir, "../mobile/app/src/main/assets");
        if (assetsDir.exists()) {
            String assetsLocation = "file:" + assetsDir.getAbsolutePath() + File.separator;
            registry.addResourceHandler("/**")
                    .addResourceLocations(assetsLocation, "classpath:/static/", "classpath:/public/");
        }
    }
}
