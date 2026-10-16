package com.secsmart.regent.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Regent 启动入口。
 *
 * <p>装配约定见 AGENTS.md：所有 Bean 在 {@code boot/config} 下用 {@code @Configuration} +
 * {@code @Bean} 显式声明，{@code regent-core} 内不得出现任何 Spring 注解。
 */
@SpringBootApplication
public class RegentApplication {

    public static void main(String[] args) {
        SpringApplication.run(RegentApplication.class, args);
    }
}
