package com.utubehub.controller;

import com.utubehub.entity.ChannelEntity;
import com.utubehub.entity.PlaylistEntity;
import com.utubehub.entity.VideoEntity;
import com.utubehub.repository.ChannelRepository;
import com.utubehub.repository.PlaylistRepository;
import com.utubehub.repository.VideoRepository;
import com.utubehub.service.YouTubeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/subscriptions")
@Tag(name = "YouTube Subscriptions", description = "Endpoints for retrieving subscriptions, channel videos, shorts, playlists, and syncing with YouTube API")
public class SubscriptionController {

    private final YouTubeService youTubeService;
    private final ChannelRepository channelRepository;
    private final VideoRepository videoRepository;
    private final PlaylistRepository playlistRepository;

    @Autowired
    public SubscriptionController(
            YouTubeService youTubeService,
            ChannelRepository channelRepository,
            VideoRepository videoRepository,
            PlaylistRepository playlistRepository) {
        this.youTubeService = youTubeService;
        this.channelRepository = channelRepository;
        this.videoRepository = videoRepository;
        this.playlistRepository = playlistRepository;
    }

    @GetMapping
    @Operation(summary = "List All Subscriptions", description = "Fetches all user subscribed YouTube channels cached in local PostgreSQL database.")
    public ResponseEntity<List<ChannelEntity>> getSubscriptions() {
        List<ChannelEntity> channels = youTubeService.getLocalSubscriptions();
        if (channels.isEmpty()) {
            // Seed initial demo channels for instant browsing
            ChannelEntity devChannel = ChannelEntity.builder()
                    .channelId("UC_x5XG1OV2P6uZZ5FSM9Ttw")
                    .title("Google Developers")
                    .description("Official Google Developers channel featuring tech talks, tutorials, and keynotes.")
                    .subscriberCount(2450000L)
                    .videoCount(5230L)
                    .lastSyncedAt(LocalDateTime.now())
                    .build();

            ChannelEntity fireshipChannel = ChannelEntity.builder()
                    .channelId("UCsBjURrP6M6nO6jC11p9xGA")
                    .title("Fireship")
                    .description("High-intensity code tutorials and tech news to help you build apps faster.")
                    .subscriberCount(3100000L)
                    .videoCount(650L)
                    .lastSyncedAt(LocalDateTime.now())
                    .build();

            channelRepository.saveAll(List.of(devChannel, fireshipChannel));

            // Seed sample videos for playability
            videoRepository.saveAll(List.of(
                VideoEntity.builder()
                    .videoId("l83R15D3910")
                    .channelId("UC_x5XG1OV2P6uZZ5FSM9Ttw")
                    .title("Google I/O 2026 Keynote")
                    .description("Watch the official announcements from Google I/O.")
                    .durationSeconds(7200)
                    .isShort(false)
                    .publishedAt(LocalDateTime.now().minusDays(2))
                    .viewCount(1500000L)
                    .build(),
                VideoEntity.builder()
                    .videoId("M576WGiDBdQ")
                    .channelId("UCsBjURrP6M6nO6jC11p9xGA")
                    .title("React 19 in 100 Seconds")
                    .description("A fast breakdown of React 19 features.")
                    .durationSeconds(130)
                    .isShort(true)
                    .publishedAt(LocalDateTime.now().minusDays(5))
                    .viewCount(850000L)
                    .build()
            ));

            // Seed sample playlists
            playlistRepository.save(
                PlaylistEntity.builder()
                    .playlistId("PLOU2XLYxmsIKC8eODk_LrhLnlpe25880-")
                    .channelId("UC_x5XG1OV2P6uZZ5FSM9Ttw")
                    .title("Spring Boot & Cloud Native Java")
                    .description("Tutorials for building modern Java applications.")
                    .itemCount(24)
                    .build()
            );

            return ResponseEntity.ok(channelRepository.findAll());
        }
        return ResponseEntity.ok(channels);
    }

    @PostMapping("/sync")
    @Operation(summary = "Sync Subscriptions with YouTube API", description = "Fetches live subscriptions from YouTube Data API v3 using user OAuth access token and updates local database.")
    public ResponseEntity<?> syncSubscriptions(
            @RequestHeader(name = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", "Unauthorized",
                    "message", "Bearer OAuth Access Token is required to sync with YouTube API."
            ));
        }

        String accessToken = authHeader.substring(7);
        if (accessToken.startsWith("demo-")) {
            // Seed demo channels & content for unblocked testing
            getSubscriptions();
            return ResponseEntity.ok(Map.of(
                    "message", "Demo mode sync completed! Seeded 2 channels, videos, shorts, and playlists.",
                    "channelsSynced", 2
            ));
        }

        try {
            List<ChannelEntity> synced = youTubeService.syncUserSubscriptions(accessToken);
            return ResponseEntity.ok(Map.of(
                    "message", "Successfully synced " + synced.size() + " subscriptions from YouTube API.",
                    "channelsSynced", synced.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Sync Failed",
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/{channelId}/videos")
    @Operation(summary = "Get Channel Videos & Shorts", description = "Retrieves videos for a specific subscribed channel, with an optional filter for Shorts.")
    public ResponseEntity<List<VideoEntity>> getChannelVideos(
            @PathVariable String channelId,
            @Parameter(description = "Set to true to isolate YouTube Shorts (<60s format)")
            @RequestParam(required = false, defaultValue = "false") Boolean shortsOnly) {
        getSubscriptions(); // Ensure DB seeded if empty
        return ResponseEntity.ok(youTubeService.getChannelVideos(channelId, shortsOnly));
    }

    @GetMapping("/{channelId}/playlists")
    @Operation(summary = "Get Channel Playlists", description = "Retrieves playlists created by a specific channel.")
    public ResponseEntity<List<PlaylistEntity>> getChannelPlaylists(@PathVariable String channelId) {
        getSubscriptions(); // Ensure DB seeded if empty
        return ResponseEntity.ok(youTubeService.getChannelPlaylists(channelId));
    }
}
