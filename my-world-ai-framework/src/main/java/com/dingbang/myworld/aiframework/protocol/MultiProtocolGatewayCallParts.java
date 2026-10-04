package com.dingbang.myworld.aiframework.protocol;


/**
 * 保存同一工具流索引的跨事件状态。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
final class MultiProtocolGatewayCallParts {
    /**
     * 调用标识。
     */
    String id;
    /**
     * 工具名称。
     */
    String name;
    /**
     * 增量参数 JSON。
     */
    final StringBuilder arguments = new StringBuilder();
    /**
     * 协议提供的最终参数 JSON。
     */
    String finalArguments;
    /**
     * 最终参数是否已计入输出预算。
     */
    boolean finalCounted;
}
