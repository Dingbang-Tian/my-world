package com.dingbang.myworld.news.kr36.entity.resp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 飞书自定义机器人接收消息后的业务响应。
 *
 * @author Sebastian
 * @since 2026/10/08
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FeishuWebhookResp {

    /**
     * 业务状态码，只有 0 表示成功。
     */
    private Integer code;

    /**
     * 飞书返回的业务结果说明。
     */
    private String msg;
}
