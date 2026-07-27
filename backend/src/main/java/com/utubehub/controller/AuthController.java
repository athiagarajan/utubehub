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
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication & OAuth Token", description = "Endpoints for checking OAuth authentication status, retrieving user profile, and obtaining OAuth2 access tokens for Swagger UI")
public class AuthController {

    private final OAuth2AuthorizedClientService authorizedClientService;
    private static final Map<String, Map<String, Object>> authenticatedAccounts = new ConcurrentHashMap<>();
    private static String activeAccountEmail = null;

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
    @Operation(summary = "Get Authenticated User Profile", description = "Returns profile details (name, email, avatar) of the currently authenticated Google user and all logged-in Google accounts.")
    public ResponseEntity<?> getUserProfile(@AuthenticationPrincipal OAuth2User oauth2User) {
        if (oauth2User != null) {
            String name = oauth2User.getAttribute("name") != null ? oauth2User.getAttribute("name").toString() : "Google User";
            String email = oauth2User.getAttribute("email") != null ? oauth2User.getAttribute("email").toString() : "user@gmail.com";
            String picture = oauth2User.getAttribute("picture") != null ? oauth2User.getAttribute("picture").toString() : "";

            Map<String, Object> accountInfo = new HashMap<>();
            accountInfo.put("name", name);
            accountInfo.put("email", email);
            accountInfo.put("picture", picture);

            authenticatedAccounts.put(email, accountInfo);
            activeAccountEmail = email;

            Map<String, Object> response = new HashMap<>();
            response.put("authenticated", true);
            response.put("email", email);
            response.put("name", name);
            response.put("picture", picture);
            response.put("accounts", new ArrayList<>(authenticatedAccounts.values()));
            return ResponseEntity.ok(response);
        }

        List<Map<String, Object>> registryProfiles = com.utubehub.config.OAuthTokenRegistry.getAllProfiles();
        if (!registryProfiles.isEmpty()) {
            Map<String, Object> latest = registryProfiles.get(registryProfiles.size() - 1);
            Map<String, Object> response = new HashMap<>();
            response.put("authenticated", true);
            response.put("email", latest.get("email"));
            response.put("name", latest.get("name"));
            response.put("picture", latest.get("picture"));
            response.put("accounts", registryProfiles);
            return ResponseEntity.ok(response);
        }

        if (activeAccountEmail != null && authenticatedAccounts.containsKey(activeAccountEmail)) {
            Map<String, Object> activeInfo = authenticatedAccounts.get(activeAccountEmail);
            Map<String, Object> response = new HashMap<>();
            response.put("authenticated", true);
            response.put("email", activeInfo.get("email"));
            response.put("name", activeInfo.get("name"));
            response.put("picture", activeInfo.get("picture"));
            response.put("accounts", new ArrayList<>(authenticatedAccounts.values()));
            return ResponseEntity.ok(response);
        }

        Map<String, Object> unauth = new HashMap<>();
        unauth.put("authenticated", false);
        unauth.put("accounts", new ArrayList<>(authenticatedAccounts.values()));
        unauth.put("message", "No active Google OAuth session.");
        return ResponseEntity.ok(unauth);
    }
}
