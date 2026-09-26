package com.dingbang.myworld.web.ai;

import com.dingbang.myworld.ai.application.AiChatRequest;
import com.dingbang.myworld.ai.application.AiChatResponse;
import com.dingbang.myworld.ai.application.AiChatService;
import com.dingbang.myworld.common.api.ApiResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开发环境 AI 连通性测试接口。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
@Profile("dev")
@RestController
@RequestMapping("/api/ai")
public class AiConnectivityController {

    /**
     * 项目内部 AI 对话服务。
     */
    private final AiChatService aiChatService;

    /**
     * 创建 AI 连通性测试接口。
     *
     * @param aiChatService 项目内部 AI 对话服务
     */
    public AiConnectivityController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    /**
     * 调用一次最小对话请求以验证 DeepSeek 连通性。
     *
     * @return AI 对话结果
     */
    @PostMapping("/connectivity")
    public ApiResponse<AiChatResponse> checkConnectivity() {
        return ApiResponse.success(aiChatService.chat(
                new AiChatRequest("请只回复：OK", "connectivity-check")));
    }
}
