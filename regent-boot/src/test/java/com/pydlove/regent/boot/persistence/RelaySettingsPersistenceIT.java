package com.pydlove.regent.boot.persistence;

import com.pydlove.regent.settings.domain.model.RelaySettings;
import com.pydlove.regent.settings.domain.repository.RelaySettingsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 中继安装设置的真实 MySQL 往返验证。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RelaySettingsPersistenceIT {

    @Autowired
    private RelaySettingsRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("服务器 IP、SSH 用户名和安装目录可持久化，密码没有落库字段")
    void persistsInstallSettingsWithoutPassword() {
        RelaySettings settings = new RelaySettings(
                "https://relay.example.com",
                true,
                "203.0.113.10",
                "deploy",
                "/opt/regent/relay"
        );

        repository.save(settings);
        RelaySettings loaded = repository.find().orElseThrow();

        assertEquals(settings, loaded);
        Integer passwordColumns = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'relay_setting'
                  AND COLUMN_NAME LIKE '%password%'
                """, Integer.class);
        assertEquals(0, passwordColumns, "SSH 密码只能在一次安装请求中使用，不能落库");
    }
}
