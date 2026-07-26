package com.utubehub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "courses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseEntity {

    @Id
    @Column(name = "course_id")
    private String courseId;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "channel_id", nullable = false)
    private String channelId;

    @Column(nullable = false, length = 512)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "lesson_count")
    private Integer lessonCount;

    @Column(name = "thumbnail_url", length = 1024)
    private String thumbnailUrl;
}
