package com.dingbang.myworld.web.ai.connectivity;

import com.dingbang.myworld.ai.application.AiChatRequest;
import com.dingbang.myworld.ai.application.AiChatResponse;
import com.dingbang.myworld.ai.application.AiChatService;
import com.dingbang.myworld.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 连通性测试接口。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
@RestController
@ConditionalOnProperty(prefix = "my-world.ai.connectivity", name = "enabled", havingValue = "true")
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiConnectivityController {

    /**
     * 项目内部 AI 对话服务。
     */
    private final AiChatService aiChatService;

    /**
     * 调用一次最小对话请求以验证 DeepSeek 连通性。
     *
     * @return AI 对话结果
     */
    @PostMapping("/connectivity")
    public ApiResponse<AiChatResponse> checkConnectivity() {
        // 用于连通性验证的固定对话请求。
        AiChatRequest request = new AiChatRequest("请只回复：OK", "connectivity-check");
        // AI 服务返回的连通性结果。
        AiChatResponse response = aiChatService.chat(request);
        return ApiResponse.success(response);
    }
}
