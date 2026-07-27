package com.utubehub.config;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class OAuthTokenRegistry {
    private static final Map<String, String> userTokens = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, Object>> userProfiles = new ConcurrentHashMap<>();

    public static void saveTokenAndProfile(String email, String token, String name, String picture) {
        if (email != null && !email.isBlank()) {
            String cleanEmail = email.trim().toLowerCase();
            if (token != null && !token.isBlank()) {
                userTokens.put(cleanEmail, token);
            }
            Map<String, Object> profile = new HashMap<>();
            profile.put("email", email);
            profile.put("name", name != null ? name : "Google User");
            profile.put("picture", picture != null ? picture : "");
            userProfiles.put(cleanEmail, profile);
        }
    }

    public static String getToken(String email) {
        if (email == null) return null;
        return userTokens.get(email.trim().toLowerCase());
    }

    public static List<Map<String, Object>> getAllProfiles() {
        return new ArrayList<>(userProfiles.values());
    }

    public static Map<String, Object> getProfile(String email) {
        if (email == null) return null;
        return userProfiles.get(email.trim().toLowerCase());
    }
}
