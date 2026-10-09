package com.dingbang.myworld.news.kr36.entity.req;

import com.dingbang.myworld.news.kr36.entity.dto.FeishuTextContentDTO;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 飞书自定义机器人的文本消息请求。
 *
 * @author Sebastian
 * @since 2026/10/08
 */
@Data
@AllArgsConstructor
public class FeishuTextMessageReq {

    /**
     * 消息类型，文本消息使用 text。
     */
    @JsonProperty("msg_type")
    private String msgType;

    /**
     * 文本消息内容。
     */
    private FeishuTextContentDTO content;
}
