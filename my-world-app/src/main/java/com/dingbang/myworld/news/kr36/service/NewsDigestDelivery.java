package com.dingbang.myworld.news.kr36.service;

import com.dingbang.myworld.news.kr36.entity.resp.Kr36NewsDigestResp;

/**
 * 新闻日报的可替换推送接口。
 *
 * @author Sebastian
 * @since 2026/10/05
 */
public interface NewsDigestDelivery {

    /**
     * 推送一份日报；失败时抛出异常以便下次重试。
     *
     * @param digest 待推送日报
     */
    void deliver(Kr36NewsDigestResp digest);
}
