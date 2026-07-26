package com.utubehub.repository;

import com.utubehub.entity.CourseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseRepository extends JpaRepository<CourseEntity, String> {
    List<CourseEntity> findByChannelId(String channelId);
    List<CourseEntity> findByUserId(String userId);
}
