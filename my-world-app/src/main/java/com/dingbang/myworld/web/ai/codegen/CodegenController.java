package com.dingbang.myworld.web.ai.codegen;

import com.dingbang.myworld.agent.api.AgentEventException;
import com.dingbang.myworld.aiapp.codegen.application.CodegenTaskState;
import com.dingbang.myworld.agent.session.SessionSnapshot;
import com.dingbang.myworld.common.api.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 暴露代码生成任务的创建、观察、取消和显式恢复入口。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@RestController
@ConditionalOnProperty(prefix = "my-world.codegen", name = "enabled", havingValue = "true")
@RequestMapping("/api/codegen/tasks")
public class CodegenController {

    /**
     * 代码生成 Web 应用服务。
     */
    private final CodegenWebService codegen;

    /**
     * 绑定代码生成 Web 应用服务。
     *
     * @param codegen 代码生成 Web 应用服务
     */
    public CodegenController(CodegenWebService codegen) {
        this.codegen = codegen;
    }

    /**
     * 接收仅含任务、请求标识和可选会话标识的输入。
     *
     * @param body 用户任务输入
     * @return 创建或复用的任务状态
     */
    @PostMapping
    public ApiResponse<CodegenTaskState> create(@RequestBody JsonNode body) {
        requireFields(body, Set.of("requestId", "sessionId", "task"));
        return ApiResponse.success(codegen.create(optionalText(body, "sessionId"),
                requiredText(body, "requestId"), requiredText(body, "task")));
    }

    /**
     * 查询运行状态、最终结果及文件产物元数据。
     *
     * @param runId 运行标识
     * @return 当前任务状态
     */
    @GetMapping("/{runId}")
    public ApiResponse<CodegenTaskState> status(@PathVariable String runId) {
        return ApiResponse.success(codegen.status(runId));
    }

    /**
     * 查询当前内存或持久化会话中的完整消息快照。
     *
     * @param sessionId 会话标识
     * @return 会话版本、消息和摘要
     */
    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<SessionSnapshot> session(@PathVariable String sessionId) {
        return ApiResponse.success(codegen.session(sessionId));
    }

    /**
     * 按事件序号订阅结构化 SSE，连接断开不取消后台运行。
     *
     * @param runId 运行标识
     * @param after 查询参数中的最后事件序号
     * @param lastEventId 浏览器重连携带的最后事件标识
     * @return 事件流
     */
    @GetMapping(value = "/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable String runId,
                             @RequestParam(name = "after", required = false) Long after,
                             @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId) {
        /**
         * 订阅起始序号。
         */
        long cursor = after == null ? parseLastEventId(lastEventId) : after;
        /**
         * 长运行的 SSE 连接。
         */
        SseEmitter emitter = new SseEmitter(0L);
        /**
         * 当前网络连接是否仍能接收事件。
         */
        AtomicBoolean open = new AtomicBoolean(true);
        emitter.onCompletion(() -> open.set(false));
        emitter.onTimeout(() -> open.set(false));
        emitter.onError(error -> open.set(false));
        codegen.subscribe(runId, cursor, new CodegenSseListener(emitter, open));
        return emitter;
    }

    /**
     * 显式取消当前进程中的运行。
     *
     * @param runId 运行标识
     * @return 取消后的状态
     */
    @PostMapping("/{runId}/cancel")
    public ApiResponse<CodegenTaskState> cancel(@PathVariable String runId) {
        return ApiResponse.success(codegen.cancel(runId));
    }

    /**
     * 在安全检查点恢复已中断运行。
     *
     * @param runId 来源运行标识
     * @param body 仅含新请求标识的输入
     * @return 新运行状态
     */
    @PostMapping("/{runId}/resume")
    public ApiResponse<CodegenTaskState> resume(@PathVariable String runId,
                                                @RequestBody JsonNode body) {
        requireFields(body, Set.of("requestId"));
        return ApiResponse.success(codegen.resume(runId, requiredText(body, "requestId")));
    }

    /**
     * 将请求异常映射为明确的客户端状态。
     *
     * @param exception 非法参数
     * @return 400 响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> invalid(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure("BAD_REQUEST", exception.getMessage()));
    }

    /**
     * 将幂等冲突、不可恢复状态映射为冲突响应。
     *
     * @param exception 当前状态冲突
     * @return 409 响应
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> conflict(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.failure("CONFLICT", exception.getMessage()));
    }

    /**
     * 将事件回放缺口映射为需要客户端重查状态的响应。
     *
     * @param exception 事件订阅异常
     * @return 410 响应
     */
    @ExceptionHandler(AgentEventException.class)
    public ResponseEntity<ApiResponse<Void>> eventGap(AgentEventException exception) {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(ApiResponse.failure(exception.getCode(), exception.getMessage()));
    }

    /**
     * 拒绝可能试图覆盖工作目录、工具或模板的额外字段。
     *
     * @param body JSON 对象
     * @param allowed 允许的字段名
     */
    private void requireFields(JsonNode body, Set<String> allowed) {
        if (body == null || !body.isObject()) throw new IllegalArgumentException("请求体必须为 JSON 对象");
        body.fieldNames().forEachRemaining(name -> {
            if (!allowed.contains(name)) throw new IllegalArgumentException("不支持的请求字段: " + name);
        });
    }

    /**
     * 读取必填的字符串字段。
     *
     * @param body JSON 对象
     * @param name 字段名
     * @return 字段文本
     */
    private String requiredText(JsonNode body, String name) {
        /**
         * 已解析字段。
         */
        JsonNode value = body.get(name);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException(name + " 必须为非空字符串");
        }
        return value.asText();
    }

    /**
     * 读取可选的字符串字段。
     *
     * @param body JSON 对象
     * @param name 字段名
     * @return 字段文本；未提供时为空
     */
    private String optionalText(JsonNode body, String name) {
        return body.hasNonNull(name) ? requiredText(body, name) : null;
    }

    /**
     * 解析浏览器重连的最后事件序号。
     *
     * @param value 请求头内容
     * @return 最后已接收序号
     */
    private long parseLastEventId(String value) {
        if (value == null || value.isBlank()) return 0L;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Last-Event-ID 必须为事件序号", exception);
        }
    }
}
