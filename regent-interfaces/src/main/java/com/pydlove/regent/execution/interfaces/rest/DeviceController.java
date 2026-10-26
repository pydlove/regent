package com.pydlove.regent.execution.interfaces.rest;

import com.pydlove.regent.execution.application.command.DevicePresenceService;
import com.pydlove.regent.execution.interfaces.dto.DevicePresenceResponse;
import com.pydlove.regent.identity.application.query.AuthenticatedUser;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.interfaces.rest.AuthSessionInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 浏览器侧的设备状态和任务派发入口。浏览器只访问同源服务器，不直连 127.0.0.1。 */
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DevicePresenceService presenceService;

    public DeviceController(DevicePresenceService presenceService) {
        this.presenceService = presenceService;
    }

    @GetMapping("/current")
    public ResponseEntity<DevicePresenceResponse> current(HttpServletRequest request) {
        return presenceService.current(owner(request))
                .map(DevicePresenceResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{deviceId}/tasks/{taskId}/dispatch")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void dispatch(@PathVariable String deviceId,
                         @PathVariable long taskId,
                         HttpServletRequest request) {
        UserId owner = owner(request);
        presenceService.enqueue(owner, deviceId, taskId);
    }

    private static UserId owner(HttpServletRequest request) {
        AuthenticatedUser user = AuthSessionInterceptor.currentUser(request);
        return UserId.of(user.id());
    }
}
