package com.dingbang.myworld.aiapp.config.model;

import com.dingbang.myworld.aiframework.api.ModelGateway;
import com.dingbang.myworld.aiframework.api.ModelOptions;
import com.dingbang.myworld.aiframework.protocol.openai.OpenAiChatGateway;
import com.dingbang.myworld.aiframework.protocol.openai.OpenAiChatModelConfig;
import com.dingbang.myworld.aiframework.protocol.MultiProtocolGateway;
import com.dingbang.myworld.aiframework.protocol.MultiProtocolModelConfig;
import com.dingbang.myworld.aiframework.protocol.RoutedModelGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

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
        /** OpenAI Chat 模型实例配置。 */
        List<OpenAiChatModelConfig> chatConfigs = new ArrayList<>();
        /** Responses 与 Anthropic 模型实例配置。 */
        List<MultiProtocolModelConfig> otherConfigs = new ArrayList<>();
        /** 本地模型到协议适配器的路由。 */
        Map<String, ModelGateway> routes = new LinkedHashMap<>();
        /** 当前本地模型标识及其已绑定配置。 */
        for (Map.Entry<String, OpenAiChatProperties.ModelProperties> entry : properties.getModels().entrySet()) {
            /** 当前模型的已绑定属性。 */
            OpenAiChatProperties.ModelProperties model = entry.getValue();
            /** 此模型的默认调用选项。 */
            ModelOptions options = new ModelOptions(model.getTemperature(), model.getMaxCompletionTokens(),
                    model.getReasoningEffort(), model.getThinkingEnabled());
            if ("chat".equalsIgnoreCase(model.getProtocol())) {
                chatConfigs.add(new OpenAiChatModelConfig(entry.getKey(), model.getProviderId(),
                        URI.create(model.getEndpoint()), model.getModel(), model.getApiKey(), options,
                        model.isForwardReasoningContent(), model.isImageEnabled(), model.isThinkingSwitchEnabled()));
            } else {
                /** 当前显式选择的协议。 */
                MultiProtocolModelConfig.Protocol protocol = switch (model.getProtocol().toLowerCase()) {
                    case "responses" -> MultiProtocolModelConfig.Protocol.RESPONSES;
                    case "anthropic" -> MultiProtocolModelConfig.Protocol.ANTHROPIC;
                    default -> throw new IllegalArgumentException("未知聊天协议: " + model.getProtocol());
                };
                otherConfigs.add(new MultiProtocolModelConfig(entry.getKey(), protocol,
                        URI.create(model.getEndpoint()), model.getModel(), model.getApiKey(), options,
                        model.isImageEnabled(), model.isReasoningEnabled(), model.isForwardUnsignedThinking()));
            }
        }
        if (otherConfigs.isEmpty()) return new OpenAiChatGateway(chatConfigs);
        if (chatConfigs.isEmpty()) return new MultiProtocolGateway(otherConfigs);
        if (!chatConfigs.isEmpty()) {
            /** 共享的 Chat 协议适配器。 */
            ModelGateway gateway = new OpenAiChatGateway(chatConfigs);
            for (OpenAiChatModelConfig config : chatConfigs) routes.put(config.getModelId(), gateway);
        }
        if (!otherConfigs.isEmpty()) {
            /** 共享的 Responses/Anthropic 协议适配器。 */
            ModelGateway gateway = new MultiProtocolGateway(otherConfigs);
            for (MultiProtocolModelConfig config : otherConfigs) routes.put(config.modelId(), gateway);
        }
        return new RoutedModelGateway(routes);
    }
}
