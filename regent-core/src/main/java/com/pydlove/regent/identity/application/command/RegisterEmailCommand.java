package com.pydlove.regent.identity.application.command;

public record RegisterEmailCommand(String email,
                                   String emailCode,
                                   String password,
                                   String confirmPassword) {
}
