package com.utubehub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "posts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostEntity {

    @Id
    @Column(name = "post_id")
    private String postId;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "channel_id", nullable = false)
    private String channelId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "like_count")
    private Long likeCount;
}
