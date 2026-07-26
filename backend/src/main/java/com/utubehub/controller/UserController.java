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
            seedDemoUserContent();
            return ResponseEntity.ok(Map.of(
                    "message", "Demo mode user content sync completed!",
                    "channelSynced", "UC_MY_OWN_CHANNEL_DEMO"
            ));
        }

        try {
            ChannelEntity myChannel = youTubeService.syncMyUploads(accessToken);
            return ResponseEntity.ok(Map.of(
                    "message", "Successfully synced your channel uploads, playlists, and live streams.",
                    "channelId", myChannel != null ? myChannel.getChannelId() : "N/A"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Sync Failed",
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/channel")
    @Operation(summary = "Get Your Channel Profile", description = "Returns your own YouTube channel profile and uploads metadata.")
    public ResponseEntity<?> getMyChannel() {
        Optional<ChannelEntity> myChannel = youTubeService.getMyChannel();
        if (myChannel.isEmpty()) {
            seedDemoUserContent();
            myChannel = youTubeService.getMyChannel();
        }
        return ResponseEntity.ok(myChannel.orElse(null));
    }

    @GetMapping("/videos")
    @Operation(summary = "Get Your Uploaded Videos & Shorts", description = "Retrieves videos uploaded by your YouTube account, with an optional filter for Shorts.")
    public ResponseEntity<List<VideoEntity>> getMyVideos(
            @Parameter(description = "Set to true to isolate YouTube Shorts (<60s format)")
            @RequestParam(required = false, defaultValue = "false") Boolean shortsOnly) {
        Optional<ChannelEntity> myChannel = youTubeService.getMyChannel();
        if (myChannel.isEmpty()) {
            seedDemoUserContent();
            myChannel = youTubeService.getMyChannel();
        }

        String myChannelId = myChannel.map(ChannelEntity::getChannelId).orElse("UC_MY_OWN_CHANNEL_DEMO");
        return ResponseEntity.ok(youTubeService.getChannelVideos(myChannelId, shortsOnly));
    }

    @GetMapping("/playlists")
    @Operation(summary = "Get Your Playlists", description = "Retrieves playlists created and owned by your YouTube account.")
    public ResponseEntity<List<PlaylistEntity>> getMyPlaylists() {
        Optional<ChannelEntity> myChannel = youTubeService.getMyChannel();
        if (myChannel.isEmpty()) {
            seedDemoUserContent();
            myChannel = youTubeService.getMyChannel();
        }

        String myChannelId = myChannel.map(ChannelEntity::getChannelId).orElse("UC_MY_OWN_CHANNEL_DEMO");
        return ResponseEntity.ok(youTubeService.getChannelPlaylists(myChannelId));
    }

    @GetMapping("/live")
    @Operation(summary = "Get Your Live Streams", description = "Retrieves live streams and broadcasts created by your account.")
    public ResponseEntity<List<LiveStreamEntity>> getMyLiveStreams() {
        Optional<ChannelEntity> myChannel = youTubeService.getMyChannel();
        if (myChannel.isEmpty()) {
            seedDemoUserContent();
            myChannel = youTubeService.getMyChannel();
        }

        String myChannelId = myChannel.map(ChannelEntity::getChannelId).orElse("UC_MY_OWN_CHANNEL_DEMO");
        return ResponseEntity.ok(youTubeService.getChannelLiveStreams(myChannelId));
    }

    @GetMapping("/posts")
    @Operation(summary = "Get Your Community Posts", description = "Retrieves community posts and announcements created by your account.")
    public ResponseEntity<List<PostEntity>> getMyPosts() {
        Optional<ChannelEntity> myChannel = youTubeService.getMyChannel();
        if (myChannel.isEmpty()) {
            seedDemoUserContent();
            myChannel = youTubeService.getMyChannel();
        }

        String myChannelId = myChannel.map(ChannelEntity::getChannelId).orElse("UC_MY_OWN_CHANNEL_DEMO");
        return ResponseEntity.ok(youTubeService.getChannelPosts(myChannelId));
    }

    private void seedDemoUserContent() {
        if (!channelRepository.existsById("UC_MY_OWN_CHANNEL_DEMO")) {
            ChannelEntity myOwnChannel = ChannelEntity.builder()
                    .channelId("UC_MY_OWN_CHANNEL_DEMO")
                    .title("Your Account (My Channel)")
                    .description("Your uploaded videos, playlists, live streams, and community posts.")
                    .subscriberCount(1500L)
                    .videoCount(8L)
                    .isMine(true)
                    .lastSyncedAt(LocalDateTime.now())
                    .build();

            channelRepository.save(myOwnChannel);

            videoRepository.saveAll(List.of(
                VideoEntity.builder()
                    .videoId("l83R15D3910")
                    .channelId("UC_MY_OWN_CHANNEL_DEMO")
                    .title("My First Uploaded Project Demo")
                    .description("Welcome to my YouTube channel! Here is a demo of my app.")
                    .durationSeconds(420)
                    .isShort(false)
                    .publishedAt(LocalDateTime.now().minusDays(1))
                    .viewCount(1200L)
                    .build(),
                VideoEntity.builder()
                    .videoId("M576WGiDBdQ")
                    .channelId("UC_MY_OWN_CHANNEL_DEMO")
                    .title("My Quick Coding Short")
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
                    .channelId("UC_MY_OWN_CHANNEL_DEMO")
                    .title("My Uploaded Tutorials & Demos")
                    .description("Collection of project demos created by me.")
                    .itemCount(5)
                    .build()
            );

            liveStreamRepository.save(
                LiveStreamEntity.builder()
                    .streamId("live_stream_demo_1")
                    .channelId("UC_MY_OWN_CHANNEL_DEMO")
                    .title("Live Stream: Full Stack Java & React Development Session")
                    .description("Watch live coding and building YouTube Subscription Hub.")
                    .status("completed")
                    .actualStartTime(LocalDateTime.now().minusDays(10))
                    .build()
            );

            postRepository.save(
                PostEntity.builder()
                    .postId("post_demo_1")
                    .channelId("UC_MY_OWN_CHANNEL_DEMO")
                    .content("🚀 Excited to announce our upcoming feature release! Stay tuned for more updates.")
                    .publishedAt(LocalDateTime.now().minusDays(4))
                    .likeCount(42L)
                    .build()
            );
        }
    }
}
