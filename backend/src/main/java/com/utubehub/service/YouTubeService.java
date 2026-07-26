package com.utubehub.service;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.*;
import com.utubehub.entity.*;
import com.utubehub.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class YouTubeService {

    private final HttpTransport httpTransport;
    private final JsonFactory jsonFactory;
    private final ChannelRepository channelRepository;
    private final VideoRepository videoRepository;
    private final PlaylistRepository playlistRepository;
    private final LiveStreamRepository liveStreamRepository;
    private final PostRepository postRepository;

    @Autowired
    public YouTubeService(
            HttpTransport httpTransport,
            JsonFactory jsonFactory,
            ChannelRepository channelRepository,
            VideoRepository videoRepository,
            PlaylistRepository playlistRepository,
            LiveStreamRepository liveStreamRepository,
            PostRepository postRepository) {
        this.httpTransport = httpTransport;
        this.jsonFactory = jsonFactory;
        this.channelRepository = channelRepository;
        this.videoRepository = videoRepository;
        this.playlistRepository = playlistRepository;
        this.liveStreamRepository = liveStreamRepository;
        this.postRepository = postRepository;
    }

    private YouTube createYouTubeClient(String accessToken) {
        Credential credential = new Credential(BearerToken.authorizationHeaderAccessMethod())
                .setAccessToken(accessToken);
        return new YouTube.Builder(httpTransport, jsonFactory, credential)
                .setApplicationName("UTubeHub")
                .build();
    }

    public ChannelEntity syncMyUploads(String accessToken, String userId) throws Exception {
        YouTube youtube = createYouTubeClient(accessToken);
        YouTube.Channels.List request = youtube.channels()
                .list(List.of("snippet", "statistics", "contentDetails"))
                .setMine(true);

        ChannelListResponse response = request.execute();
        List<Channel> items = response.getItems();

        if (items != null && !items.isEmpty()) {
            Channel myChannel = items.get(0);
            ChannelSnippet snippet = myChannel.getSnippet();
            ChannelStatistics stats = myChannel.getStatistics();
            ChannelContentDetails details = myChannel.getContentDetails();

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
                    .channelId(myChannel.getId())
                    .userId(userId)
                    .title((snippet != null ? snippet.getTitle() : userId + "'s YouTube Channel"))
                    .description(snippet != null ? snippet.getDescription() : "Uploaded videos, playlists, live streams, and posts for " + userId)
                    .thumbnailUrl(thumbnailUrl)
                    .subscriberCount(subscriberCount)
                    .videoCount(videoCount)
                    .uploadsPlaylistId(uploadsPlaylistId)
                    .isMine(true)
                    .lastSyncedAt(LocalDateTime.now())
                    .build();

            ChannelEntity saved = channelRepository.save(entity);

            // Sync videos, shorts, playlists
            syncChannelContent(accessToken, myChannel.getId(), userId);

            // Sync Live Streams
            try {
                YouTube.LiveBroadcasts.List broadcastReq = youtube.liveBroadcasts()
                        .list(List.of("snippet", "status"))
                        .setBroadcastStatus("all")
                        .setMaxResults(25L);

                LiveBroadcastListResponse broadcastRes = broadcastReq.execute();
                List<LiveBroadcast> broadcasts = broadcastRes.getItems();
                if (broadcasts != null) {
                    for (LiveBroadcast lb : broadcasts) {
                        LiveBroadcastSnippet bSnippet = lb.getSnippet();
                        LiveBroadcastStatus bStatus = lb.getStatus();

                        String bThumb = null;
                        if (bSnippet != null && bSnippet.getThumbnails() != null && bSnippet.getThumbnails().getDefault() != null) {
                            bThumb = bSnippet.getThumbnails().getDefault().getUrl();
                        }

                        LiveStreamEntity streamEntity = LiveStreamEntity.builder()
                                .streamId(lb.getId())
                                .userId(userId)
                                .channelId(myChannel.getId())
                                .title(bSnippet != null ? bSnippet.getTitle() : "Live Stream")
                                .description(bSnippet != null ? bSnippet.getDescription() : "")
                                .thumbnailUrl(bThumb)
                                .status(bStatus != null ? bStatus.getLifeCycleStatus() : "completed")
                                .build();

                        liveStreamRepository.save(streamEntity);
                    }
                }
            } catch (Exception e) {
                System.err.println("Notice: Live broadcasts fetch notice: " + e.getMessage());
            }

            return saved;
        }
        return null;
    }

    public List<ChannelEntity> syncUserSubscriptions(String accessToken, String userId) throws Exception {
        YouTube youtube = createYouTubeClient(accessToken);
        List<String> channelIds = new ArrayList<>();

        // Sync own uploads channel first
        try {
            syncMyUploads(accessToken, userId);
        } catch (Exception e) {
            System.err.println("Notice: Could not sync own channel uploads: " + e.getMessage());
        }

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
                        channelIds.add(snippet.getResourceId().getChannelId());
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
                            .userId(userId)
                            .title(snippet != null ? snippet.getTitle() : "Subscribed Channel")
                            .description(snippet != null ? snippet.getDescription() : "")
                            .thumbnailUrl(thumbnailUrl)
                            .subscriberCount(subscriberCount)
                            .videoCount(videoCount)
                            .uploadsPlaylistId(uploadsPlaylistId)
                            .isMine(false)
                            .lastSyncedAt(LocalDateTime.now())
                            .build();

                    savedChannels.add(channelRepository.save(entity));
                }
            }
        }

        return savedChannels;
    }

    public List<VideoEntity> syncChannelContent(String accessToken, String channelId, String userId) throws Exception {
        YouTube youtube = createYouTubeClient(accessToken);

        // Lookup channel's uploads playlist ID
        ChannelEntity channel = channelRepository.findById(channelId).orElse(null);
        String uploadsPlaylistId = (channel != null) ? channel.getUploadsPlaylistId() : null;

        if (uploadsPlaylistId == null) {
            YouTube.Channels.List chReq = youtube.channels().list(List.of("contentDetails")).setId(List.of(channelId));
            ChannelListResponse chRes = chReq.execute();
            if (chRes.getItems() != null && !chRes.getItems().isEmpty()) {
                uploadsPlaylistId = chRes.getItems().get(0).getContentDetails().getRelatedPlaylists().getUploads();
            }
        }

        List<VideoEntity> savedVideos = new ArrayList<>();
        if (uploadsPlaylistId != null) {
            // Fetch latest uploads
            YouTube.PlaylistItems.List playlistReq = youtube.playlistItems()
                    .list(List.of("snippet", "contentDetails"))
                    .setPlaylistId(uploadsPlaylistId)
                    .setMaxResults(50L);

            PlaylistItemListResponse playlistRes = playlistReq.execute();
            List<PlaylistItem> items = playlistRes.getItems();

            if (items != null && !items.isEmpty()) {
                List<String> videoIds = items.stream()
                        .map(item -> item.getContentDetails().getVideoId())
                        .toList();

                // Fetch video durations & statistics to isolate Shorts (<60s)
                YouTube.Videos.List videoReq = youtube.videos()
                        .list(List.of("snippet", "contentDetails", "statistics"))
                        .setId(videoIds);

                VideoListResponse videoRes = videoReq.execute();
                List<com.google.api.services.youtube.model.Video> ytVideos = videoRes.getItems();

                if (ytVideos != null) {
                    for (com.google.api.services.youtube.model.Video v : ytVideos) {
                        VideoSnippet snippet = v.getSnippet();
                        VideoContentDetails details = v.getContentDetails();
                        VideoStatistics stats = v.getStatistics();

                        int durationSecs = 0;
                        if (details != null && details.getDuration() != null) {
                            try {
                                durationSecs = (int) Duration.parse(details.getDuration()).getSeconds();
                            } catch (Exception ignored) {}
                        }

                        boolean isShort = durationSecs > 0 && durationSecs <= 60;
                        if (snippet != null && snippet.getTitle() != null && snippet.getTitle().toLowerCase().contains("#shorts")) {
                            isShort = true;
                        }

                        String thumbnailUrl = null;
                        if (snippet != null && snippet.getThumbnails() != null) {
                            if (snippet.getThumbnails().getHigh() != null) {
                                thumbnailUrl = snippet.getThumbnails().getHigh().getUrl();
                            } else if (snippet.getThumbnails().getDefault() != null) {
                                thumbnailUrl = snippet.getThumbnails().getDefault().getUrl();
                            }
                        }

                        VideoEntity entity = VideoEntity.builder()
                                .videoId(v.getId())
                                .userId(userId)
                                .channelId(channelId)
                                .title(snippet != null ? snippet.getTitle() : "Untitled Video")
                                .description(snippet != null ? snippet.getDescription() : "")
                                .thumbnailUrl(thumbnailUrl)
                                .durationSeconds(durationSecs)
                                .isShort(isShort)
                                .viewCount((stats != null && stats.getViewCount() != null) ? stats.getViewCount().longValue() : 0L)
                                .likeCount((stats != null && stats.getLikeCount() != null) ? stats.getLikeCount().longValue() : 0L)
                                .build();

                        savedVideos.add(videoRepository.save(entity));
                    }
                }
            }
        }

        // Fetch channel playlists
        try {
            YouTube.Playlists.List playlistReq = youtube.playlists()
                    .list(List.of("snippet", "contentDetails"))
                    .setChannelId(channelId)
                    .setMaxResults(25L);

            PlaylistListResponse playlistRes = playlistReq.execute();
            List<com.google.api.services.youtube.model.Playlist> ytPlaylists = playlistRes.getItems();
            if (ytPlaylists != null) {
                for (com.google.api.services.youtube.model.Playlist pl : ytPlaylists) {
                    PlaylistSnippet snippet = pl.getSnippet();
                    PlaylistContentDetails details = pl.getContentDetails();

                    String thumbnailUrl = null;
                    if (snippet != null && snippet.getThumbnails() != null && snippet.getThumbnails().getDefault() != null) {
                        thumbnailUrl = snippet.getThumbnails().getDefault().getUrl();
                    }

                    PlaylistEntity entity = PlaylistEntity.builder()
                            .playlistId(pl.getId())
                            .userId(userId)
                            .channelId(channelId)
                            .title(snippet != null ? snippet.getTitle() : "Untitled Playlist")
                            .description(snippet != null ? snippet.getDescription() : "")
                            .itemCount((details != null && details.getItemCount() != null) ? details.getItemCount().intValue() : 0)
                            .thumbnailUrl(thumbnailUrl)
                            .build();

                    playlistRepository.save(entity);
                }
            }
        } catch (Exception e) {
            System.err.println("Playlists fetch notice for channel " + channelId + ": " + e.getMessage());
        }

        return savedVideos;
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

    public List<LiveStreamEntity> getChannelLiveStreams(String channelId) {
        return liveStreamRepository.findByChannelId(channelId);
    }

    public List<PostEntity> getChannelPosts(String channelId) {
        return postRepository.findByChannelId(channelId);
    }
}
