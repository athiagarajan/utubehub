package com.utubehub.repository;

import com.utubehub.entity.ChannelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChannelRepository extends JpaRepository<ChannelEntity, String> {
    List<ChannelEntity> findByUserId(String userId);
    Optional<ChannelEntity> findByUserIdAndIsMineTrue(String userId);
    Optional<ChannelEntity> findByIsMineTrue();
}
