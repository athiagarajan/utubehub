package com.utubehub.controller;

import com.utubehub.entity.ChannelEntity;
import com.utubehub.entity.LiveStreamEntity;
import com.utubehub.entity.PlaylistEntity;
import com.utubehub.entity.PostEntity;
import com.utubehub.entity.VideoEntity;
import com.utubehub.repository.ChannelRepository;
import com.utubehub.repository.LiveStreamRepository;
import com.utubehub.repository.PlaylistRepository;
import com.utubehub.repository.PostRepository;
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
@Tag(name = "YouTube Subscriptions", description = "Endpoints for retrieving subscriptions, user own videos, channel videos, shorts, playlists, and syncing with YouTube API")
public class SubscriptionController {

    private final YouTubeService youTubeService;
    private final ChannelRepository channelRepository;
    private final VideoRepository videoRepository;
    private final PlaylistRepository playlistRepository;
    private final LiveStreamRepository liveStreamRepository;
    private final PostRepository postRepository;
    private final OAuth2AuthorizedClientService authorizedClientService;

    @Autowired
    public SubscriptionController(
            YouTubeService youTubeService,
            ChannelRepository channelRepository,
            VideoRepository videoRepository,
            PlaylistRepository playlistRepository,
            LiveStreamRepository liveStreamRepository,
            PostRepository postRepository,
            OAuth2AuthorizedClientService authorizedClientService) {
        this.youTubeService = youTubeService;
        this.channelRepository = channelRepository;
        this.videoRepository = videoRepository;
        this.playlistRepository = playlistRepository;
        this.liveStreamRepository = liveStreamRepository;
        this.postRepository = postRepository;
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
    @Operation(summary = "List Subscriptions for Active Account", description = "Fetches active account's own channel and subscribed YouTube channels scoped by userId.")
    public ResponseEntity<List<ChannelEntity>> getSubscriptions(
            @RequestParam(required = false, defaultValue = "user1@gmail.com") String userId,
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

        List<ChannelEntity> userChannels = channelRepository.findByUserId(userId);
        if (userChannels.isEmpty()) {
            seedDemoDataForUser(userId);
            userChannels = channelRepository.findByUserId(userId);
        }
        return ResponseEntity.ok(userChannels);
    }

    @PostMapping("/sync")
    @Operation(summary = "Sync Subscriptions for Active Account", description = "Fetches live subscriptions and your own uploaded videos from YouTube Data API v3 using OAuth access token.")
    public ResponseEntity<?> syncSubscriptions(
            @RequestParam(required = false, defaultValue = "user1@gmail.com") String userId,
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
            seedDemoDataForUser(userId);
            return ResponseEntity.ok(Map.of(
                    "message", "Demo mode sync completed for " + userId + "!",
                    "channelsSynced", 3
            ));
        }

        try {
            List<ChannelEntity> synced = youTubeService.syncUserSubscriptions(accessToken);
            return ResponseEntity.ok(Map.of(
                    "message", "Successfully synced " + synced.size() + " subscriptions for " + userId,
                    "channelsSynced", synced.size()
            ));
        } catch (Exception e) {
            seedDemoDataForUser(userId);
            return ResponseEntity.ok(Map.of(
                    "message", "Synced sample data for " + userId,
                    "notice", e.getMessage()
            ));
        }
    }

