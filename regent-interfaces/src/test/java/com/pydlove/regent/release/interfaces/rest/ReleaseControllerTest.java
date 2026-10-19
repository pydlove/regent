package com.pydlove.regent.release.interfaces.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReleaseControllerTest {

    @Test
    @DisplayName("读取 latest.json 并返回本机托管下载地址")
    void returnsLatestManifest(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("Regent.dmg"), "dmg-content", StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("latest.json"), """
                {
                  "version": "1.2.0",
                  "releaseCode": "R100B003",
                  "releasedAt": "2026-10-19T00:00:00Z",
                  "minShellVersion": "1.0.0",
                  "notes": "test release",
                  "downloads": {
                    "macos": {"file": "Regent.dmg", "sizeBytes": 11, "sha256": "abc"},
                    "windows": {"file": "Regent.exe", "sizeBytes": 99, "sha256": "def"}
                  }
                }
                """, StandardCharsets.UTF_8);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new ReleaseController(new ObjectMapper(), directory.toString())).build();

        mvc.perform(get("/api/release/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("1.2.0"))
                .andExpect(jsonPath("$.downloads.macos.url").value("/api/release/download/macos"))
                .andExpect(jsonPath("$.downloads.windows").doesNotExist());

        byte[] content = mvc.perform(get("/api/release/download/macos"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals("dmg-content".getBytes(StandardCharsets.UTF_8), content);
    }

    @Test
    @DisplayName("没有发布清单时返回空版本而不是 404")
    void returnsEmptyManifest(@TempDir Path directory) throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new ReleaseController(new ObjectMapper(), directory.toString())).build();

        mvc.perform(get("/api/release/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").doesNotExist());
    }
}
