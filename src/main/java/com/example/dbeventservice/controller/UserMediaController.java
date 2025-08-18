package com.example.dbeventservice.controller;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.dbeventservice.auth.jwt.UserDetailsImpl;
import com.example.dbeventservice.model.User;
import com.example.dbeventservice.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserMediaController {
    private final Cloudinary cloudinary;
    private final UserRepository userRepo;


    @PostMapping(value="/api/users/me/avatar", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadAvatar(@AuthenticationPrincipal UserDetailsImpl me,
                                                            @RequestParam("file") MultipartFile file) {
        try {
            final long MAX = 20L * 1024 * 1024;
            final var ALLOWED = Set.of("image/jpeg","image/png","image/webp");

            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error","Empty file"));
            }
            String ct = (file.getContentType() == null ? "" : file.getContentType().toLowerCase());
            if (!ALLOWED.contains(ct)) {
                return ResponseEntity.badRequest().body(Map.of("error","Unsupported type"));
            }
            if (file.getSize() > MAX) {
                return ResponseEntity.status(413).body(Map.of("error","Max 20MB"));
            }

            var result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", "avatars",
                            "resource_type", "image",
                            // keep one avatar per user; overwrites old one
                            "public_id", "user_" + me.getId(),
                            "overwrite", true,
                            "use_filename", true
                    )
            );
            String secureUrl = (String) result.get("secure_url");

            User u = userRepo.findById(me.getId()).orElseThrow();
            u.setAvatarUrl(secureUrl);
            userRepo.save(u);

            return ResponseEntity.ok(Map.of("avatarUrl", secureUrl));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error","Image upload failed"));
        }
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String,String>> tooBig(Exception ex) {
        return ResponseEntity.status(413).body(Map.of("error","Maximum upload size exceeded"));
    }

    @PatchMapping("/bio")
    public ResponseEntity<?> updateBio(@AuthenticationPrincipal UserDetailsImpl me,
                                       @RequestBody Map<String, String> payload) {
        var user = userRepo.findById(me.getId()).orElseThrow();
        user.setBio(payload.getOrDefault("bio", ""));
        userRepo.save(user);
        return ResponseEntity.ok().build();
    }
}
