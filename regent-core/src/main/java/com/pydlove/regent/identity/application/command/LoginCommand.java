package com.pydlove.regent.identity.application.command;

public record LoginCommand(String identifier, String password, boolean rememberMe) {
}
