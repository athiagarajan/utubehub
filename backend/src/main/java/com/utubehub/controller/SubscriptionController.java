package com.utubehub.controller;

import com.utubehub.entity.*;
import com.utubehub.repository.*;
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
@Tag(name = "YouTube Subscriptions", description = "Endpoints for retrieving subscriptions, user own videos, channel videos, shorts, playlists, live, podcasts, posts, and syncing with YouTube API")
public class SubscriptionController {

    private final YouTubeService youTubeService;
    private final ChannelRepository channelRepository;
    private final VideoRepository videoRepository;
    private final PlaylistRepository playlistRepository;
    private final LiveStreamRepository liveStreamRepository;
    private final PostRepository postRepository;
    private final PodcastRepository podcastRepository;
    private final CourseRepository courseRepository;
    private final ClipRepository clipRepository;
    private final OAuth2AuthorizedClientService authorizedClientService;

    @Autowired
    public SubscriptionController(
            YouTubeService youTubeService,
            ChannelRepository channelRepository,
            VideoRepository videoRepository,
            PlaylistRepository playlistRepository,
            LiveStreamRepository liveStreamRepository,
            PostRepository postRepository,
            PodcastRepository podcastRepository,
            CourseRepository courseRepository,
            ClipRepository clipRepository,
            OAuth2AuthorizedClientService authorizedClientService) {
        this.youTubeService = youTubeService;
        this.channelRepository = channelRepository;
        this.videoRepository = videoRepository;
        this.playlistRepository = playlistRepository;
        this.liveStreamRepository = liveStreamRepository;
        this.postRepository = postRepository;
        this.podcastRepository = podcastRepository;
        this.courseRepository = courseRepository;
        this.clipRepository = clipRepository;
        this.authorizedClientService = authorizedClientService;
    }

    private String resolveAccessToken(String authHeader, Authentication authentication, String userId) {
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
        if (userId != null) {
            String registryToken = com.utubehub.config.OAuthTokenRegistry.getToken(userId);
            if (registryToken != null) {
                return registryToken;
            }
        }
        return null;
    }

