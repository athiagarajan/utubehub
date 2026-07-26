package com.utubehub.repository;

import com.utubehub.entity.ClipEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClipRepository extends JpaRepository<ClipEntity, String> {
    List<ClipEntity> findByChannelId(String channelId);
    List<ClipEntity> findByUserId(String userId);
}
