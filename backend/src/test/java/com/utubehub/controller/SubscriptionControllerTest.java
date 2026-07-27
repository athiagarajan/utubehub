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
class SubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getSubscriptionsShouldReturn200OK() throws Exception {
        mockMvc.perform(get("/api/v1/subscriptions")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("athiagarajan@gmail.com's YouTube Channel"));
    }

    @Test
    void syncSubscriptionsWithoutAuthHeaderShouldReturn200WithDemoSyncMessage() throws Exception {
        mockMvc.perform(post("/api/v1/subscriptions/sync")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void getChannelVideosShouldReturn200OK() throws Exception {
        mockMvc.perform(get("/api/v1/subscriptions/UC_x5XG1OV2P6uZZ5FSM9Ttw/videos")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