    @GetMapping
    @Operation(summary = "List Subscriptions for Active Account", description = "Fetches active account's own channel and subscribed YouTube channels scoped by userId.")
    public ResponseEntity<List<ChannelEntity>> getSubscriptions(
            @RequestParam(required = false, defaultValue = "athiagarajan@gmail.com") String userId,
            @RequestHeader(name = "Authorization", required = false) String authHeader,
            Authentication authentication) {

        String accessToken = resolveAccessToken(authHeader, authentication, userId);

        if (accessToken != null && !accessToken.startsWith("demo-")) {
            try {
                List<ChannelEntity> liveChannels = youTubeService.syncUserSubscriptions(accessToken, userId);
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
            @RequestParam(required = false, defaultValue = "athiagarajan@gmail.com") String userId,
            @RequestHeader(name = "Authorization", required = false) String authHeader,
            Authentication authentication) {

        String accessToken = resolveAccessToken(authHeader, authentication, userId);
        if (accessToken == null) {
            seedDemoDataForUser(userId);
            return ResponseEntity.ok(Map.of(
                    "message", "Synced demo channel data for account: " + userId + "! Log in with Google to sync live YouTube API subscriptions.",
                    "channelsSynced", 3
            ));
        }

        if (accessToken.startsWith("demo-")) {
            seedDemoDataForUser(userId);
            return ResponseEntity.ok(Map.of(
                    "message", "Demo mode sync completed for account: " + userId + "!",
                    "channelsSynced", 3
            ));
        }

        try {
            List<ChannelEntity> synced = youTubeService.syncUserSubscriptions(accessToken, userId);
            return ResponseEntity.ok(Map.of(
                    "message", "Successfully synced " + synced.size() + " subscriptions for account: " + userId,
                    "channelsSynced", synced.size()
            ));
        } catch (Exception e) {
            seedDemoDataForUser(userId);
            return ResponseEntity.ok(Map.of(
                    "message", "Synced sample data for account: " + userId,
                    "notice", e.getMessage()
            ));
        }
    }

    @GetMapping("/{channelId}/videos")
    @Operation(summary = "Get Channel Videos & Shorts", description = "Retrieves videos for a specific channel, with an optional filter for Shorts.")
    public ResponseEntity<List<VideoEntity>> getChannelVideos(
            @PathVariable String channelId,
            @RequestParam(required = false, defaultValue = "athiagarajan@gmail.com") String userId,
            @Parameter(description = "Set to true to isolate YouTube Shorts (<60s format)")
            @RequestParam(required = false, defaultValue = "false") Boolean shortsOnly,
            @RequestHeader(name = "Authorization", required = false) String authHeader,
            Authentication authentication) {

        List<VideoEntity> videos = youTubeService.getChannelVideos(channelId, shortsOnly);
        if (videos.isEmpty()) {
            String accessToken = resolveAccessToken(authHeader, authentication, userId);
            if (accessToken != null && !accessToken.startsWith("demo-")) {
                try {
                    youTubeService.syncChannelContent(accessToken, channelId, userId);
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
            @RequestParam(required = false, defaultValue = "athiagarajan@gmail.com") String userId) {

        List<PlaylistEntity> playlists = playlistRepository.findByChannelId(channelId);
        if (playlists.isEmpty()) {
            seedDemoDataForUser(userId);
            playlists = playlistRepository.findByChannelId(channelId);
        }
        return ResponseEntity.ok(playlists);
    }

    @GetMapping("/{channelId}/live")
    @Operation(summary = "Get Channel Live Streams", description = "Retrieves live streams and broadcasts created by a specific channel.")
    public ResponseEntity<List<LiveStreamEntity>> getChannelLiveStreams(
            @PathVariable String channelId,
            @RequestParam(required = false, defaultValue = "athiagarajan@gmail.com") String userId) {

        List<LiveStreamEntity> streams = liveStreamRepository.findByChannelId(channelId);
        if (streams.isEmpty()) {
            seedDemoDataForUser(userId);
            streams = liveStreamRepository.findByChannelId(channelId);
        }
        return ResponseEntity.ok(streams);
    }

    @GetMapping("/{channelId}/podcasts")
    @Operation(summary = "Get Channel Podcasts", description = "Retrieves podcast series created by a specific channel.")
    public ResponseEntity<List<PodcastEntity>> getChannelPodcasts(
            @PathVariable String channelId,
            @RequestParam(required = false, defaultValue = "athiagarajan@gmail.com") String userId) {

        List<PodcastEntity> podcasts = podcastRepository.findByChannelId(channelId);
        if (podcasts.isEmpty()) {
            seedDemoDataForUser(userId);
            podcasts = podcastRepository.findByChannelId(channelId);
        }
        return ResponseEntity.ok(podcasts);
    }

    @GetMapping("/{channelId}/posts")
    @Operation(summary = "Get Channel Posts", description = "Retrieves community posts and announcements created by a specific channel.")
    public ResponseEntity<List<PostEntity>> getChannelPosts(
            @PathVariable String channelId,
            @RequestParam(required = false, defaultValue = "athiagarajan@gmail.com") String userId) {

        List<PostEntity> posts = postRepository.findByChannelId(channelId);
        if (posts.isEmpty()) {
            seedDemoDataForUser(userId);
            posts = postRepository.findByChannelId(channelId);
        }
        return ResponseEntity.ok(posts);
    }

    public void seedDemoDataForUser(String userId) {
        String cleanEmail = (userId != null && !userId.isBlank()) ? userId.trim() : "athiagarajan@gmail.com";
        String userSlug = cleanEmail.replaceAll("[^a-zA-Z0-9]", "_").toLowerCase();
        String ownChannelId = "UC_OWN_" + userSlug.toUpperCase();
        String sub1ChannelId = "UC_SUB1_" + userSlug.toUpperCase();
        String sub2ChannelId = "UC_SUB2_" + userSlug.toUpperCase();

        if (!channelRepository.existsById(ownChannelId)) {
            ChannelEntity ownChannel = ChannelEntity.builder()
                    .channelId(ownChannelId)
                    .userId(cleanEmail)
                    .title(cleanEmail + "'s YouTube Channel")
                    .description("Uploaded videos, playlists, live streams, courses, clips, and community posts for " + cleanEmail)
                    .subscriberCount(5200L)
                    .videoCount(8L)
                    .isMine(true)
                    .lastSyncedAt(LocalDateTime.now())
                    .build();

            ChannelEntity subChannel1 = ChannelEntity.builder()
                    .channelId(sub1ChannelId)
                    .userId(cleanEmail)
                    .title("Google Developers")
                    .description("Official Google Developers channel with the latest tutorials and news.")
                    .subscriberCount(2450000L)
                    .videoCount(5230L)
                    .isMine(false)
                    .lastSyncedAt(LocalDateTime.now())
                    .build();

            ChannelEntity subChannel2 = ChannelEntity.builder()
                    .channelId(sub2ChannelId)
                    .userId(cleanEmail)
                    .title("Fireship")
                    .description("High-intensity code tutorials to help you build apps faster.")
                    .subscriberCount(3100000L)
                    .videoCount(650L)
                    .isMine(false)
                    .lastSyncedAt(LocalDateTime.now())
                    .build();

            channelRepository.saveAll(List.of(ownChannel, subChannel1, subChannel2));

            // Seed Uploaded Videos
            videoRepository.saveAll(List.of(
                VideoEntity.builder()
                    .videoId("video_upload_1_" + userSlug)
                    .userId(cleanEmail)
                    .channelId(ownChannelId)
                    .title("UTubeHub Multi-User Architecture & Spring Boot Demo")
                    .description("Full walkthrough of multi-user Google OAuth integration & YouTube API Hub!")
                    .durationSeconds(420)
                    .isShort(false)
                    .publishedAt(LocalDateTime.now().minusDays(1))
                    .viewCount(1850L)
                    .build(),
                VideoEntity.builder()
                    .videoId("video_upload_2_" + userSlug)
                    .userId(cleanEmail)
                    .channelId(ownChannelId)
                    .title("Full Stack React 18 & Spring Boot 3 #shorts")
                    .description("Building full stack microservices fast #shorts")
                    .durationSeconds(45)
                    .isShort(true)
                    .publishedAt(LocalDateTime.now().minusDays(3))
                    .viewCount(4200L)
                    .build(),
                VideoEntity.builder()
                    .videoId("video_sub_1_" + userSlug)
                    .userId(cleanEmail)
                    .channelId(sub1ChannelId)
                    .title("Google I/O 2026 Developer Keynote Highlights")
                    .description("Featured science & technology presentation from Google I/O.")
                    .durationSeconds(1200)
                    .isShort(false)
                    .publishedAt(LocalDateTime.now().minusDays(2))
                    .viewCount(1500000L)
                    .build()
            ));

            // Seed Playlists
            playlistRepository.saveAll(List.of(
                PlaylistEntity.builder()
                    .playlistId("playlist_1_" + userSlug)
                    .userId(cleanEmail)
                    .channelId(ownChannelId)
                    .title("UTubeHub Engineering & Architecture Tutorials")
                    .description("Collection of project tutorials created for " + cleanEmail)
                    .itemCount(5)
                    .build(),
                PlaylistEntity.builder()
                    .playlistId("playlist_sub_1_" + userSlug)
                    .userId(cleanEmail)
                    .channelId(sub1ChannelId)
                    .title("Google Cloud & GenAI Masterclass")
                    .description("Official tutorials from Google Developers")
                    .itemCount(12)
                    .build()
            ));

            // Seed Live Streams
            liveStreamRepository.saveAll(List.of(
                LiveStreamEntity.builder()
                    .streamId("stream_1_" + userSlug)
                    .userId(cleanEmail)
                    .channelId(ownChannelId)
                    .title("Live Stream: Full Stack Development & Google OAuth Q&A")
                    .description("Interactive coding stream for " + cleanEmail)
                    .status("completed")
                    .actualStartTime(LocalDateTime.now().minusDays(5))
                    .build(),
                LiveStreamEntity.builder()
                    .streamId("stream_sub_1_" + userSlug)
                    .userId(cleanEmail)
                    .channelId(sub1ChannelId)
                    .title("Google Developers Live: Spring Boot & Cloud AI")
                    .description("Live interactive broadcast from Google I/O")
                    .status("completed")
                    .actualStartTime(LocalDateTime.now().minusDays(10))
                    .build()
            ));

            // Seed Podcasts
            podcastRepository.saveAll(List.of(
                PodcastEntity.builder()
                    .podcastId("podcast_sub_1_" + userSlug)
                    .userId(cleanEmail)
                    .channelId(sub1ChannelId)
                    .title("The Google Developer Podcast")
                    .description("Deep dives into modern web engineering and cloud systems.")
                    .episodeCount(42)
                    .build(),
                PodcastEntity.builder()
                    .podcastId("podcast_sub_2_" + userSlug)
                    .userId(cleanEmail)
                    .channelId(sub2ChannelId)
                    .title("The Fireship Tech Talk Podcast")
                    .description("Weekly discussions on frontend frameworks and backend databases.")
                    .episodeCount(18)
                    .build()
            ));

            // Seed Community Posts
            postRepository.saveAll(List.of(
                PostEntity.builder()
                    .postId("post_1_" + userSlug)
                    .userId(cleanEmail)
                    .channelId(ownChannelId)
                    .content("🚀 Welcome to " + cleanEmail + "'s official channel updates & announcements!")
                    .publishedAt(LocalDateTime.now().minusDays(2))
                    .likeCount(88L)
                    .build(),
                PostEntity.builder()
                    .postId("post_sub_1_" + userSlug)
                    .userId(cleanEmail)
                    .channelId(sub1ChannelId)
                    .content("📢 We just published our new Spring Boot 3.3 and React 18 integration guide! Check it out.")
                    .publishedAt(LocalDateTime.now().minusDays(4))
                    .likeCount(412L)
                    .build()
            ));

            // Seed Courses
            courseRepository.save(
                CourseEntity.builder()
                    .courseId("course_1_" + userSlug)
                    .userId(cleanEmail)
                    .channelId(ownChannelId)
                    .title("Complete Full-Stack Java & React Engineering Course")
                    .description("Structured 10-module course covering Spring Boot 3.3, OAuth2, and Vite React 18.")
                    .lessonCount(24)
                    .build()
            );

            // Seed Clips
            clipRepository.save(
                ClipEntity.builder()
                    .clipId("clip_1_" + userSlug)
                    .userId(cleanEmail)
                    .channelId(ownChannelId)
                    .videoId("video_upload_1_" + userSlug)
                    .title("Highlight: Setting up Google OIDC Security in Spring Boot")
                    .durationSeconds(30)
                    .publishedAt(LocalDateTime.now().minusDays(1))
                    .build()
            );
        }
    }
}
