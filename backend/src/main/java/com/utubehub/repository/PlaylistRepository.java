package com.utubehub.repository;

import com.utubehub.entity.PlaylistEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlaylistRepository extends JpaRepository<PlaylistEntity, String> {
    List<PlaylistEntity> findByChannelId(String channelId);
    List<PlaylistEntity> findByUserId(String userId);
}
