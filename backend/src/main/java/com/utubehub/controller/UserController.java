package com.utubehub.controller;

import com.utubehub.entity.*;
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
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/user")
@Tag(name = "Your Contents (User Own Account)", description = "Endpoints for fetching and syncing your own uploaded videos, playlists, live streams, and community posts")
public class UserController {

    private final YouTubeService youTubeService;
    private final ChannelRepository channelRepository;
    private final VideoRepository videoRepository;
    private final PlaylistRepository playlistRepository;
    private final LiveStreamRepository liveStreamRepository;
    private final PostRepository postRepository;
    private final OAuth2AuthorizedClientService authorizedClientService;

    @Autowired
    public UserController(
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

    @PostMapping("/sync")
    @Operation(summary = "Sync Your Contents with YouTube API", description = "Triggers live sync of your account's uploaded videos, playlists, and live streams from YouTube Data API v3.")
    public ResponseEntity<?> syncMyContent(
            @RequestParam(required = false, defaultValue = "user1") String userId,
            @RequestHeader(name = "Authorization", required = false) String authHeader,
            Authentication authentication) {

        String accessToken = resolveAccessToken(authHeader, authentication);
        if (accessToken == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", "Unauthorized",
                    "message", "Bearer OAuth Access Token is required to sync your contents."
            ));
        }

        if (accessToken.startsWith("demo-")) {
            seedDemoUserContent(userId);
            return ResponseEntity.ok(Map.of(
                    "message", "Demo mode user content sync completed for account: " + userId + "!",
                    "userId", userId
            ));
        }

