package com.dingbang.myworld.news.kr36.entity.resp;

import com.dingbang.myworld.news.kr36.entity.dto.Kr36NewsItemDTO;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 待推送的 36Kr 每日新闻摘要响应。
 *
 * @author Sebastian
 * @since 2026/10/05
 */
@Data
@AllArgsConstructor
public class Kr36NewsDigestResp {

    /**
     * 日报日期。
     */
    private LocalDate date;

    /**
     * AI 生成的摘要及原文索引。
     */
    private String content;

    /**
     * 本次采用的新闻。
     */
    private List<Kr36NewsItemDTO> articles;

}
