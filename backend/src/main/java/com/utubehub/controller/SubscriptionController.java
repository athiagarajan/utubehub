package com.utubehub.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/subscriptions")
@Tag(name = "YouTube Subscriptions", description = "Endpoints for fetching and managing YouTube subscribed channels and metadata")
public class SubscriptionController {

    @GetMapping
    @Operation(summary = "List Subscriptions", description = "Fetches paginated list of subscribed YouTube channels from local cache or YouTube API.")
    public ResponseEntity<List<Map<String, Object>>> getSubscriptions() {
        // Initial sample response structure for testing/scaffolding
        List<Map<String, Object>> mockSubscriptions = List.of(
            Map.of(
                "channelId", "UC_x5XG1OV2P6uZZ5FSM9Ttw",
                "title", "Google Developers",
                "subscriberCount", 2400000,
                "videoCount", 5200,
                "thumbnailUrl", "https://yt3.googleusercontent.com/ytc/AIdro_k..."
            )
        );
        return ResponseEntity.ok(mockSubscriptions);
    }

    @PostMapping("/sync")
    @Operation(summary = "Sync Subscriptions", description = "Triggers an incremental sync of user YouTube subscriptions into local PostgreSQL cache.")
    public ResponseEntity<Map<String, String>> syncSubscriptions() {
        return ResponseEntity.ok(Map.of("message", "Subscription sync triggered successfully"));
    }
}
