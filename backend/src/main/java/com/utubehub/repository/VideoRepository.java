package com.utubehub.repository;

import com.utubehub.entity.VideoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VideoRepository extends JpaRepository<VideoEntity, String> {
    List<VideoEntity> findByChannelId(String channelId);
    List<VideoEntity> findByChannelIdAndIsShortTrue(String channelId);
    List<VideoEntity> findByUserId(String userId);
    List<VideoEntity> findByUserIdAndIsShortTrue(String userId);
}
