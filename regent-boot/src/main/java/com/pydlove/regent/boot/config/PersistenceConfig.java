package com.pydlove.regent.boot.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis 映射接口的扫描配置。
 *
 * <p>包名逐个列出而不是用通配符：@MapperScan 只接受确定的包名，而且这份清单本身有价值 ——
 * 新增上下文时忘了登记，映射接口不会被注册，启动就会报「找不到 Bean」，
 * 比在运行时才发现少了一张表要早得多。
 */
@Configuration
@MapperScan({
        "com.pydlove.regent.bug.infrastructure.persistence",
        "com.pydlove.regent.execution.infrastructure.persistence",
        "com.pydlove.regent.governance.infrastructure.persistence",
        "com.pydlove.regent.identity.infrastructure.persistence",
        "com.pydlove.regent.plan.infrastructure.persistence",
        "com.pydlove.regent.plugin.infrastructure.persistence",
        "com.pydlove.regent.project.infrastructure.persistence",
        "com.pydlove.regent.settings.infrastructure.persistence",
        "com.pydlove.regent.testing.infrastructure.persistence"
})
public class PersistenceConfig {
}
