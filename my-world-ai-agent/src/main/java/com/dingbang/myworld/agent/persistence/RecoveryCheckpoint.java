package com.dingbang.myworld.agent.persistence;

import com.dingbang.myworld.aiframework.model.Message;
import lombok.Value;
import java.util.List;

/**
 * 保存可继续模型调用的已确认消息边界。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@Value
public class RecoveryCheckpoint {
    /**
     * 运行开始时的会话版本。
     */
    long sessionVersion;
    /**
     * 恢复后即将执行的模型回合编号。
     */
    int nextModelTurn;
    /**
     * 已确认且可安全复用的消息交换。
     */
    List<Message> exchange;
}
