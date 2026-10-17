package com.pydlove.regent.identity.interfaces.rest;

import com.pydlove.regent.identity.application.command.AuthService;
import com.pydlove.regent.identity.application.command.LoginCommand;
import com.pydlove.regent.identity.application.command.RegisterEmailCommand;
import com.pydlove.regent.identity.application.query.AuthSessionGrant;
import com.pydlove.regent.identity.interfaces.dto.AuthUserResponse;
import com.pydlove.regent.identity.interfaces.dto.LoginRequest;
import com.pydlove.regent.identity.interfaces.dto.RegisterRequest;
import com.pydlove.regent.identity.interfaces.dto.SendEmailCodeRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final boolean secureCookie;

    public AuthController(AuthService authService,
                          @Value("${regent.auth.cookie-secure:false}") boolean secureCookie) {
        this.authService = authService;
        this.secureCookie = secureCookie;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthUserResponse register(@RequestBody RegisterRequest request) {
        return AuthUserResponse.from(authService.register(
                new RegisterEmailCommand(
                        request.email(),
                        request.emailCode(),
                        request.password(),
                        request.confirmPassword())));
    }

    @PostMapping("/email-codes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sendEmailCode(@RequestBody SendEmailCodeRequest request) {
        authService.sendRegistrationCode(request.email());
    }

    @PostMapping("/login")
    public AuthUserResponse login(@RequestBody LoginRequest request,
                                  HttpServletResponse response) {
        AuthSessionGrant grant = authService.login(
                new LoginCommand(request.username(), request.password()));
        long maxAge = Math.max(0, Duration.between(Instant.now(), grant.expiresAt()).toSeconds());
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(grant.rawToken(), maxAge).toString());
        return AuthUserResponse.from(grant.user());
    }

    @GetMapping("/me")
    public AuthUserResponse me(HttpServletRequest request) {
        return AuthUserResponse.from(
                authService.currentUser(AuthSessionInterceptor.readToken(request)));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(AuthSessionInterceptor.readToken(request));
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie("", 0).toString());
    }

    private ResponseCookie sessionCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(AuthSessionInterceptor.COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
    }
}
