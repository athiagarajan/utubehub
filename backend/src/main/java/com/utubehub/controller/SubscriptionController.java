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
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
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
    private final OAuth2AuthorizedClientService authorizedClientService;

    @Autowired
    public SubscriptionController(
            YouTubeService youTubeService,
            ChannelRepository channelRepository,
            VideoRepository videoRepository,
            PlaylistRepository playlistRepository,
            OAuth2AuthorizedClientService authorizedClientService) {
        this.youTubeService = youTubeService;
        this.channelRepository = channelRepository;
        this.videoRepository = videoRepository;
        this.playlistRepository = playlistRepository;
        this.authorizedClientService = authorizedClientService;
    }

    private String resolveAccessToken(String authHeader, Authentication authentication) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        if (authentication instanceof OAuth2AuthenticationToken oauthToken) {
            OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                    oauthToken.getAuthorizedClientRegistrationId(),
                    oauthToken.getName());
            if (client != null && client.getAccessToken() != null) {
                return client.getAccessToken().getTokenValue();
            }
        }
        return null;
    }

    @GetMapping
    @Operation(summary = "List All Subscriptions", description = "Fetches user subscribed YouTube channels. Automatically triggers live YouTube API sync when authenticated.")
    public ResponseEntity<List<ChannelEntity>> getSubscriptions(
            @RequestHeader(name = "Authorization", required = false) String authHeader,
            Authentication authentication) {

        String accessToken = resolveAccessToken(authHeader, authentication);

        if (accessToken != null && !accessToken.startsWith("demo-")) {
            try {
                List<ChannelEntity> liveChannels = youTubeService.syncUserSubscriptions(accessToken);
                if (!liveChannels.isEmpty()) {
                    return ResponseEntity.ok(liveChannels);
                }
            } catch (Exception e) {
                System.err.println("Live sync failed: " + e.getMessage() + ". Returning cached channels.");
            }
        }

        List<ChannelEntity> cached = youTubeService.getLocalSubscriptions();
        if (cached.isEmpty()) {
            seedDemoData();
            return ResponseEntity.ok(channelRepository.findAll());
        }
        return ResponseEntity.ok(cached);
    }

    @PostMapping("/sync")
    @Operation(summary = "Sync Subscriptions with YouTube API", description = "Fetches live subscriptions from YouTube Data API v3 using user OAuth access token and updates local database.")
    public ResponseEntity<?> syncSubscriptions(
            @RequestHeader(name = "Authorization", required = false) String authHeader,
            Authentication authentication) {

        String accessToken = resolveAccessToken(authHeader, authentication);
        if (accessToken == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", "Unauthorized",
                    "message", "Bearer OAuth Access Token is required to sync with YouTube API."
            ));
        }

        if (accessToken.startsWith("demo-")) {
            seedDemoData();
            return ResponseEntity.ok(Map.of(
                    "message", "Demo mode sync completed! Seeded channels, videos, shorts, and playlists.",
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
            @RequestParam(required = false, defaultValue = "false") Boolean shortsOnly,
            @RequestHeader(name = "Authorization", required = false) String authHeader,
            Authentication authentication) {

        List<VideoEntity> videos = youTubeService.getChannelVideos(channelId, shortsOnly);
        if (videos.isEmpty()) {
            String accessToken = resolveAccessToken(authHeader, authentication);
            if (accessToken != null && !accessToken.startsWith("demo-")) {
                try {
                    youTubeService.syncChannelContent(accessToken, channelId);
                    videos = youTubeService.getChannelVideos(channelId, shortsOnly);
                } catch (Exception e) {
                    System.err.println("Channel content sync failed for " + channelId + ": " + e.getMessage());
                }
            } else if (videos.isEmpty()) {
                seedDemoData();
                videos = youTubeService.getChannelVideos(channelId, shortsOnly);
            }
        }
        return ResponseEntity.ok(videos);
    }

    @GetMapping("/{channelId}/playlists")
    @Operation(summary = "Get Channel Playlists", description = "Retrieves playlists created by a specific channel.")
    public ResponseEntity<List<PlaylistEntity>> getChannelPlaylists(
            @PathVariable String channelId,
            @RequestHeader(name = "Authorization", required = false) String authHeader,
            Authentication authentication) {

        List<PlaylistEntity> playlists = youTubeService.getChannelPlaylists(channelId);
        if (playlists.isEmpty()) {
            String accessToken = resolveAccessToken(authHeader, authentication);
            if (accessToken != null && !accessToken.startsWith("demo-")) {
                try {
                    youTubeService.syncChannelContent(accessToken, channelId);
                    playlists = youTubeService.getChannelPlaylists(channelId);
                } catch (Exception e) {
                    System.err.println("Channel playlists sync failed for " + channelId + ": " + e.getMessage());
                }
            } else if (playlists.isEmpty()) {
                seedDemoData();
                playlists = youTubeService.getChannelPlaylists(channelId);
            }
        }
        return ResponseEntity.ok(playlists);
    }

    private void seedDemoData() {
        if (!channelRepository.existsById("UC_x5XG1OV2P6uZZ5FSM9Ttw")) {
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

            playlistRepository.save(
                PlaylistEntity.builder()
                    .playlistId("PLOU2XLYxmsIKC8eODk_LrhLnlpe25880-")
                    .channelId("UC_x5XG1OV2P6uZZ5FSM9Ttw")
                    .title("Spring Boot & Cloud Native Java")
                    .description("Tutorials for building modern Java applications.")
                    .itemCount(24)
                    .build()
            );
        }
    }
}
