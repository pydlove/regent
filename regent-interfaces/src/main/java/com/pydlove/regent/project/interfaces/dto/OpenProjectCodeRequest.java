package com.pydlove.regent.project.interfaces.dto;

public record OpenProjectCodeRequest(
        String relativePath,
        Integer line) {
}
