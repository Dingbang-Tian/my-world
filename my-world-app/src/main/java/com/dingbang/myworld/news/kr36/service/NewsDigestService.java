package com.dingbang.myworld.news.kr36.service;

import com.dingbang.myworld.news.kr36.entity.resp.Kr36NewsDigestResp;

/**
 * 36Kr 新闻日报业务服务。
 *
 * @author Sebastian
 * @since 2026/10/05
 */
public interface NewsDigestService {

    /**
     * 生成当前时间窗口内的新闻日报。
     *
     * @return 新闻日报响应
     */
    Kr36NewsDigestResp generate();
}
