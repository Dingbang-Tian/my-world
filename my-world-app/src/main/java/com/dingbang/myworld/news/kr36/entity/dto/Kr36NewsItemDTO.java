package com.dingbang.myworld.news.kr36.entity.dto;

import lombok.Data;
import lombok.AllArgsConstructor;

import java.time.Instant;

/**
 * 从 36Kr RSS 读取的一条新闻数据传输对象。
 *
 * @author Sebastian
 * @since 2026/10/05
 */
@Data
@AllArgsConstructor
public class Kr36NewsItemDTO {

    /**
     * 新闻标题。
     */
    private String title;

    /**
     * 原文链接。
     */
    private String link;

    /**
     * RSS 摘要文本。
     */
    private String description;

    /**
     * 新闻发布时间。
     */
    private Instant publishedAt;

}
