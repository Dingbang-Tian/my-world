package com.dingbang.myworld;

import com.dingbang.myworld.aiframework.api.ModelGateway;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 为代码生成装配测试提供无需模型凭据的单次网关。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@TestConfiguration(proxyBeanMethods = false)
class ModuleAssemblySmokeTestFakeCodegenGatewayConfiguration {
    /**
     * 返回只用于上下文装配的假模型。
     *
     * @return 不访问网络的模型网关
     */
    @Bean
    ModelGateway fakeCodegenGateway() {
        return (request, listener) -> listener.onComplete();
    }
}
