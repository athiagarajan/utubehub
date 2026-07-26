package com.utubehub.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "System Health", description = "Endpoints for inspecting backend runtime status and diagnostics")
public class HealthController {

    @GetMapping
    @Operation(summary = "Get System Health Status", description = "Returns operational health status of the UTubeHub backend service.")
    public ResponseEntity<Map<String, String>> getHealth() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "utubehub-backend",
            "version", "1.0.0"
        ));
    }
}
