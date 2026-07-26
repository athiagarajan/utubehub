package com.utubehub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "channels")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChannelEntity {

    @Id
    @Column(name = "channel_id")
    private String channelId;

    @Column(name = "user_id")
    private String userId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "thumbnail_url", length = 1024)
    private String thumbnailUrl;

    @Column(name = "subscriber_count")
    private Long subscriberCount;

    @Column(name = "video_count")
    private Long videoCount;

    @Column(name = "uploads_playlist_id")
    private String uploadsPlaylistId;

    @Column(name = "is_mine")
    private Boolean isMine;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;
}
