package com.utubehub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "clips")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClipEntity {

    @Id
    @Column(name = "clip_id")
    private String clipId;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "channel_id", nullable = false)
    private String channelId;

    @Column(name = "video_id", nullable = false)
    private String videoId;

    @Column(nullable = false, length = 512)
    private String title;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;
}