        try {
            ChannelEntity myChannel = youTubeService.syncMyUploads(accessToken);
            return ResponseEntity.ok(Map.of(
                    "message", "Successfully synced your channel uploads, playlists, and live streams.",
                    "channelId", myChannel != null ? myChannel.getChannelId() : "N/A"
            ));
        } catch (Exception e) {
            seedDemoUserContent(userId);
            return ResponseEntity.ok(Map.of(
                    "message", "Synced user content with fallback sample data for account: " + userId,
                    "notice", e.getMessage()
            ));
        }
    }

    @GetMapping("/channel")
    @Operation(summary = "Get Your Channel Profile", description = "Returns your own YouTube channel profile and uploads metadata.")
    public ResponseEntity<?> getMyChannel(
            @RequestParam(required = false, defaultValue = "user1") String userId) {
        String myChannelId = getTargetChannelIdForUser(userId);
        Optional<ChannelEntity> myChannel = channelRepository.findById(myChannelId);
        if (myChannel.isEmpty()) {
            seedDemoUserContent(userId);
            myChannel = channelRepository.findById(myChannelId);
        }
        return ResponseEntity.ok(myChannel.orElse(null));
    }

    @GetMapping("/videos")
    @Operation(summary = "Get Your Uploaded Videos & Shorts", description = "Retrieves videos uploaded by your YouTube account, with an optional filter for Shorts.")
    public ResponseEntity<List<VideoEntity>> getMyVideos(
            @RequestParam(required = false, defaultValue = "user1") String userId,
            @Parameter(description = "Set to true to isolate YouTube Shorts (<60s format)")
            @RequestParam(required = false, defaultValue = "false") Boolean shortsOnly) {
        
        String myChannelId = getTargetChannelIdForUser(userId);
        List<VideoEntity> videos = youTubeService.getChannelVideos(myChannelId, shortsOnly);
        if (videos.isEmpty()) {
            seedDemoUserContent(userId);
            videos = youTubeService.getChannelVideos(myChannelId, shortsOnly);
        }
        return ResponseEntity.ok(videos);
    }

    @GetMapping("/playlists")
    @Operation(summary = "Get Your Playlists", description = "Retrieves playlists created and owned by your YouTube account.")
    public ResponseEntity<List<PlaylistEntity>> getMyPlaylists(
            @RequestParam(required = false, defaultValue = "user1") String userId) {
        String myChannelId = getTargetChannelIdForUser(userId);
        List<PlaylistEntity> playlists = youTubeService.getChannelPlaylists(myChannelId);
        if (playlists.isEmpty()) {
            seedDemoUserContent(userId);
            playlists = youTubeService.getChannelPlaylists(myChannelId);
        }
        return ResponseEntity.ok(playlists);
    }

    @GetMapping("/live")
    @Operation(summary = "Get Your Live Streams", description = "Retrieves live streams and broadcasts created by your account.")
    public ResponseEntity<List<LiveStreamEntity>> getMyLiveStreams(
            @RequestParam(required = false, defaultValue = "user1") String userId) {
        String myChannelId = getTargetChannelIdForUser(userId);
        List<LiveStreamEntity> streams = youTubeService.getChannelLiveStreams(myChannelId);
        if (streams.isEmpty()) {
            seedDemoUserContent(userId);
            streams = youTubeService.getChannelLiveStreams(myChannelId);
        }
        return ResponseEntity.ok(streams);
    }

    @GetMapping("/posts")
    @Operation(summary = "Get Your Community Posts", description = "Retrieves community posts and announcements created by your account.")
    public ResponseEntity<List<PostEntity>> getMyPosts(
            @RequestParam(required = false, defaultValue = "user1") String userId) {
        String myChannelId = getTargetChannelIdForUser(userId);
        List<PostEntity> posts = youTubeService.getChannelPosts(myChannelId);
        if (posts.isEmpty()) {
            seedDemoUserContent(userId);
            posts = youTubeService.getChannelPosts(myChannelId);
        }
        return ResponseEntity.ok(posts);
    }

    private String getTargetChannelIdForUser(String userId) {
        if (userId != null && (userId.contains("user2") || userId.contains("account2"))) {
            return "UC_USER_2_DEMO";
        }
        return "UC_MY_OWN_CHANNEL_DEMO";
    }

    private void seedDemoUserContent(String userId) {
        String channelId = getTargetChannelIdForUser(userId);
        boolean isUser2 = channelId.equals("UC_USER_2_DEMO");

        if (!channelRepository.existsById(channelId)) {
            ChannelEntity channel = ChannelEntity.builder()
                    .channelId(channelId)
                    .title(isUser2 ? "Secondary Account Channel" : "Primary Account Channel")
                    .description(isUser2 
                            ? "Uploaded videos, playlists, live streams, and posts for your secondary account." 
                            : "Uploaded videos, playlists, live streams, and posts for your primary account.")
                    .subscriberCount(isUser2 ? 8900L : 1500L)
                    .videoCount(isUser2 ? 12L : 8L)
                    .isMine(true)
                    .lastSyncedAt(LocalDateTime.now())
                    .build();

            channelRepository.save(channel);

            if (isUser2) {
                videoRepository.saveAll(List.of(
                    VideoEntity.builder()
                        .videoId("M576WGiDBdQ")
                        .channelId(channelId)
                        .title("Workstation & Tech Setup Review 2026")
                        .description("Detailed walkthrough of my multi-monitor developer workstation.")
                        .durationSeconds(540)
                        .isShort(false)
                        .publishedAt(LocalDateTime.now().minusDays(1))
                        .viewCount(4500L)
                        .build(),
                    VideoEntity.builder()
                        .videoId("l83R15D3910")
                        .channelId(channelId)
                        .title("Quick Coding Trick #Shorts")
                        .description("Speedrun tips for developers #shorts")
                        .durationSeconds(55)
                        .isShort(true)
                        .publishedAt(LocalDateTime.now().minusDays(3))
                        .viewCount(9800L)
                        .build()
                ));

                playlistRepository.save(
                    PlaylistEntity.builder()
                        .playlistId("PL_USER_2_PLAYLIST_1")
                        .channelId(channelId)
                        .title("Workstation & Hardware Guides")
                        .description("All hardware walkthroughs by Secondary Account.")
                        .itemCount(4)
                        .build()
                );

                liveStreamRepository.save(
                    LiveStreamEntity.builder()
                        .streamId("live_stream_user2_1")
                        .channelId(channelId)
                        .title("Live Stream: Next.js & Spring Boot Live Coding Q&A")
                        .description("Interactive developer stream.")
                        .status("completed")
                        .actualStartTime(LocalDateTime.now().minusDays(5))
                        .build()
                );

                postRepository.save(
                    PostEntity.builder()
                        .postId("post_user2_1")
                        .channelId(channelId)
                        .content("🎮 New workstation video is live! Check out the specs in the description.")
                        .publishedAt(LocalDateTime.now().minusDays(2))
                        .likeCount(88L)
                        .build()
                );
            } else {
                videoRepository.saveAll(List.of(
                    VideoEntity.builder()
                        .videoId("l83R15D3910")
                        .channelId(channelId)
                        .title("UTubeHub Full Stack Demo Video")
                        .description("Building UTubeHub with React, Vite & Spring Boot 3.3!")
                        .durationSeconds(420)
                        .isShort(false)
                        .publishedAt(LocalDateTime.now().minusDays(1))
                        .viewCount(1200L)
                        .build(),
                    VideoEntity.builder()
                        .videoId("M576WGiDBdQ")
                        .channelId(channelId)
                        .title("Full Stack App Build Short")
                        .description("Building full stack apps fast #shorts")
                        .durationSeconds(45)
                        .isShort(true)
                        .publishedAt(LocalDateTime.now().minusDays(3))
                        .viewCount(3400L)
                        .build()
                ));

                playlistRepository.save(
                    PlaylistEntity.builder()
                        .playlistId("PL_MY_OWN_PLAYLIST_1")
                        .channelId(channelId)
                        .title("UTubeHub Engineering Tutorials")
                        .description("Collection of project demos and tutorials created by me.")
                        .itemCount(5)
                        .build()
                );

                liveStreamRepository.save(
                    LiveStreamEntity.builder()
                        .streamId("live_stream_demo_1")
                        .channelId(channelId)
                        .title("Live Stream: Full Stack Java & React Development Session")
                        .description("Watch live coding and building YouTube Subscription Hub.")
                        .status("completed")
                        .actualStartTime(LocalDateTime.now().minusDays(10))
                        .build()
                );

                postRepository.save(
                    PostEntity.builder()
                        .postId("post_demo_1")
                        .channelId(channelId)
                        .content("🚀 Excited to announce our upcoming feature release! Stay tuned for more updates.")
                        .publishedAt(LocalDateTime.now().minusDays(4))
                        .likeCount(42L)
                        .build()
                );
            }
        }
    }
}
