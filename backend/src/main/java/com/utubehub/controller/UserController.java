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

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/user")
@Tag(name = "Your Contents (User Own Account)", description = "Endpoints for fetching and syncing your own uploaded videos, playlists, live streams, community posts, courses, and clips")
public class UserController {

    private final YouTubeService youTubeService;
    private final ChannelRepository channelRepository;
    private final VideoRepository videoRepository;
    private final PlaylistRepository playlistRepository;
    private final LiveStreamRepository liveStreamRepository;
    private final PostRepository postRepository;
    private final CourseRepository courseRepository;
    private final ClipRepository clipRepository;
    private final SubscriptionController subscriptionController;
    private final OAuth2AuthorizedClientService authorizedClientService;

    @Autowired
    public UserController(
            YouTubeService youTubeService,
            ChannelRepository channelRepository,
            VideoRepository videoRepository,
            PlaylistRepository playlistRepository,
            LiveStreamRepository liveStreamRepository,
            PostRepository postRepository,
            CourseRepository courseRepository,
            ClipRepository clipRepository,
            SubscriptionController subscriptionController,
            OAuth2AuthorizedClientService authorizedClientService) {
        this.youTubeService = youTubeService;
        this.channelRepository = channelRepository;
        this.videoRepository = videoRepository;
        this.playlistRepository = playlistRepository;
        this.liveStreamRepository = liveStreamRepository;
        this.postRepository = postRepository;
        this.courseRepository = courseRepository;
        this.clipRepository = clipRepository;
        this.subscriptionController = subscriptionController;
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
            @RequestParam(required = false, defaultValue = "athiagarajan@gmail.com") String userId,
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
            subscriptionController.seedDemoDataForUser(userId);
            return ResponseEntity.ok(Map.of(
                    "message", "Demo mode user content sync completed for account: " + userId + "!",
                    "userId", userId
            ));
        }

        try {
            ChannelEntity myChannel = youTubeService.syncMyUploads(accessToken, userId);
            return ResponseEntity.ok(Map.of(
                    "message", "Successfully synced your channel uploads, playlists, and live streams for account: " + userId,
                    "channelId", myChannel != null ? myChannel.getChannelId() : "N/A"
            ));
        } catch (Exception e) {
            subscriptionController.seedDemoDataForUser(userId);
            return ResponseEntity.ok(Map.of(
                    "message", "Synced user content with fallback sample data for account: " + userId,
                    "notice", e.getMessage()
            ));
        }
    }

    @GetMapping("/channel")
    @Operation(summary = "Get Your Channel Profile", description = "Returns your own YouTube channel profile and uploads metadata.")
    public ResponseEntity<?> getMyChannel(
            @RequestParam(required = false, defaultValue = "athiagarajan@gmail.com") String userId) {
        Optional<ChannelEntity> myChannel = channelRepository.findByUserIdAndIsMineTrue(userId);
        if (myChannel.isEmpty()) {
            subscriptionController.seedDemoDataForUser(userId);
            myChannel = channelRepository.findByUserIdAndIsMineTrue(userId);
        }
        return ResponseEntity.ok(myChannel.orElse(null));
    }

    @GetMapping("/videos")
    @Operation(summary = "Get Your Uploaded Videos & Shorts", description = "Retrieves videos uploaded by your YouTube account, with an optional filter for Shorts.")
    public ResponseEntity<List<VideoEntity>> getMyVideos(
            @RequestParam(required = false, defaultValue = "athiagarajan@gmail.com") String userId,
            @Parameter(description = "Set to true to isolate YouTube Shorts (<60s format)")
            @RequestParam(required = false, defaultValue = "false") Boolean shortsOnly) {
        
        List<VideoEntity> videos = Boolean.TRUE.equals(shortsOnly) 
                ? videoRepository.findByUserIdAndIsShortTrue(userId) 
                : videoRepository.findByUserId(userId);

        if (videos.isEmpty()) {
            subscriptionController.seedDemoDataForUser(userId);
            videos = Boolean.TRUE.equals(shortsOnly) 
                    ? videoRepository.findByUserIdAndIsShortTrue(userId) 
                    : videoRepository.findByUserId(userId);
        }
        return ResponseEntity.ok(videos);
    }

    @GetMapping("/playlists")
    @Operation(summary = "Get Your Playlists", description = "Retrieves playlists created and owned by your YouTube account.")
    public ResponseEntity<List<PlaylistEntity>> getMyPlaylists(
            @RequestParam(required = false, defaultValue = "athiagarajan@gmail.com") String userId) {
        List<PlaylistEntity> playlists = playlistRepository.findByUserId(userId);
        if (playlists.isEmpty()) {
            subscriptionController.seedDemoDataForUser(userId);
            playlists = playlistRepository.findByUserId(userId);
        }
        return ResponseEntity.ok(playlists);
    }

    @GetMapping("/live")
    @Operation(summary = "Get Your Live Streams", description = "Retrieves live streams and broadcasts created by your account.")
    public ResponseEntity<List<LiveStreamEntity>> getMyLiveStreams(
            @RequestParam(required = false, defaultValue = "athiagarajan@gmail.com") String userId) {
        List<LiveStreamEntity> streams = liveStreamRepository.findByUserId(userId);
        if (streams.isEmpty()) {
            subscriptionController.seedDemoDataForUser(userId);
            streams = liveStreamRepository.findByUserId(userId);
        }
        return ResponseEntity.ok(streams);
    }

    @GetMapping("/posts")
    @Operation(summary = "Get Your Community Posts", description = "Retrieves community posts and announcements created by your account.")
    public ResponseEntity<List<PostEntity>> getMyPosts(
            @RequestParam(required = false, defaultValue = "athiagarajan@gmail.com") String userId) {
        List<PostEntity> posts = postRepository.findByUserId(userId);
        if (posts.isEmpty()) {
            subscriptionController.seedDemoDataForUser(userId);
            posts = postRepository.findByUserId(userId);
        }
        return ResponseEntity.ok(posts);
    }

    @GetMapping("/courses")
    @Operation(summary = "Get Your Educational Courses", description = "Retrieves educational courses and structured modules created by your account.")
    public ResponseEntity<List<CourseEntity>> getMyCourses(
            @RequestParam(required = false, defaultValue = "athiagarajan@gmail.com") String userId) {
        List<CourseEntity> courses = courseRepository.findByUserId(userId);
        if (courses.isEmpty()) {
            subscriptionController.seedDemoDataForUser(userId);
            courses = courseRepository.findByUserId(userId);
        }
        return ResponseEntity.ok(courses);
    }

    @GetMapping("/clips")
    @Operation(summary = "Get Your Short Clips", description = "Retrieves short clips bookmarked or created from your uploaded videos.")
    public ResponseEntity<List<ClipEntity>> getMyClips(
            @RequestParam(required = false, defaultValue = "athiagarajan@gmail.com") String userId) {
        List<ClipEntity> clips = clipRepository.findByUserId(userId);
        if (clips.isEmpty()) {
            subscriptionController.seedDemoDataForUser(userId);
            clips = clipRepository.findByUserId(userId);
        }
        return ResponseEntity.ok(clips);
    }
}
