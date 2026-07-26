package com.utubehub.repository;

import com.utubehub.entity.LiveStreamEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LiveStreamRepository extends JpaRepository<LiveStreamEntity, String> {
    List<LiveStreamEntity> findByChannelId(String channelId);
    List<LiveStreamEntity> findByUserId(String userId);
}
