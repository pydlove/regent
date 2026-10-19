package com.pydlove.regent.boot.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 在 Spring 读取完 application*.yml 后，把所有 {@code ENC(v1:...)} 属性解密并放到最高优先级。
 *
 * <p>这样业务代码和 DataSource 都不需要感知密文；配置文件里仍然只出现密文。
 */
public class EncryptedPropertyEnvironmentPostProcessor
        implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
                                       SpringApplication application) {
        Map<String, Object> decrypted = new LinkedHashMap<>();
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (!(source instanceof EnumerablePropertySource<?> enumerable)) {
                continue;
            }
            for (String name : enumerable.getPropertyNames()) {
                Object value = enumerable.getProperty(name);
                if (value instanceof String text && ConfigEncryption.isEncrypted(text)) {
                    decrypted.put(name, text);
                }
            }
        }
        if (decrypted.isEmpty()) {
            return;
        }

        String masterKey = environment.getProperty("REGENT_CONFIG_KEY");
        if (masterKey == null || masterKey.isBlank()) {
            masterKey = ConfigEncryption.resolveMasterKey();
        }
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalStateException(
                    "检测到 ENC(...) 配置，但没有找到 REGENT_CONFIG_KEY 或 ~/.regent/config.key");
        }

        Map<String, Object> plain = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : decrypted.entrySet()) {
            plain.put(entry.getKey(), ConfigEncryption.decrypt((String) entry.getValue(), masterKey));
        }
        environment.getPropertySources().addFirst(
                new MapPropertySource("regentDecryptedProperties", plain));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
