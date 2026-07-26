package com.utubehub.controller;

import com.utubehub.entity.ChannelEntity;
import com.utubehub.entity.PlaylistEntity;
import com.utubehub.entity.VideoEntity;
import com.utubehub.service.YouTubeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/subscriptions")
@Tag(name = "YouTube Subscriptions", description = "Endpoints for retrieving subscriptions, channel videos, shorts, playlists, and syncing with YouTube API")
public class SubscriptionController {

    private final YouTubeService youTubeService;

    @Autowired
    public SubscriptionController(YouTubeService youTubeService) {
        this.youTubeService = youTubeService;
    }

    @GetMapping
    @Operation(summary = "List All Subscriptions", description = "Fetches all user subscribed YouTube channels cached in local PostgreSQL database.")
    public ResponseEntity<List<ChannelEntity>> getSubscriptions() {
        List<ChannelEntity> channels = youTubeService.getLocalSubscriptions();
        if (channels.isEmpty()) {
            // Seed initial display channel if DB is empty
            ChannelEntity demoChannel = ChannelEntity.builder()
                    .channelId("UC_x5XG1OV2P6uZZ5FSM9Ttw")
                    .title("Google Developers")
                    .description("The official Google Developers channel for tech videos and tutorials.")
                    .thumbnailUrl("https://yt3.googleusercontent.com/ytc/AIdro_k...")
                    .subscriberCount(2400000L)
                    .videoCount(5200L)
                    .build();
            return ResponseEntity.ok(List.of(demoChannel));
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
        try {
            List<ChannelEntity> synced = youTubeService.syncUserSubscriptions(accessToken);
            return ResponseEntity.ok(Map.of(
                    "message", "Successfully synced " + synced.size() + " subscriptions from YouTube.",
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
        return ResponseEntity.ok(youTubeService.getChannelVideos(channelId, shortsOnly));
    }

    @GetMapping("/{channelId}/playlists")
    @Operation(summary = "Get Channel Playlists", description = "Retrieves playlists created by a specific channel.")
    public ResponseEntity<List<PlaylistEntity>> getChannelPlaylists(@PathVariable String channelId) {
        return ResponseEntity.ok(youTubeService.getChannelPlaylists(channelId));
    }
}
