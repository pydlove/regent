package com.pydlove.regent.identity.interfaces.rest;

import com.pydlove.regent.identity.application.command.LoginCommand;
import com.pydlove.regent.identity.application.command.MobileAuthService;
import com.pydlove.regent.identity.interfaces.dto.MobileAuthResponse;
import com.pydlove.regent.identity.interfaces.dto.MobileLoginRequest;
import com.pydlove.regent.identity.interfaces.dto.MobileRefreshRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mobile/auth")
public class MobileAuthController {

    private final MobileAuthService mobileAuthService;

    public MobileAuthController(MobileAuthService mobileAuthService) {
        this.mobileAuthService = mobileAuthService;
    }

    @PostMapping("/login")
    public MobileAuthResponse login(@RequestBody MobileLoginRequest request) {
        return MobileAuthResponse.from(mobileAuthService.login(
                new LoginCommand(request.username(), request.password(), false),
                request.installationId()));
    }

    @PostMapping("/refresh")
    public MobileAuthResponse refresh(@RequestBody MobileRefreshRequest request) {
        return MobileAuthResponse.from(mobileAuthService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        mobileAuthService.logout(AuthSessionInterceptor.readToken(request));
    }
}
