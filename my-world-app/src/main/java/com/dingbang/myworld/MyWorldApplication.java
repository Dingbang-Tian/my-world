package com.dingbang.myworld;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用启动入口
 *
 * @author dingbang.tian
 * @since 2026/09/21
 */
@SpringBootApplication
@Slf4j
public class MyWorldApplication {

    /**
     * 启动应用
     *
     * @param args
     */
    public static void main(String[] args) {
        SpringApplication.run(MyWorldApplication.class, args);
        log.warn("======== MyWorld Has Been Started ========");
    }
}
