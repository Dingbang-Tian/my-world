package com.dingbang.myworld.agent.runtime;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.dingbang.myworld.agent.api.AgentDefinition;
import com.dingbang.myworld.agent.api.AgentRequest;
import com.dingbang.myworld.agent.api.AgentResult;
import com.dingbang.myworld.agent.api.AgentResultStatus;
import com.dingbang.myworld.agent.prompt.PromptTemplateRegistry;
import com.dingbang.myworld.agent.tool.ToolExecutionEvent;
import com.dingbang.myworld.agent.tool.ToolExecutionPhase;
import com.dingbang.myworld.aiframework.api.ModelFinishReason;
import com.dingbang.myworld.aiframework.api.ModelRequest;
import com.dingbang.myworld.aiframework.api.ModelTurn;
import com.dingbang.myworld.aiframework.api.SafeModelDebugGateway;
import com.dingbang.myworld.aiframework.api.event.TextDelta;
import com.dingbang.myworld.aiframework.api.event.TurnCompleted;
import com.dingbang.myworld.aiframework.model.Message;
import com.dingbang.myworld.aiframework.model.Role;
import com.dingbang.myworld.aiframework.model.ToolResult;
import com.dingbang.myworld.aiframework.model.ToolResultStatus;
import com.dingbang.myworld.aiframework.model.content.TextContentBlock;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证运行审计关联、未知用量和敏感内容脱敏。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
class RunAuditInterceptorTest {
    /**
     * 真实运行的日志应带关联与耗时，而不泄露用户内容或请求标识。
     */
    @Test
    void recordsCorrelatedRunWithoutUserContent() {
        // 审计类的测试日志入口。
        Logger logger = (Logger) LoggerFactory.getLogger(RunAuditInterceptor.class);
        // 暂存原日志级别。
        Level previous = logger.getLevel();
        // 收集本测试运行产生的日志。
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
        try {
            // 不报告 token 用量的假模型。
            ScriptedAgentModelGateway gateway = new ScriptedAgentModelGateway((request, listener) -> {
                // 含敏感字符串的助手消息。
                Message answer = new Message("answer", Role.ASSISTANT,
                        List.of(new TextContentBlock("SECRET_MODEL_OUTPUT")), List.of(), List.of(), Map.of());
                listener.onEvent(new TurnCompleted(new ModelTurn(answer, ModelFinishReason.STOP)));
                listener.onComplete();
            });
            // 使用默认模板的服务。
            DefaultAgentService service = new DefaultAgentService(gateway,
                    new PromptTemplateRegistry(new DefaultResourceLoader(), Map.of(), Map.of()),
                    List.of(new AgentDefinition("app", "assistant", "助手", "回答", "model", null)));
            // 执行后的真实结果。
            AgentResult result = service.run(new AgentRequest("app", "assistant", null,
                    "SECRET_REQUEST_ID", "SECRET_USER_INPUT"));
            // 审计日志的文本快照。
            String logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (left, right) -> left + "\n" + right);
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.COMPLETED);
            assertThat(logs).contains("event=start", "event=finish", "agent_model", "usageStatus=unknown",
                    "traceId=" + result.getRunId(), "durationMillis=");
            assertThat(logs).doesNotContain("SECRET_REQUEST_ID", "SECRET_USER_INPUT", "SECRET_MODEL_OUTPUT");
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previous);
            appender.stop();
        }
    }

    /**
     * 工具审计只记录阶段、状态和耗时，不记录敏感工具结果。
     */
    @Test
    void toolAuditOmitsResultContent() {
        // 审计类的测试日志入口。
        Logger logger = (Logger) LoggerFactory.getLogger(RunAuditInterceptor.class);
        // 收集工具阶段的日志。
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            // 绑定父子追踪的审计拦截器。
            RunAuditInterceptor audit = new RunAuditInterceptor("trace-1", "child-1", "session-1", "parent-1");
            audit.onEvent(new ToolExecutionEvent("call-1", "read_file", ToolExecutionPhase.PREPARING, null));
            audit.onEvent(new ToolExecutionEvent("call-1", "read_file", ToolExecutionPhase.COMPLETED,
                    new ToolResult("call-1", ToolResultStatus.SUCCESS, "SECRET_API_KEY", null, false)));
            // 两个工具阶段的结构化日志。
            String logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (left, right) -> left + "\n" + right);
            assertThat(logs).contains("traceId=trace-1", "parentRunId=parent-1", "tool=read_file",
                    "phase=COMPLETED", "durationMillis=");
            assertThat(logs).doesNotContain("SECRET_API_KEY");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    /**
     * 开启模型调试时也只记录事件类型，不输出请求和流分片原文。
     */
    @Test
    void modelDebugOmitsRequestAndStreamContent() {
        // 模型调试类的测试日志入口。
        Logger logger = (Logger) LoggerFactory.getLogger(SafeModelDebugGateway.class);
        // 暂存原日志级别。
        Level previous = logger.getLevel();
        // 收集本测试的调试日志。
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        try {
            // 返回敏感流文本的模型入口。
            SafeModelDebugGateway gateway = new SafeModelDebugGateway((request, listener) -> {
                listener.onEvent(new TextDelta("SECRET_STREAM_CONTENT"));
                listener.onComplete();
            });
            // 含敏感请求文本的消息。
            Message user = new Message("user", Role.USER,
                    List.of(new TextContentBlock("SECRET_REQUEST_CONTENT")), List.of(), List.of(), Map.of());
            gateway.generate(new ModelRequest("model", List.of(user)), new AssertNoErrorModelListener());
            // 本次模型调试的日志文本。
            String logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (left, right) -> left + "\n" + right);
            assertThat(logs).contains("event=request", "event=stream", "type=TextDelta", "event=complete",
                    "content=[REDACTED]");
            assertThat(logs).doesNotContain("SECRET_REQUEST_CONTENT", "SECRET_STREAM_CONTENT");
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previous);
            appender.stop();
        }
    }
}
