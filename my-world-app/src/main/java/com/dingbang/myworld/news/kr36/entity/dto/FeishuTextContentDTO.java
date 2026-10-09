package com.dingbang.myworld.news.kr36.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 飞书机器人文本消息的正文。
 *
 * @author Sebastian
 * @since 2026/10/08
 */
@Data
@AllArgsConstructor
public class FeishuTextContentDTO {

    /**
     * 待发送的文本。
     */
    private String text;
}
