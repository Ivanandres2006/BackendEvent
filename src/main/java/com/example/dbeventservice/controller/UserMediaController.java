package com.example.dbeventservice.controller;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.dbeventservice.auth.jwt.UserDetailsImpl;
import com.example.dbeventservice.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserMediaController {
    private final Cloudinary cloudinary;
    private final UserRepository userRepo;

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadAvatar(@AuthenticationPrincipal UserDetailsImpl me,
                                          @RequestPart("file") MultipartFile file) {
        try {
            Map<?, ?> res = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", "avatars",
                    "overwrite", true,
                    "public_id", "user_" + me.getId()
            ));
            String url = (String) res.get("secure_url");
            var user = userRepo.findById(me.getId()).orElseThrow();
            user.setAvatarUrl(url);
            userRepo.save(user);
            return ResponseEntity.ok(url);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Upload failed");
        }
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
