// src/main/java/com/example/dbeventservice/config/CloudinaryConfig.java
package com.example.dbeventservice.config;

import com.cloudinary.Cloudinary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class CloudinaryConfig {
    private static final Logger log = LoggerFactory.getLogger(CloudinaryConfig.class);

    @Value("${cloudinary.url:}")              private String propUrl;
    @Value("${cloudinary.cloud-name:}")      private String propCloud;
    @Value("${cloudinary.api-key:}")         private String propKey;
    @Value("${cloudinary.api-secret:}")      private String propSecret;

    @Bean
    public Cloudinary cloudinary() {
        // 1) Prefer CLOUDINARY_URL env if present (Render recommended)
        String envUrl = System.getenv("CLOUDINARY_URL");
        if (notBlank(envUrl)) {
            log.info("Cloudinary: using CLOUDINARY_URL env");
            return new Cloudinary(envUrl);
        }

        // 2) Then spring property cloudinary.url if present
        if (notBlank(propUrl)) {
            log.info("Cloudinary: using cloudinary.url property");
            return new Cloudinary(propUrl);
        }

        // 3) Otherwise discrete keys from properties (with env fallback)
        String cloud  = firstNonBlank(propCloud,  System.getenv("CLOUDINARY_CLOUD_NAME"));
        String key    = firstNonBlank(propKey,    System.getenv("CLOUDINARY_API_KEY"));
        String secret = firstNonBlank(propSecret, System.getenv("CLOUDINARY_API_SECRET"));

        if (!notBlank(cloud) || !notBlank(key) || !notBlank(secret)) {
            log.error("Cloudinary: missing credentials. Provide CLOUDINARY_URL or cloudinary.{cloud-name,api-key,api-secret}");
            throw new IllegalStateException("Cloudinary credentials not configured");
        }

        Map<String, String> cfg = new HashMap<>();
        cfg.put("cloud_name", cloud);
        cfg.put("api_key",    key);
        cfg.put("api_secret", secret);
        log.info("Cloudinary: using discrete properties (cloud_name={})", cloud);
        return new Cloudinary(cfg);
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
    private static String firstNonBlank(String a, String b) { return notBlank(a) ? a : (notBlank(b) ? b : ""); }
}
