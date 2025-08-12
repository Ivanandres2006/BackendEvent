package com.example.dbeventservice.config;

import com.cloudinary.Cloudinary;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class CloudinaryConfig {
    @Bean
    public Cloudinary cloudinary() {
        // Render: set CLOUDINARY_URL in Environment (format below)
        // CLOUDINARY_URL=cloudinary://<api_key>:<api_secret>@<cloud_name>
        String url = System.getenv("CLOUDINARY_URL");
        if (url != null && !url.isBlank()) {
            return new Cloudinary(url);
        }
        // fallback: read discrete envs if you prefer
        return new Cloudinary(Map.of(
                "cloud_name", System.getenv("CLOUDINARY_CLOUD_NAME"),
                "api_key",    System.getenv("CLOUDINARY_API_KEY"),
                "api_secret", System.getenv("CLOUDINARY_API_SECRET")
        ));
    }
}
