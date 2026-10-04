package com.dingbang.myworld.agent.memory;

import com.dingbang.myworld.aiframework.model.Message;
import com.dingbang.myworld.aiframework.model.Role;
import com.dingbang.myworld.aiframework.model.content.TextContentBlock;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证可替换输入估算器能根据真实用量收敛并保留安全余量。
 *
 * @author Sebastian
 * @since 2026/10/05
 */
class CalibratedTokenEstimatorTest {
    /**
     * 验证大样本校准和短调用忽略规则。
     */
    @Test
    void calibratesLargeRequestsAndKeepsSmallSamplesOut() {
        /** 含较长中文内容的输入消息。 */
        List<Message> messages = List.of(new Message("u", Role.USER,
                List.of(new TextContentBlock("请检查代码".repeat(500))), List.of(), List.of(), Map.of()));
        /** 使用基础估算器的上下文组装器。 */
        ContextAssembler assembler = new ContextAssembler(new CalibratedTokenEstimator());
        /** 校准前的字节估算。 */
        int before = assembler.estimate(messages, List.of());
        assembler.observe(before, 120);
        assertThat(assembler.estimate(messages, List.of())).isEqualTo(before);
        assembler.observe(before, 2000);
        /** 校准后仍高于供应商本次真实用量。 */
        int after = assembler.estimate(messages, List.of());
        assertThat(after).isGreaterThan(2000).isLessThan(before);
        /** 自定义估算器可直接替换默认实现。 */
        assertThat(new ContextAssembler((input, tools) -> 42).estimate(messages, List.of())).isEqualTo(42);
    }
}
