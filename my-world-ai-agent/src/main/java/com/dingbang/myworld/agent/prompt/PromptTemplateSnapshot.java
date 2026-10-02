package com.dingbang.myworld.agent.prompt;

import com.dingbang.myworld.aiframework.model.Message;
import com.dingbang.myworld.aiframework.model.Role;
import com.dingbang.myworld.aiframework.model.content.contentImpl.TextContentBlock;
import com.dingbang.myworld.common.utils.lang.StringUtils;
import lombok.Data;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模板加载后的不可变内容、变量及内容哈希快照。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
@Data
public final class PromptTemplateSnapshot {

    /**
     * 只识别简单变量名，不执行 Java 或 SpEL 表达式。
     */
    private static final Pattern VARIABLE = Pattern.compile("\\{\\{([A-Za-z][A-Za-z0-9_]*)\\}\\}");

    /**
     * 模板标识。
     */
    private final String templateId;

    /**
     * 原始模板文本。
     */
    private final String content;

    /**
     * 原始模板文本的 SHA-256 十六进制摘要。
     */
    private final String contentHash;

    /**
     * 模板所需变量名快照。
     */
    private final Set<String> requiredVariables;

    /**
     * 校验并冻结已读取的模板。
     *
     * @param templateId 模板标识
     * @param content 模板文本
     * @throws IllegalArgumentException 模板为空或存在非法占位符时
     */
    PromptTemplateSnapshot(String templateId, String content) {
        if (StringUtils.isBlank(templateId) || StringUtils.isBlank(content)) {
            throw new IllegalArgumentException("模板标识和内容不能为空");
        }
        this.templateId = templateId;
        this.content = content;
        this.contentHash = sha256(content);

        // 先移除合法占位符，再拒绝剩余的表达式边界。
        Matcher matcher = VARIABLE.matcher(content);
        Set<String> variables = new LinkedHashSet<>();
        while (matcher.find()) {
            variables.add(matcher.group(1));
        }
        String remainder = matcher.replaceAll("");
        if (remainder.contains("{{") || remainder.contains("}}")) {
            throw new IllegalArgumentException("模板 " + templateId + " 含非法占位符");
        }
        this.requiredVariables = Collections.unmodifiableSet(variables);
    }

    /**
     * 单次替换模板变量；变量值中的双花括号作为普通文本保留。
     *
     * @param variables 变量值
     * @return 渲染后的提示词
     * @throws IllegalArgumentException 必需变量缺失或值为 null 时
     */
    public String render(Map<String, String> variables) {
        Objects.requireNonNull(variables, "模板变量不能为 null");
        Matcher matcher = VARIABLE.matcher(content);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = variables.get(name);
            if (value == null) {
                throw new IllegalArgumentException("模板 " + templateId + " 缺少变量 " + name);
            }
            matcher.appendReplacement(output, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    /**
     * 用渲染结果构造明确的 SYSTEM 角色消息。
     *
     * @param messageId 消息标识
     * @param variables 模板变量
     * @return 系统消息
     * @throws IllegalArgumentException 渲染结果为空或缺少变量时
     */
    public Message toSystemMessage(String messageId, Map<String, String> variables) {
        return new Message(messageId, Role.SYSTEM,
                Collections.singletonList(new TextContentBlock(render(variables))),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyMap());
    }

    /**
     * 计算文本的稳定 SHA-256 摘要。
     *
     * @param value 原始文本
     * @return 小写十六进制摘要
     */
    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte part : digest) {
                hex.append(Character.forDigit((part >>> 4) & 0x0f, 16));
                hex.append(Character.forDigit(part & 0x0f, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }
}
