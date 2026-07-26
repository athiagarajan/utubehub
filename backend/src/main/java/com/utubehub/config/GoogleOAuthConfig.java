package com.utubehub.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

import java.io.InputStreamReader;
import java.io.Reader;

@Configuration
public class GoogleOAuthConfig {

    @Bean
    public JsonFactory jsonFactory() {
        return GsonFactory.getDefaultInstance();
    }

    @Bean
    public HttpTransport httpTransport() throws Exception {
        return GoogleNetHttpTransport.newTrustedTransport();
    }

    @Bean
    public GoogleClientSecrets googleClientSecrets() {
        try {
            ClassPathResource resource = new ClassPathResource("oauth_client_utubehub.json");
            if (resource.exists()) {
                try (Reader reader = new InputStreamReader(resource.getInputStream())) {
                    return GoogleClientSecrets.load(jsonFactory(), reader);
                }
            }
        } catch (Exception e) {
            System.err.println("Notice: oauth_client_utubehub.json not found. Falling back to environment variables.");
        }
        return null;
    }

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(GoogleClientSecrets secrets) {
        String clientId = (secrets != null && secrets.getDetails() != null)
                ? secrets.getDetails().getClientId()
                : System.getenv().getOrDefault("GOOGLE_CLIENT_ID", "YOUR_CLIENT_ID");

        String clientSecret = (secrets != null && secrets.getDetails() != null)
                ? secrets.getDetails().getClientSecret()
                : System.getenv().getOrDefault("GOOGLE_CLIENT_SECRET", "YOUR_CLIENT_SECRET");

        ClientRegistration googleRegistration = CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .scope("openid", "profile", "email", "https://www.googleapis.com/auth/youtube.readonly")
                .build();

        return new InMemoryClientRegistrationRepository(googleRegistration);
    }
}
