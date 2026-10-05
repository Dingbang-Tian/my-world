package com.dingbang.myworld.web.ai.codegen;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 代码生成 Web 入口的可信调用方配置。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@Getter
@Setter
@Component
@ConditionalOnProperty(prefix = "my-world.codegen", name = "enabled", havingValue = "true")
@ConfigurationProperties(prefix = "my-world.codegen.web")
public class CodegenWebProperties {

    /**
     * 由服务端配置的代码生成调用方标识，不从请求体获取。
     */
    private String ownerKey = "local-dev";
}
