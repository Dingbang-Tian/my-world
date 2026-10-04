package com.dingbang.myworld.aiframework.embedding;

import com.dingbang.myworld.aiframework.api.ModelGatewayException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 以独立模型注册表调用 OpenAI 文本和 DashScope 多模态向量接口。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public final class HttpEmbeddingGateway implements EmbeddingGateway {
    /**
     * 以本地标识索引的模型实例。
     */
    private final Map<String, EmbeddingModelConfig> models;
    /**
     * 单次 HTTP 客户端。
     */
    private final HttpClient client;
    /**
     * JSON 编解码器。
     */
    private final ObjectMapper mapper;

    /**
     * 创建默认 HTTP 客户端的网关。
     *
     * @param configs 向量模型实例
     */
    public HttpEmbeddingGateway(Collection<EmbeddingModelConfig> configs) {
        this(configs, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), new ObjectMapper());
    }

    /**
     * 创建可注入客户端的网关。
     *
     * @param configs 向量模型实例
     * @param client HTTP 客户端
     * @param mapper JSON 编解码器
     */
    public HttpEmbeddingGateway(Collection<EmbeddingModelConfig> configs, HttpClient client, ObjectMapper mapper) {
        // 注册中模型映射。
        Map<String, EmbeddingModelConfig> indexed = new LinkedHashMap<>();
        for (EmbeddingModelConfig config : Objects.requireNonNull(configs, "配置不能为空")) {
            if (indexed.putIfAbsent(config.getModelId(), config) != null) {
                throw new IllegalArgumentException("重复向量模型标识: " + config.getModelId());
            }
        }
        this.models = Map.copyOf(indexed);
        this.client = Objects.requireNonNull(client, "客户端不能为空");
        this.mapper = Objects.requireNonNull(mapper, "解析器不能为空");
    }

    /**
     * 验证能力后调用一次向量接口并校验结果。
     *
     * @param request 向量请求
     * @return 索引有序的向量结果
     */
    @Override
    public EmbeddingResult embed(EmbeddingRequest request) {
        Objects.requireNonNull(request, "向量请求不能为空");
        // 选中的模型实例。
        EmbeddingModelConfig config = models.get(request.getModelId());
        if (config == null) throw new ModelGatewayException("CONFIGURATION_ERROR", "未知向量模型: " + request.getModelId());
        // 请求维度。
        Integer dimensions = request.getDimensions() == null ? config.getDefaultDimensions() : request.getDimensions();
        if (config.getProtocol() == EmbeddingProtocol.OPENAI
                && (request.isFusion() || request.getInputs().stream().anyMatch(input -> input.hasMedia()
                || input.getParts().size() != 1))) {
            throw new ModelGatewayException("UNSUPPORTED_CAPABILITY", "OpenAI 文本向量接口只接受独立文本输入");
        }
        if (request.isFusion() && !config.isFusionSupported()) {
            throw new ModelGatewayException("UNSUPPORTED_CAPABILITY", "向量模型未启用融合能力");
        }
        if (request.isFusion() && config.isFusionParameterRequired() && request.getInputs().size() != 1) {
            throw new ModelGatewayException("INVALID_REQUEST", "整批融合向量一次只接受一个语义单元");
        }
        // 本次 JSON 请求体。
        ObjectNode body = config.getProtocol() == EmbeddingProtocol.OPENAI
                ? openAiBody(request, config, dimensions) : dashScopeBody(request, config, dimensions);
        // 完整 HTTP 请求。
        HttpRequest httpRequest = HttpRequest.newBuilder(config.getEndpoint()).timeout(Duration.ofSeconds(90))
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)).build();
        try {
            // 限制响应读取的 HTTP 结果。
            HttpResponse<String> response = client.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new ModelGatewayException("PROVIDER_ERROR", "向量接口 HTTP 状态码: " + response.statusCode());
            }
            return parseResponse(mapper.readTree(response.body()), request, config, dimensions);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ModelGatewayException("INTERRUPTED", "向量请求被中断");
        } catch (IOException error) {
            throw new ModelGatewayException("PROTOCOL_ERROR", "向量请求或响应无法读取: " + error.getClass().getSimpleName());
        }
    }

    /**
     * 编码 OpenAI 批量文本输入。
     *
     * @param request 向量请求
     * @param config 模型配置
     * @param dimensions 有效维度
     * @return 协议请求体
     */
    private ObjectNode openAiBody(EmbeddingRequest request, EmbeddingModelConfig config, Integer dimensions) {
        // JSON 请求根节点。
        ObjectNode body = mapper.createObjectNode();
        body.put("model", config.getWireModel());
        // 文本批量数组。
        ArrayNode input = body.putArray("input");
        for (EmbeddingInput item : request.getInputs()) input.add(item.getParts().get(0).getValue());
        if (dimensions != null) body.put("dimensions", dimensions);
        return body;
    }

    /**
     * 编码 DashScope 的独立或融合输入。
     *
     * @param request 向量请求
     * @param config 模型配置
     * @param dimensions 有效维度
     * @return 协议请求体
     */
    private ObjectNode dashScopeBody(EmbeddingRequest request, EmbeddingModelConfig config, Integer dimensions) {
        // JSON 请求根节点。
        ObjectNode body = mapper.createObjectNode();
        body.put("model", config.getWireModel());
        // DashScope 内容数组。
        ArrayNode contents = body.putObject("input").putArray("contents");
        for (EmbeddingInput item : request.getInputs()) {
            if (request.isFusion() && config.isFusionParameterRequired()) {
                for (EmbeddingPart part : item.getParts()) {
                    contents.addObject().put(part.getType().name().toLowerCase(), part.getValue());
                }
            } else if (request.isFusion()) {
                // 同一语义单元的融合内容。
                ObjectNode content = contents.addObject();
                // 多图片内容。
                List<String> images = new ArrayList<>();
                for (EmbeddingPart part : item.getParts()) {
                    if (part.getType() == EmbeddingInputType.IMAGE) images.add(part.getValue());
                    else if (part.getType() == EmbeddingInputType.TEXT) {
                        if (content.has("text")) throw new ModelGatewayException("INVALID_REQUEST", "融合输入至多包含一个文本片段");
                        content.put("text", part.getValue());
                    } else {
                        if (content.has("video")) throw new ModelGatewayException("INVALID_REQUEST", "融合输入至多包含一个视频片段");
                        content.put("video", part.getValue());
                    }
                }
                if (images.size() == 1) content.put("image", images.get(0));
                else if (!images.isEmpty()) images.forEach(content.putArray("multi_images")::add);
            } else {
                for (EmbeddingPart part : item.getParts()) {
                    contents.addObject().put(part.getType().name().toLowerCase(), part.getValue());
                }
            }
        }
        if (dimensions != null || request.isFusion()) {
            // 多模态生成选项。
            ObjectNode parameters = body.putObject("parameters");
            if (dimensions != null) parameters.put("dimension", dimensions);
            if (request.isFusion() && config.isFusionParameterRequired()) {
                parameters.put("enable_fusion", true);
            }
        }
        return body;
    }

    /**
     * 校验并归一化协议响应。
     *
     * @param root JSON 响应
     * @param request 原始请求
     * @param config 模型实例
     * @param dimensions 有效维度
     * @return 排序后的向量结果
     */
    private EmbeddingResult parseResponse(JsonNode root, EmbeddingRequest request,
                                          EmbeddingModelConfig config, Integer dimensions) {
        // 供应商向量数组。
        JsonNode data = config.getProtocol() == EmbeddingProtocol.OPENAI
                ? root.path("data") : root.path("output").path("embeddings");
        if (!data.isArray() || data.isEmpty()) throw new ModelGatewayException("PROTOCOL_ERROR", "向量响应缺少数据");
        // 经验证的向量列表。
        List<EmbeddingVector> vectors = new ArrayList<>();
        // 已见过的输入索引。
        Set<Integer> indexes = new HashSet<>();
        for (JsonNode item : data) {
            if (!item.path("index").isInt() || !item.path("embedding").isArray()) {
                throw new ModelGatewayException("PROTOCOL_ERROR", "向量索引或数组无效");
            }
            // 当前索引。
            int index = item.path("index").intValue();
            if (index < 0 || !indexes.add(index)) throw new ModelGatewayException("PROTOCOL_ERROR", "向量索引重复或越界");
            // 当前浮点向量。
            List<Double> values = new ArrayList<>();
            for (JsonNode value : item.path("embedding")) {
                if (!value.isNumber() || !Double.isFinite(value.doubleValue())) {
                    throw new ModelGatewayException("PROTOCOL_ERROR", "向量分量无效");
                }
                values.add(value.doubleValue());
            }
            if (dimensions != null && values.size() != dimensions) {
                throw new ModelGatewayException("PROTOCOL_ERROR", "向量维度与请求不符");
            }
            vectors.add(new EmbeddingVector(index, item.path("type").isTextual()
                    ? item.path("type").asText() : null, values));
        }
        vectors.sort(Comparator.comparingInt(EmbeddingVector::getIndex));
        if (config.getProtocol() == EmbeddingProtocol.OPENAI
                && (vectors.size() != request.getInputs().size() || vectors.get(0).getIndex() != 0
                || vectors.get(vectors.size() - 1).getIndex() != vectors.size() - 1)) {
            throw new ModelGatewayException("PROTOCOL_ERROR", "文本向量索引与批量输入不匹配");
        }
        if (vectors.stream().anyMatch(vector -> vector.getDimension() != vectors.get(0).getDimension())) {
            throw new ModelGatewayException("PROTOCOL_ERROR", "同批向量维度不一致");
        }
        // 供应商用量节点。
        JsonNode usage = root.path("usage");
        // 供应商各模态的可选 token 用量。
        EmbeddingUsage tokens = usage.isObject() ? new EmbeddingUsage(
                optionalLong(usage, "input_tokens", optionalLong(usage, "prompt_tokens", null)),
                optionalLong(usage, "output_tokens", null),
                optionalLong(usage, "total_tokens", null),
                optionalLong(usage, "image_tokens", optionalLong(usage.path("input_tokens_details"), "image_tokens", null)),
                optionalLong(usage.path("input_tokens_details"), "text_tokens", null)) : null;
        return new EmbeddingResult(request.getModelId(), root.path("request_id").isTextual()
                ? root.path("request_id").asText() : null, vectors, tokens);
    }

    /**
     * 读取供应商可选的非负整数用量。
     *
     * @param usage 用量 JSON
     * @param name 字段名称
     * @param fallback 字段不存在时的值
     * @return 报告值或回退值
     */
    private Long optionalLong(JsonNode usage, String name, Long fallback) {
        if (usage == null || !usage.has(name)) return fallback;
        if (!usage.path(name).canConvertToLong() || usage.path(name).longValue() < 0)
            throw new ModelGatewayException("PROTOCOL_ERROR", "向量用量字段无效: " + name);
        return usage.path(name).longValue();
    }
}
