package com.dingbang.myworld.aiapp.config.model;

import com.dingbang.myworld.aiframework.api.ModelGateway;
import com.dingbang.myworld.aiframework.api.ModelOptions;
import com.dingbang.myworld.aiframework.protocol.openai.OpenAiChatGateway;
import com.dingbang.myworld.aiframework.protocol.openai.OpenAiChatModelConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 按应用配置注册 OpenAI Chat 兼容的模型网关。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OpenAiChatProperties.class)
public class OpenAiChatConfiguration {

    /**
     * 在显式启用时创建按 modelId 选择模型的网关。
     *
     * @param properties 已绑定的模型属性
     * @return 可复用的单次模型网关
     */
    @Bean
    @ConditionalOnProperty(prefix = "my-world.ai.chat", name = "enabled", havingValue = "true")
    public ModelGateway openAiChatGateway(OpenAiChatProperties properties) {
        if (properties.getModels() == null || properties.getModels().isEmpty()) {
            throw new IllegalArgumentException("启用 Chat 网关时必须配置至少一个模型");
        }
        /** 每个 modelId 对应的不可变实例配置。 */
        List<OpenAiChatModelConfig> configs = new ArrayList<>();
        /** 当前本地模型标识及其已绑定配置。 */
        for (Map.Entry<String, OpenAiChatProperties.ModelProperties> entry : properties.getModels().entrySet()) {
            /** 当前模型的已绑定属性。 */
            OpenAiChatProperties.ModelProperties model = entry.getValue();
            configs.add(new OpenAiChatModelConfig(entry.getKey(), model.getProviderId(),
                    URI.create(model.getEndpoint()), model.getModel(), model.getApiKey(),
                    new ModelOptions(model.getTemperature(), model.getMaxCompletionTokens(),
                            model.getReasoningEffort()), model.isForwardReasoningContent()));
        }
        return new OpenAiChatGateway(configs);
    }
}
