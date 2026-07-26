package com.utubehub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "podcasts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PodcastEntity {

    @Id
    @Column(name = "podcast_id")
    private String podcastId;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "channel_id", nullable = false)
    private String channelId;

    @Column(nullable = false, length = 512)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "episode_count")
    private Integer episodeCount;

    @Column(name = "thumbnail_url", length = 1024)
    private String thumbnailUrl;
}
