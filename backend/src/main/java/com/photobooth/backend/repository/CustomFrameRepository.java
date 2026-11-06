package com.photobooth.backend.repository;

import com.photobooth.backend.entity.CustomFrame;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomFrameRepository extends JpaRepository<CustomFrame, Long> {
    List<CustomFrame> findAllByOrderByCreatedAtDesc();
}

