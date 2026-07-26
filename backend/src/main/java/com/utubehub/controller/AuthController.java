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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
                return ResponseEntity.ok(Map.of(
                        "authenticated", true,
                        "tokenType", "Bearer",
                        "accessToken", client.getAccessToken().getTokenValue(),
                        "expiresAt", client.getAccessToken().getExpiresAt() != null 
                                ? client.getAccessToken().getExpiresAt().toString() : "N/A"
                ));
            }
        }

        return ResponseEntity.ok(Map.of(
                "authenticated", false,
                "message", "User not logged in via Google OAuth. Please visit http://localhost:8080/oauth2/authorization/google to log in."
        ));
    }

    @GetMapping("/user")
    @Operation(summary = "Get Authenticated User Profile", description = "Returns profile details (name, email, avatar) of the currently authenticated Google user.")
    public ResponseEntity<?> getUserProfile(@AuthenticationPrincipal OAuth2User oauth2User) {
        if (oauth2User == null) {
            return ResponseEntity.ok(Map.of(
                    "authenticated", false,
                    "message", "User is unauthenticated."
            ));
        }

        return ResponseEntity.ok(Map.of(
                "authenticated", true,
                "name", oauth2User.getAttribute("name"),
                "email", oauth2User.getAttribute("email"),
                "picture", oauth2User.getAttribute("picture")
        ));
    }
}
