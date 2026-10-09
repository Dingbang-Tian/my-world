package com.dingbang.myworld.news.kr36.controller;

import com.dingbang.myworld.common.api.ApiResponse;
import com.dingbang.myworld.news.kr36.entity.resp.Kr36NewsDigestResp;
import com.dingbang.myworld.news.kr36.service.NewsDigestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 36Kr 新闻日报预览接口。
 *
 * @author Sebastian
 * @since 2026/10/05
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/news/kr36/digest")
public class Kr36NewsDigestController {

    /**
     * 新闻日报服务。
     */
    private final NewsDigestService newsDigestService;

    /**
     * 生成一份当前日报预览，不触发推送。
     *
     * @return 新闻日报响应
     */
    @GetMapping("/preview")
    public ApiResponse<Kr36NewsDigestResp> preview() {
        // 生成当前日报预览。
        return ApiResponse.success(newsDigestService.generate());
    }
}
