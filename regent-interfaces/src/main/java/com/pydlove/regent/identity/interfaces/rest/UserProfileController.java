package com.pydlove.regent.identity.interfaces.rest;

import com.pydlove.regent.identity.application.command.UploadedUserAvatar;
import com.pydlove.regent.identity.application.command.UserProfileService;
import com.pydlove.regent.identity.application.query.AuthenticatedUser;
import com.pydlove.regent.identity.application.query.UserAvatarContent;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.interfaces.dto.AuthUserResponse;
import com.pydlove.regent.identity.interfaces.dto.UpdateProfileRequest;
import com.pydlove.regent.shared.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;

/** 当前登录用户的公开资料与头像。 */
@RestController
@RequestMapping("/api/profile")
public class UserProfileController {

    private static final CacheControl AVATAR_CACHE =
            CacheControl.maxAge(Duration.ofDays(7)).cachePrivate();

    private final UserProfileService profileService;

    public UserProfileController(UserProfileService profileService) {
        this.profileService = profileService;
    }

    @PutMapping
    public AuthUserResponse update(@RequestBody UpdateProfileRequest request,
                                   HttpServletRequest servletRequest) {
        AuthenticatedUser current = currentUser(servletRequest);
        return AuthUserResponse.from(profileService.updateDisplayName(
                UserId.of(current.id()), request.displayName()));
    }

    @PostMapping("/avatar")
    public AuthUserResponse uploadAvatar(@RequestParam("file") MultipartFile file,
                                         HttpServletRequest servletRequest) {
        if (file == null || file.isEmpty()) {
            throw new DomainException("没有收到图片内容");
        }
        AuthenticatedUser current = currentUser(servletRequest);
        return AuthUserResponse.from(profileService.uploadAvatar(
                UserId.of(current.id()), toUploaded(file)));
    }

    @GetMapping("/avatar")
    public ResponseEntity<byte[]> avatar(HttpServletRequest servletRequest) {
        AuthenticatedUser current = currentUser(servletRequest);
        return avatarResponse(profileService.loadAvatar(UserId.of(current.id())));
    }

    @GetMapping("/avatar/{userId}")
    public ResponseEntity<byte[]> memberAvatar(@PathVariable long userId) {
        return avatarResponse(profileService.loadAvatar(UserId.of(userId)));
    }

    private static ResponseEntity<byte[]> avatarResponse(UserAvatarContent avatar) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(avatar.contentType()))
                .cacheControl(AVATAR_CACHE)
                .body(avatar.content());
    }

    private AuthenticatedUser currentUser(HttpServletRequest request) {
        return AuthSessionInterceptor.currentUser(request);
    }

    private static UploadedUserAvatar toUploaded(MultipartFile file) {
        try {
            return new UploadedUserAvatar(
                    file.getOriginalFilename(), file.getContentType(), file.getBytes());
        } catch (IOException exception) {
            throw new DomainException("上传的头像读不出来：" + exception.getMessage());
        }
    }
}
