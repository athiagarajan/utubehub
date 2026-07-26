package com.utubehub.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication & OAuth Token", description = "Endpoints for checking OAuth authentication status, retrieving user profile, and obtaining OAuth2 access tokens for Swagger UI")
public class AuthController {

    private final OAuth2AuthorizedClientService authorizedClientService;

    @Autowired
    public AuthController(OAuth2AuthorizedClientService authorizedClientService) {
        this.authorizedClientService = authorizedClientService;
    }

    @GetMapping("/token")
    @Operation(summary = "Get OAuth2 Access Token", description = "Returns the active Google OAuth2 Bearer Access Token for the logged-in session. Copy this token into Swagger UI's Authorize button.")
    public ResponseEntity<?> getAccessToken(Authentication authentication) {
        if (authentication instanceof OAuth2AuthenticationToken oauthToken) {
            OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                    oauthToken.getAuthorizedClientRegistrationId(),
                    oauthToken.getName());

            if (client != null && client.getAccessToken() != null) {
                Map<String, Object> response = new HashMap<>();
                response.put("authenticated", true);
                response.put("tokenType", "Bearer");
                response.put("accessToken", client.getAccessToken().getTokenValue());
                response.put("expiresAt", client.getAccessToken().getExpiresAt() != null 
                        ? client.getAccessToken().getExpiresAt().toString() : "N/A");
                return ResponseEntity.ok(response);
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("authenticated", false);
        response.put("message", "User not logged in via Google OAuth. Please visit http://localhost:8080/oauth2/authorization/google to log in or use /api/v1/auth/demo-login.");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/demo-login")
    @Operation(summary = "Generate Demo Session Token", description = "Generates a instant local Demo OAuth Access Token for unblocked testing in Swagger UI and the React App while Google Cloud Console access is being configured.")
    public ResponseEntity<?> generateDemoToken() {
        Map<String, Object> response = new HashMap<>();
        response.put("authenticated", true);
        response.put("tokenType", "Bearer");
        response.put("accessToken", "demo-youtube-oauth-token-utubehub-2026");
        response.put("user", Map.of(
                "name", "Demo YouTube Creator",
                "email", "demo.user@utubehub.com",
                "picture", "https://yt3.googleusercontent.com/ytc/AIdro_k..."
        ));
        response.put("message", "Demo OAuth token generated! Use this token in Swagger UI's Authorize button.");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user")
    @Operation(summary = "Get Authenticated User Profile", description = "Returns profile details (name, email, avatar) of the currently authenticated Google user.")
    public ResponseEntity<?> getUserProfile(@AuthenticationPrincipal OAuth2User oauth2User) {
        if (oauth2User == null) {
            Map<String, Object> unauth = new HashMap<>();
            unauth.put("authenticated", false);
            unauth.put("message", "User is unauthenticated.");
            return ResponseEntity.ok(unauth);
        }

        String name = oauth2User.getAttribute("name") != null ? oauth2User.getAttribute("name").toString() : "Google User";
        String email = oauth2User.getAttribute("email") != null ? oauth2User.getAttribute("email").toString() : "user@gmail.com";
        String picture = oauth2User.getAttribute("picture") != null ? oauth2User.getAttribute("picture").toString() : "";

        Map<String, Object> response = new HashMap<>();
        response.put("authenticated", true);
        response.put("name", name);
        response.put("email", email);
        response.put("picture", picture);
        return ResponseEntity.ok(response);
    }
}
