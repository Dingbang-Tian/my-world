package com.dingbang.myworld.aiframework.protocol.openai;


/**
 * 保存同一个工具 index 的跨事件片段。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
final class OpenAiChatGatewayToolParts {
    /**
     * 供应商调用标识的片段。
     */
    final StringBuilder id = new StringBuilder();
    /**
     * 工具名称的片段。
     */
    final StringBuilder name = new StringBuilder();
    /**
     * JSON 参数的片段。
     */
    final StringBuilder arguments = new StringBuilder();
}
