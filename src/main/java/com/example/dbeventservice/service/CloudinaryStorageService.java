package com.example.dbeventservice.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Service
public class CloudinaryStorageService {

    private final Cloudinary cloudinary;

    public CloudinaryStorageService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    public String uploadChatImage(MultipartFile file, Long chatId, Long userId) {
        try {
            Map<?,?> res = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", "wknd/chat/" + chatId,
                            "public_id", "u" + userId + "_" + System.currentTimeMillis(),
                            "overwrite", true,
                            "resource_type", "image"
                    )
            );
            return (String) res.get("secure_url");
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload image", e);
        }
    }

    public String uploadTicketReceipt(MultipartFile file, Long eventId, Long ticketId, Long userId) {
        try {
            Map<?,?> res = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", "wknd/tickets/" + eventId + "/" + ticketId,
                            "public_id", "u" + userId + "_" + System.currentTimeMillis(),
                            "overwrite", true,
                            "resource_type", "image"
                    )
            );
            return (String) res.get("secure_url");
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload receipt", e);
        }
    }

}
