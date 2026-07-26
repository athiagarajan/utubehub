package com.utubehub.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getMyChannelShouldReturn200OK() throws Exception {
        mockMvc.perform(get("/api/v1/user/channel")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void getMyVideosShouldReturn200OK() throws Exception {
        mockMvc.perform(get("/api/v1/user/videos")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void getMyPlaylistsShouldReturn200OK() throws Exception {
        mockMvc.perform(get("/api/v1/user/playlists")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void getMyLiveStreamsShouldReturn200OK() throws Exception {
        mockMvc.perform(get("/api/v1/user/live")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void syncMyContentWithoutAuthHeaderShouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/user/sync")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }
}
