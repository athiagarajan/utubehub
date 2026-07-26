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

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        List<String> channelIds = new ArrayList<>();
        Map<String, SubscriptionSnippet> subscriptionSnippets = new HashMap<>();

        String pageToken = null;
        do {
            YouTube.Subscriptions.List request = youtube.subscriptions()
                    .list(List.of("snippet", "contentDetails"))
                    .setMine(true)
                    .setMaxResults(50L);

            if (pageToken != null && !pageToken.isEmpty()) {
                request.setPageToken(pageToken);
            }

            SubscriptionListResponse response = request.execute();
            List<Subscription> items = response.getItems();

            if (items != null) {
                for (Subscription sub : items) {
                    SubscriptionSnippet snippet = sub.getSnippet();
                    if (snippet != null && snippet.getResourceId() != null) {
                        String channelId = snippet.getResourceId().getChannelId();
                        channelIds.add(channelId);
                        subscriptionSnippets.put(channelId, snippet);
                    }
                }
            }
            pageToken = response.getNextPageToken();
        } while (pageToken != null && !pageToken.isEmpty());

        List<ChannelEntity> savedChannels = new ArrayList<>();

        // Batch fetch channel details & statistics in chunks of 50
        for (int i = 0; i < channelIds.size(); i += 50) {
            List<String> batch = channelIds.subList(i, Math.min(i + 50, channelIds.size()));
            YouTube.Channels.List channelRequest = youtube.channels()
                    .list(List.of("snippet", "statistics", "contentDetails"))
                    .setId(batch);

            ChannelListResponse channelResponse = channelRequest.execute();
            List<Channel> channels = channelResponse.getItems();

            if (channels != null) {
                for (Channel ch : channels) {
                    ChannelSnippet snippet = ch.getSnippet();
                    ChannelStatistics stats = ch.getStatistics();
                    ChannelContentDetails details = ch.getContentDetails();

                    String uploadsPlaylistId = (details != null && details.getRelatedPlaylists() != null) 
                            ? details.getRelatedPlaylists().getUploads() : null;

                    Long subscriberCount = (stats != null && stats.getSubscriberCount() != null) 
                            ? stats.getSubscriberCount().longValue() : 0L;

                    Long videoCount = (stats != null && stats.getVideoCount() != null) 
                            ? stats.getVideoCount().longValue() : 0L;

                    String thumbnailUrl = null;
                    if (snippet != null && snippet.getThumbnails() != null) {
                        if (snippet.getThumbnails().getHigh() != null) {
                            thumbnailUrl = snippet.getThumbnails().getHigh().getUrl();
                        } else if (snippet.getThumbnails().getDefault() != null) {
                            thumbnailUrl = snippet.getThumbnails().getDefault().getUrl();
                        }
                    }

                    ChannelEntity entity = ChannelEntity.builder()
                            .channelId(ch.getId())
                            .title(snippet != null ? snippet.getTitle() : "Unknown Channel")
                            .description(snippet != null ? snippet.getDescription() : "")
                            .thumbnailUrl(thumbnailUrl)
                            .subscriberCount(subscriberCount)
                            .videoCount(videoCount)
                            .uploadsPlaylistId(uploadsPlaylistId)
                            .lastSyncedAt(LocalDateTime.now())
                            .build();

                    savedChannels.add(channelRepository.save(entity));
                }
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
