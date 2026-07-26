package com.utubehub.service;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.*;
import com.utubehub.entity.ChannelEntity;
import com.utubehub.entity.PlaylistEntity;
import com.utubehub.entity.VideoEntity;
import com.utubehub.repository.ChannelRepository;
import com.utubehub.repository.PlaylistRepository;
import com.utubehub.repository.VideoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class YouTubeService {

    private final HttpTransport httpTransport;
    private final JsonFactory jsonFactory;
    private final ChannelRepository channelRepository;
    private final VideoRepository videoRepository;
    private final PlaylistRepository playlistRepository;

    @Autowired
    public YouTubeService(
            HttpTransport httpTransport,
            JsonFactory jsonFactory,
            ChannelRepository channelRepository,
            VideoRepository videoRepository,
            PlaylistRepository playlistRepository) {
        this.httpTransport = httpTransport;
        this.jsonFactory = jsonFactory;
        this.channelRepository = channelRepository;
        this.videoRepository = videoRepository;
        this.playlistRepository = playlistRepository;
    }

    private YouTube createYouTubeClient(String accessToken) {
        Credential credential = new Credential(BearerToken.authorizationHeaderAccessMethod())
                .setAccessToken(accessToken);
        return new YouTube.Builder(httpTransport, jsonFactory, credential)
                .setApplicationName("UTubeHub")
                .build();
    }

    public List<ChannelEntity> syncUserSubscriptions(String accessToken) throws Exception {
        YouTube youtube = createYouTubeClient(accessToken);
        YouTube.Subscriptions.List request = youtube.subscriptions()
                .list(List.of("snippet", "contentDetails"))
                .setMine(true)
                .setMaxResults(50L);

        SubscriptionListResponse response = request.execute();
        List<Subscription> items = response.getItems();

        List<ChannelEntity> savedChannels = new ArrayList<>();
        if (items != null) {
            for (Subscription sub : items) {
                SubscriptionSnippet snippet = sub.getSnippet();
                String channelId = snippet.getResourceId().getChannelId();

                ChannelEntity entity = ChannelEntity.builder()
                        .channelId(channelId)
                        .title(snippet.getTitle())
                        .description(snippet.getDescription())
                        .thumbnailUrl(snippet.getThumbnails() != null && snippet.getThumbnails().getDefault() != null 
                                ? snippet.getThumbnails().getDefault().getUrl() : null)
                        .lastSyncedAt(LocalDateTime.now())
                        .build();

                savedChannels.add(channelRepository.save(entity));
            }
        }
        return savedChannels;
    }

    public List<ChannelEntity> getLocalSubscriptions() {
        return channelRepository.findAll();
    }

    public List<VideoEntity> getChannelVideos(String channelId, Boolean shortsOnly) {
        if (Boolean.TRUE.equals(shortsOnly)) {
            return videoRepository.findByChannelIdAndIsShortTrue(channelId);
        }
        return videoRepository.findByChannelId(channelId);
    }

    public List<PlaylistEntity> getChannelPlaylists(String channelId) {
        return playlistRepository.findByChannelId(channelId);
    }
}
