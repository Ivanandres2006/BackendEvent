package com.example.dbeventservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:./uploads/");

        // ✅ Match the QR code output directory
        registry.addResourceHandler("/qr/**")
                .addResourceLocations("file:./uploads/qr/");

        registry.addResourceHandler("/api/chat/image/**")
                .addResourceLocations("file:./chat-images/");
    }



}
