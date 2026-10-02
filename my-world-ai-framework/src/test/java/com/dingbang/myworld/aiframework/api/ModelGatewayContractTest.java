package com.dingbang.myworld.aiframework.api;

import com.dingbang.myworld.aiframework.api.event.ValidatingModelEventListener;
import com.dingbang.myworld.aiframework.api.event.TextDelta;
import com.dingbang.myworld.aiframework.api.event.TurnCompleted;
import com.dingbang.myworld.aiframework.model.Message;
import com.dingbang.myworld.aiframework.model.Role;
import com.dingbang.myworld.aiframework.model.ToolCall;
import com.dingbang.myworld.aiframework.model.content.TextContentBlock;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证单次模型事件的完整回合、工具调用和失败语义。
 *
 * @author Sebastian
 * @since 2026/10/01
 */
class ModelGatewayContractTest {

    /**

     * 验证文本增量只用于展示，完整助手消息由唯一终态携带。

     */
    @Test
    void emitsDeltasAndOneCompleteTurnPerCall() {
        // 用户输入消息。
        Message user = textMessage("user-1", Role.USER, "只回复 OK");
        // 模型完整助手回合。
        ModelTurn turn = new ModelTurn(textMessage("assistant-1", Role.ASSISTANT, "OK"),
                ModelFinishReason.STOP);
        // 顺序返回两个增量和一个完整回合的假模型。
        ScriptedModelGateway gateway = new ScriptedModelGateway((request, listener) -> {
            listener.onEvent(new TextDelta("O"));
            listener.onEvent(new TextDelta("K"));
            listener.onEvent(new TurnCompleted(turn));
            listener.onComplete();
        });
        // 发给假模型的单轮消息快照。
        ModelRequest request = new ModelRequest("scripted", Collections.singletonList(user));
        // 记录通过完整回合校验的模型事件。
        RecordingModelEventListener recorder = new RecordingModelEventListener();

        gateway.generate(request, new ValidatingModelEventListener(recorder));

        assertThat(recorder.getEvents()).containsExactly(
                new TextDelta("O"),
                new TextDelta("K"),
                new TurnCompleted(turn));
        assertThat(recorder.isCompleted()).isTrue();
        assertThat(recorder.getError()).isNull();
        assertThat(turn.getAssistantMessage().getContentBlocks())
                .containsExactly(new TextContentBlock("OK"));
        assertThat(gateway.getCallCount()).isEqualTo(1);
    }

    /**

     * 验证完整工具调用及系统消息在请求快照中保留原义。

     */
    @Test
    void preservesSystemRoleToolCallAndRequestSnapshot() {
        // 模型给出的工具调用及完整参数 JSON。
        ToolCall call = new ToolCall("call-1", "add", "{\"a\":2,\"b\":3}");
        // 请求中的系统及用户消息。
        ArrayList<Message> sourceMessages = new ArrayList<>(Arrays.asList(
                textMessage("system-1", Role.SYSTEM, "你是计算助手"),
                textMessage("user-1", Role.USER, "计算 2+3")));
        // 已复制消息列表的模型请求。
        ModelRequest request = new ModelRequest("scripted", sourceMessages);
        sourceMessages.clear();
        // 只提出工具调用、尚未执行工具的助手消息。
        Message assistant = new Message("assistant-1", Role.ASSISTANT, Collections.emptyList(),
                Collections.singletonList(call), Collections.emptyList(), Collections.emptyMap());
        // 以工具请求结束的完整模型回合。
        ModelTurn turn = new ModelTurn(assistant, ModelFinishReason.TOOL_CALLS);
        // 返回完整工具调用的假模型。
        ScriptedModelGateway gateway = new ScriptedModelGateway((modelRequest, listener) -> {
            assertThat(modelRequest.getMessages()).hasSize(2);
            assertThat(modelRequest.getMessages().get(0).getRole()).isEqualTo(Role.SYSTEM);
            listener.onEvent(new TurnCompleted(turn));
            listener.onComplete();
        });
        // 记录模型终态。
        RecordingModelEventListener recorder = new RecordingModelEventListener();

        gateway.generate(request, new ValidatingModelEventListener(recorder));

        assertThat(recorder.getEvents()).containsExactly(new TurnCompleted(turn));
        assertThat(recorder.isCompleted()).isTrue();
        assertThat(recorder.getError()).isNull();
        assertThat(turn.getAssistantMessage().getToolCalls()).containsExactly(call);
        assertThat(call.getCallId()).isEqualTo("call-1");
        assertThat(call.getArgumentsJson()).isEqualTo("{\"a\":2,\"b\":3}");
        assertThat(gateway.getCallCount()).isEqualTo(1);
    }

    /**

     * 验证脚本异常通过模型错误回调传播。

     */
    @Test
    void propagatesScriptFailure() {
        // 抛出预定失败的假模型。
        ScriptedModelGateway gateway = new ScriptedModelGateway((request, listener) -> {
            throw new IllegalStateException("script failure");
        });
        // 记录模型错误。
        RecordingModelEventListener recorder = new RecordingModelEventListener();

        gateway.generate(new ModelRequest("scripted", Collections.singletonList(
                textMessage("user-1", Role.USER, "你好"))), new ValidatingModelEventListener(recorder));

        assertThat(recorder.getError()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("script failure");
        assertThat(recorder.isCompleted()).isFalse();
    }

    /**

     * 验证正常结束时缺失完整回合或重复完整回合都会失败。

     */
    @Test
    void rejectsMissingAndRepeatedCompletion() {
        // 只返回文本增量的模型请求。
        ModelRequest request = new ModelRequest("scripted", Collections.singletonList(
                textMessage("user-1", Role.USER, "你好")));
        // 未发出完整回合的假模型。
        ScriptedModelGateway missing = new ScriptedModelGateway((ignored, listener) -> {
            listener.onEvent(new TextDelta("你"));
            listener.onComplete();
        });
        // 记录缺失完整回合的校验错误。
        RecordingModelEventListener missingRecorder = new RecordingModelEventListener();

        missing.generate(request, new ValidatingModelEventListener(missingRecorder));

        assertThat(missingRecorder.getError()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("缺少完整回合");

        // 错误地发出两个终态的完整回合。
        TurnCompleted completed = new TurnCompleted(
                new ModelTurn(textMessage("assistant-1", Role.ASSISTANT, "你好"),
                        ModelFinishReason.STOP));
        // 重复发出完整回合的假模型。
        ScriptedModelGateway repeated = new ScriptedModelGateway((ignored, listener) -> {
            listener.onEvent(completed);
            listener.onEvent(completed);
            listener.onComplete();
        });
        // 记录重复完整回合的校验错误。
        RecordingModelEventListener repeatedRecorder = new RecordingModelEventListener();

        repeated.generate(request, new ValidatingModelEventListener(repeatedRecorder));

        assertThat(repeatedRecorder.getError()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("完整回合之后");
    }

    /**
     * 构造没有工具调用的文本消息。
     *
     * @param messageId 消息标识
     * @param role 消息角色
     * @param text 消息文本
     * @return 不可变文本消息
     */
    private static Message textMessage(String messageId, Role role, String text) {
        return new Message(messageId, role, Collections.singletonList(new TextContentBlock(text)),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyMap());
    }
}
