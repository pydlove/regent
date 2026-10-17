package com.pydlove.regent.settings.interfaces.rest;

import com.pydlove.regent.settings.application.command.RelaySettingsService;
import com.pydlove.regent.settings.interfaces.dto.RelaySettingsResponse;
import com.pydlove.regent.settings.interfaces.dto.UpdateRelaySettingsRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 中继服务器设置与状态接口。
 *
 * <p>读取时同步执行一次短超时探活，设置页据此展示服务器是否可达、连接器是否在线；
 * 接口只接触公开地址，不接受或返回连接器共享密钥。
 */
@RestController
@RequestMapping("/api/settings/relay")
public class RelaySettingsController {

    private final RelaySettingsService relaySettingsService;

    public RelaySettingsController(RelaySettingsService relaySettingsService) {
        this.relaySettingsService = relaySettingsService;
    }

    @GetMapping
    public RelaySettingsResponse current() {
        return RelaySettingsResponse.from(relaySettingsService.current());
    }

    @PutMapping
    public RelaySettingsResponse update(@RequestBody UpdateRelaySettingsRequest request) {
        return RelaySettingsResponse.from(
                relaySettingsService.update(request.serverUrl(), request.monitoringEnabled())
        );
    }
}
