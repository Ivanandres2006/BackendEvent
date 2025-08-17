package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.UserPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserPhotoRepository extends JpaRepository<UserPhoto, Long> {
    List<UserPhoto> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, UserPhoto.Status status);
}
