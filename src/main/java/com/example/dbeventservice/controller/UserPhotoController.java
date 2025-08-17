package com.example.dbeventservice.controller;

import com.example.dbeventservice.auth.jwt.UserDetailsImpl;
import com.example.dbeventservice.dto.PhotoDTO;
import com.example.dbeventservice.service.UserPhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/photos")
@RequiredArgsConstructor
public class UserPhotoController {
    private final UserPhotoService svc;

    /** Raw boolean `true/false` is fine for iOS Bool decoding */
    @GetMapping("/eligibility/{eventId}")
    public boolean canUpload(@AuthenticationPrincipal UserDetailsImpl me, @PathVariable Long eventId) {
        return svc.canUpload(me.getId(), eventId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PhotoDTO upload(@AuthenticationPrincipal UserDetailsImpl me,
                           @RequestPart("file") MultipartFile file,
                           @RequestPart("eventId") Long eventId) throws Exception {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("FILE_REQUIRED");
        if (file.getSize() > 10_000_000L) throw new IllegalArgumentException("FILE_TOO_LARGE"); // 10MB
        return svc.upload(me.getId(), eventId, file.getBytes(), file.getOriginalFilename());
    }

    @GetMapping("/user/{userId}")
    public List<PhotoDTO> userPhotos(@PathVariable Long userId) {
        return svc.userPhotos(userId);
    }

    @GetMapping("/me")
    public List<PhotoDTO> myPhotos(@AuthenticationPrincipal UserDetailsImpl me) {
        return svc.userPhotos(me.getId());
    }
}