    @GetMapping("/{channelId}/videos")
    @Operation(summary = "Get Channel Videos & Shorts", description = "Retrieves videos for a specific channel, with an optional filter for Shorts.")
    public ResponseEntity<List<VideoEntity>> getChannelVideos(
            @PathVariable String channelId,
            @RequestParam(required = false, defaultValue = "user1@gmail.com") String userId,
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
                seedDemoDataForUser(userId);
                videos = youTubeService.getChannelVideos(channelId, shortsOnly);
            }
        }
        return ResponseEntity.ok(videos);
    }

    @GetMapping("/{channelId}/playlists")
    @Operation(summary = "Get Channel Playlists", description = "Retrieves playlists created by a specific channel.")
    public ResponseEntity<List<PlaylistEntity>> getChannelPlaylists(
            @PathVariable String channelId,
            @RequestParam(required = false, defaultValue = "user1@gmail.com") String userId,
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
                seedDemoDataForUser(userId);
                playlists = youTubeService.getChannelPlaylists(channelId);
            }
        }
        return ResponseEntity.ok(playlists);
    }

    public void seedDemoDataForUser(String userId) {
        boolean isUser2 = userId != null && (userId.contains("user2") || userId.contains("account2") || userId.contains("2"));
        String prefix = isUser2 ? "account2" : "account1";

        String ownChannelId = "UC_OWN_CHANNEL_" + prefix.toUpperCase();

        if (!channelRepository.existsById(ownChannelId)) {
            ChannelEntity ownChannel = ChannelEntity.builder()
                    .channelId(ownChannelId)
                    .userId(userId)
                    .title(prefix + " Uploaded Channel")
                    .description("Uploaded videos, playlists, live streams, and posts for " + userId)
                    .subscriberCount(isUser2 ? 9800L : 2400L)
                    .videoCount(isUser2 ? 14L : 10L)
                    .isMine(true)
                    .lastSyncedAt(LocalDateTime.now())
                    .build();

            ChannelEntity subChannel1 = ChannelEntity.builder()
                    .channelId("UC_SUB1_" + prefix.toUpperCase())
                    .userId(userId)
                    .title(isUser2 ? "Veritasium" : "Google Developers")
                    .description(isUser2 ? "An element of truth - videos about science." : "Official Google Developers channel.")
                    .subscriberCount(isUser2 ? 14500000L : 2450000L)
                    .videoCount(isUser2 ? 380L : 5230L)
                    .isMine(false)
                    .lastSyncedAt(LocalDateTime.now())
                    .build();

            ChannelEntity subChannel2 = ChannelEntity.builder()
                    .channelId("UC_SUB2_" + prefix.toUpperCase())
                    .userId(userId)
                    .title(isUser2 ? "IGN" : "Fireship")
                    .description(isUser2 ? "The latest game reviews and trailers." : "High-intensity code tutorials.")
                    .subscriberCount(isUser2 ? 17800000L : 3100000L)
                    .videoCount(isUser2 ? 45000L : 650L)
                    .isMine(false)
                    .lastSyncedAt(LocalDateTime.now())
                    .build();

            channelRepository.saveAll(List.of(ownChannel, subChannel1, subChannel2));

            // Seed Uploaded Videos
            videoRepository.saveAll(List.of(
                VideoEntity.builder()
                    .videoId("video_upload_1_" + prefix)
                    .userId(userId)
                    .channelId(ownChannelId)
                    .title(isUser2 ? "Workstation & Dual Monitor Setup Review 2026" : "UTubeHub Full Stack Architecture Demo")
                    .description(isUser2 ? "Detailed walkthrough of developer setup." : "Building YouTube Hub with Spring Boot 3.3 and React 18!")
                    .durationSeconds(isUser2 ? 540 : 420)
                    .isShort(false)
                    .publishedAt(LocalDateTime.now().minusDays(1))
                    .viewCount(isUser2 ? 4500L : 1200L)
                    .build(),
                VideoEntity.builder()
                    .videoId("video_upload_2_" + prefix)
                    .userId(userId)
                    .channelId(ownChannelId)
                    .title(isUser2 ? "Speedrun Developer Tips #shorts" : "Full Stack App Build Short #shorts")
                    .description(isUser2 ? "Quick tips for developers #shorts" : "Building full stack apps fast #shorts")
                    .durationSeconds(45)
                    .isShort(true)
                    .publishedAt(LocalDateTime.now().minusDays(3))
                    .viewCount(isUser2 ? 9800L : 3400L)
                    .build(),
                VideoEntity.builder()
                    .videoId("video_sub_1_" + prefix)
                    .userId(userId)
                    .channelId("UC_SUB1_" + prefix.toUpperCase())
                    .title(isUser2 ? "The Surprising Physics of Water" : "Google I/O 2026 Keynote")
                    .description("Featured science & tech video.")
                    .durationSeconds(1200)
                    .isShort(false)
                    .publishedAt(LocalDateTime.now().minusDays(2))
                    .viewCount(1500000L)
                    .build()
            ));

            // Seed Playlists
            playlistRepository.save(
                PlaylistEntity.builder()
                    .playlistId("playlist_1_" + prefix)
                    .userId(userId)
                    .channelId(ownChannelId)
                    .title(isUser2 ? "Hardware & Workstation Walkthroughs" : "UTubeHub Engineering Tutorials")
                    .description("Collection of project tutorials created for " + userId)
                    .itemCount(5)
                    .build()
            );

            // Seed Live Streams
            liveStreamRepository.save(
                LiveStreamEntity.builder()
                    .streamId("stream_1_" + prefix)
                    .userId(userId)
                    .channelId(ownChannelId)
                    .title(isUser2 ? "Live Stream: Next.js & Spring Boot Live Coding Q&A" : "Live Stream: Java & React Development Session")
                    .description("Interactive coding stream for " + userId)
                    .status("completed")
                    .actualStartTime(LocalDateTime.now().minusDays(5))
                    .build()
            );

            // Seed Community Posts
            postRepository.save(
                PostEntity.builder()
                    .postId("post_1_" + prefix)
                    .userId(userId)
                    .channelId(ownChannelId)
                    .content("🚀 Welcome to " + userId + "'s channel updates & project announcements!")
                    .publishedAt(LocalDateTime.now().minusDays(2))
                    .likeCount(64L)
                    .build()
            );
        }
    }
}
