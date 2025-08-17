package com.example.dbeventservice.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.dbeventservice.dto.PhotoDTO;
import com.example.dbeventservice.model.UserPhoto;
import com.example.dbeventservice.repositories.UserPhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserPhotoService {
    private final UserPhotoRepository photos;
    private final Cloudinary cloudinary;
    private final EligibilityService eligibility;

    @Value("${photos.gate.enabled:true}")
    private boolean gateEnabled;

    public boolean canUpload(Long userId, Long eventId) {
        if (!gateEnabled) return true;
        return eligibility.hasUpcomingTicket(userId, eventId);
    }

    public PhotoDTO upload(Long userId, Long eventId, byte[] bytes, String originalFilename) throws Exception {
        if (!canUpload(userId, eventId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "NOT_ELIGIBLE");
        }
        // Upload to Cloudinary
        String publicId = UUID.randomUUID().toString();
        Map res = cloudinary.uploader().upload(bytes, ObjectUtils.asMap(
                "folder", "user_photos/" + userId,
                "public_id", publicId,
                "overwrite", true,
                "resource_type", "image"
        ));
        String url = (String) res.getOrDefault("secure_url", res.get("url"));
        if (url == null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "UPLOAD_FAILED");

        var photo = new UserPhoto();
        photo.setUserId(userId);
        photo.setEventId(eventId);
        photo.setUrl(url);
        photo.setStatus(UserPhoto.Status.APPROVED);
        photo.setCreatedAt(OffsetDateTime.now());
        var saved = photos.save(photo);

        return new PhotoDTO(saved.getId(), saved.getUrl(), saved.getEventId(),
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(saved.getCreatedAt()));
    }

    public List<PhotoDTO> userPhotos(Long userId) {
        var fmt = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        return photos.findByUserIdAndStatusOrderByCreatedAtDesc(userId, UserPhoto.Status.APPROVED)
                .stream()
                .map(p -> new PhotoDTO(p.getId(), p.getUrl(), p.getEventId(), fmt.format(p.getCreatedAt())))
                .toList();
    }
}
