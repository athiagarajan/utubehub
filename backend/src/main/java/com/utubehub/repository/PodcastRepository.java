package com.utubehub.repository;

import com.utubehub.entity.PodcastEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PodcastRepository extends JpaRepository<PodcastEntity, String> {
    List<PodcastEntity> findByChannelId(String channelId);
    List<PodcastEntity> findByUserId(String userId);
}
