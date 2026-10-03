package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.agent.api.AgentDefinition;
import com.dingbang.myworld.agent.api.AgentRequest;
import com.dingbang.myworld.agent.api.AgentResult;
import com.dingbang.myworld.agent.api.AgentRun;
import com.dingbang.myworld.agent.api.AgentService;
import com.dingbang.myworld.agent.prompt.PromptRepository;
import com.dingbang.myworld.agent.prompt.PromptTemplateSnapshot;
import com.dingbang.myworld.agent.session.AgentSessionService;
import com.dingbang.myworld.agent.session.InMemorySessionRepository;
import com.dingbang.myworld.agent.session.ImportedSession;
import com.dingbang.myworld.agent.session.Session;
import com.dingbang.myworld.agent.session.SessionExportCodec;
import com.dingbang.myworld.agent.session.SessionRepository;
import com.dingbang.myworld.agent.session.SessionSnapshot;
import com.dingbang.myworld.agent.skill.AgentSkill;
import com.dingbang.myworld.agent.tool.ToolRegistry;
import com.dingbang.myworld.aiframework.api.ModelGateway;
import com.dingbang.myworld.aiframework.api.ModelOptions;
import com.dingbang.myworld.aiframework.model.Message;
import com.dingbang.myworld.common.utils.collection.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;


/**
 * 用固定 Agent 定义、提示词仓库和单次模型入口实现公共 Agent 服务。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
public final class DefaultAgentService implements AgentService, AgentSessionService {

    /**
     * 单次模型调用入口。
     */
    private final ModelGateway gateway;

    /**
     * 执行模型调用的 JDK 后台执行器。
     */
    private final Executor executor;

    /**
     * 已加载的提示词仓库。
     */
    private final PromptRepository prompts;

    /**
     * 按应用和 Agent 标识固定的定义集合。
     */
    private final Map<String, Map<String, AgentDefinition>> definitions;

    /**
     * 可供可信定义选择的工具全集。
     */
    private final ToolRegistry tools;

    /**
     * 按标识索引的不可变技能。
     */
    private final Map<String, AgentSkill> skills;

    /**
     * 当前进程的会话历史。
     */
    private final SessionRepository sessions;

    /** 会话 JSON 编解码器。 */
    private final SessionExportCodec sessionCodec = new SessionExportCodec();

    /**
     * 创建不依赖具体模型供应商的 Agent 服务。
     *
     * @param gateway 单次模型调用入口
     * @param prompts 提示词仓库
     * @param definitions 可用 Agent 定义
     * @throws IllegalArgumentException 定义集合为空或含重复身份时
     */
    public DefaultAgentService(ModelGateway gateway, PromptRepository prompts,
                               Collection<AgentDefinition> definitions) {
        // 默认使用 JDK 公共线程池，使 execute 可以立即返回而不阻塞调用 Agent 的线程。
        this(gateway, prompts, definitions, AgentExecutors.WORK);
    }

    /**
     * 创建可指定后台执行器的 Agent 服务。
     *
     * @param gateway 单次模型调用入口
     * @param prompts 提示词仓库
     * @param definitions 可用 Agent 定义
     * @param executor 执行模型调用的后台执行器
     * @throws IllegalArgumentException 定义集合为空或含重复身份时
     */
    public DefaultAgentService(ModelGateway gateway, PromptRepository prompts,
                               Collection<AgentDefinition> definitions, Executor executor) {
        this(gateway, prompts, definitions, new ToolRegistry(Collections.emptyList()),
                Collections.emptyList(), executor);
    }

    /**
     * 创建支持工具与技能的 Agent 服务。
     *
     * @param gateway 单次模型调用入口
     * @param prompts 提示词仓库
     * @param definitions 可用 Agent 定义
     * @param tools 可信工具全集
     * @param skills 可用技能
     * @param executor 执行模型与工具的后台执行器
     */
    public DefaultAgentService(ModelGateway gateway, PromptRepository prompts,
                               Collection<AgentDefinition> definitions, ToolRegistry tools,
                               Collection<AgentSkill> skills, Executor executor) {
        this(gateway, prompts, definitions, tools, skills, executor, new InMemorySessionRepository());
    }

    /**
     * 创建使用可替换会话仓库的 Agent 服务。
     *
     * @param gateway 单次模型入口
     * @param prompts 提示词仓库
     * @param definitions 可信定义
     * @param tools 可信工具全集
     * @param skills 可用技能
     * @param executor 后台执行器
     * @param sessions 会话仓库
     */
    public DefaultAgentService(ModelGateway gateway, PromptRepository prompts,
                               Collection<AgentDefinition> definitions, ToolRegistry tools,
                               Collection<AgentSkill> skills, Executor executor, SessionRepository sessions) {
        this.gateway = Objects.requireNonNull(gateway, "模型入口不能为 null");
        this.prompts = Objects.requireNonNull(prompts, "提示词仓库不能为 null");
        this.executor = Objects.requireNonNull(executor, "模型执行器不能为 null");
        this.tools = Objects.requireNonNull(tools, "工具注册表不能为 null");
        this.sessions = Objects.requireNonNull(sessions, "会话仓库不能为 null");
        Objects.requireNonNull(skills, "技能集合不能为 null");
        /** 技能索引。 */
        Map<String, AgentSkill> indexedSkills = new LinkedHashMap<>();
        for (AgentSkill skill : skills) {
            Objects.requireNonNull(skill, "技能不能为 null");
            if (indexedSkills.putIfAbsent(skill.getSkillId(), skill) != null) {
                throw new IllegalArgumentException("重复技能标识: " + skill.getSkillId());
            }
        }
        this.skills = Collections.unmodifiableMap(indexedSkills);
        if (CollectionUtils.isEmpty(definitions)) {
            throw new IllegalArgumentException("至少需要一个 Agent 定义");
        }
        // 先按应用分组，再按 Agent 标识索引，运行时无需遍历全部定义。
        Map<String, Map<String, AgentDefinition>> byApp = new LinkedHashMap<>();
        for (AgentDefinition definition : definitions) {
            Objects.requireNonNull(definition, "Agent 定义不能为 null");
            Map<String, AgentDefinition> byAgent = byApp.computeIfAbsent(
                    definition.getAppId(), ignored -> new LinkedHashMap<>());
            if (byAgent.putIfAbsent(definition.getAgentId(), definition) != null) {
                throw new IllegalArgumentException("应用中存在重复 Agent: " + definition.getAgentId());
            }
        }
        // 构造完成后冻结定义，避免运行中的模型、模板身份被调用方修改。
        Map<String, Map<String, AgentDefinition>> frozen = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, AgentDefinition>> entry : byApp.entrySet()) {
            frozen.put(entry.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(entry.getValue())));
        }
        this.definitions = Collections.unmodifiableMap(frozen);
    }

    /**
     * 固定 Agent、会话和模板快照，创建未执行的运行句柄。
     *
     * @param request 用户请求
     * @return 未启动的 Agent 运行
     * @throws IllegalArgumentException Agent 或既有会话不存在、归属不符时
     */
    @Override
    public AgentRun prepare(AgentRequest request) {
        Objects.requireNonNull(request, "Agent 请求不能为 null");
        // prepare 只固定本轮输入和会话，不会调用 ModelGateway。
        // 从可信定义集合解析身份，用户请求不能自行指定模型或模板。
        Map<String, AgentDefinition> application = definitions.get(request.getAppId());
        AgentDefinition definition = application == null ? null : application.get(request.getAgentId());
        if (definition == null) {
            throw new IllegalArgumentException("未知的应用或 Agent");
        }
        // 在准备阶段预分配标识，模型调用仍要等到 AgentRun.execute()。
        String sessionId = request.getSessionId() == null ? UUID.randomUUID().toString() : request.getSessionId();
        String runId = UUID.randomUUID().toString();

        // 当前 run 固定模板内容和 hash，运行期间的配置变化不会影响它。
        PromptTemplateSnapshot template = prompts.get(definition.getPromptTemplateId());
        /** 直接工具和技能工具去重后的可信授权名称。 */
        LinkedHashSet<String> authorizedNames = new LinkedHashSet<>(definition.getToolIds());
        /** 技能说明，按照定义顺序加入系统模板。 */
        List<String> instructions = new ArrayList<>();
        for (String skillId : new LinkedHashSet<>(definition.getSkillIds())) {
            /** 当前启用的技能。 */
            AgentSkill skill = skills.get(skillId);
            if (skill == null) {
                throw new IllegalArgumentException("未知技能: " + skillId);
            }
            authorizedNames.addAll(skill.getToolIds());
            instructions.add(skill.getInstructions());
        }
        /** 运行级工具授权快照。 */
        ToolRegistry selectedTools = tools.select(authorizedNames);
        /** 当前运行的系统模板变量。 */
        Map<String, String> variables = promptVariables(definition, request, sessionId, runId,
                instructions);
        Message system = template.toSystemMessage(runId + ":system", variables);

        // 新会话在此登记；既有会话则校验它属于当前应用和 Agent。
        /** 已校验归属的会话。 */
        Session session = sessionFor(request, sessionId);
        return new DefaultAgentRun(runId, sessionId, definition, request, system, template, session, gateway,
                selectedTools, executor);
    }

    /**
     * 准备、启动并阻塞等待同一次运行结果。
     *
     * @param request 用户请求
     * @return 已完成的 Agent 结果
     */
    @Override
    public AgentResult run(AgentRequest request) {
        // 同步入口复用相同的运行句柄和事件链，不再额外创建模型请求。
        AgentRun agentRun = prepare(request);
        agentRun.execute();

        // 仅在同步便利入口阻塞；异步调用方可直接使用 AgentRun.getResult()。
        return agentRun.getResult().toCompletableFuture().join();
    }

    /**
     * 创建新会话或校验既有会话的应用归属。
     *
     * @param request 用户请求
     * @param sessionId 本次使用的会话标识
     * @return 进程内会话
     * @throws IllegalArgumentException 既有会话未知或归属不符时
     */
    private Session sessionFor(AgentRequest request, String sessionId) {
        if (request.getSessionId() == null) {
            return sessions.create(sessionId, request.getOwnerId(), request.getAppId(),
                    request.getAgentId(), ModelOptions.empty());
        }
        // 既有会话必须先存在，再检查其归属，不能只凭 sessionId 直接访问。
        /** 仓库中的既有会话。 */
        Session session = sessions.find(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("未知的会话标识");
        }
        session.requireOwner(request.getOwnerId(), request.getAppId(), request.getAgentId());
        return session;
    }

    /**
     * 创建绑定到已注册 Agent 的空会话。
     *
     * @param ownerId 所有者标识
     * @param appId 应用标识
     * @param agentId Agent 标识
     * @param options 会话级选项
     * @return 新会话标识
     */
    @Override
    public String createSession(String ownerId, String appId, String agentId, ModelOptions options) {
        requireDefinition(appId, agentId);
        if (ownerId == null || ownerId.isBlank()) throw new IllegalArgumentException("所有者不能为空");
        /** 新会话标识。 */
        String sessionId = UUID.randomUUID().toString();
        sessions.create(sessionId, ownerId, appId, agentId, Objects.requireNonNull(options));
        return sessionId;
    }

    /**
     * 读取通过归属校验的完整历史。
     *
     * @param ownerId 所有者标识
     * @param appId 应用标识
     * @param agentId Agent 标识
     * @param sessionId 会话标识
     * @return 历史快照
     */
    @Override
    public SessionSnapshot getSession(String ownerId, String appId, String agentId, String sessionId) {
        return ownedSession(ownerId, appId, agentId, sessionId).snapshot();
    }

    /**
     * 导出未运行的完整会话。
     *
     * @param ownerId 所有者标识
     * @param appId 应用标识
     * @param agentId Agent 标识
     * @param sessionId 会话标识
     * @return 带 schemaVersion 的 JSON
     */
    @Override
    public String exportSession(String ownerId, String appId, String agentId, String sessionId) {
        /** 已授权会话。 */
        Session session = ownedSession(ownerId, appId, agentId, sessionId);
        if (!session.tryStart()) throw new IllegalStateException("SESSION_BUSY");
        try {
            return sessionCodec.encode(sessionId, ownerId, appId, agentId, session.options(), session.snapshot());
        } finally {
            session.release();
        }
    }

    /**
     * 校验归属后将历史绑定到当前可信 Agent 定义。
     *
     * @param ownerId 所有者标识
     * @param appId 应用标识
     * @param agentId Agent 标识
     * @param serialized 会话 JSON
     * @return 恢复的会话标识
     */
    @Override
    public String importSession(String ownerId, String appId, String agentId, String serialized) {
        requireDefinition(appId, agentId);
        /** 已验证格式与完整性的导入数据。 */
        ImportedSession data = sessionCodec.decode(serialized);
        if (!data.getOwnerId().equals(ownerId) || !data.getAppId().equals(appId)
                || !data.getAgentId().equals(agentId)) {
            throw new IllegalArgumentException("会话归属不匹配");
        }
        sessions.importSession(data.getSessionId(), ownerId, appId, agentId, data.getVersion(),
                data.getOptions(), data.getMessages());
        return data.getSessionId();
    }

    /**
     * 获取并校验已注册的 Agent 定义。
     *
     * @param appId 应用标识
     * @param agentId Agent 标识
     */
    private void requireDefinition(String appId, String agentId) {
        if (!definitions.containsKey(appId) || !definitions.get(appId).containsKey(agentId)) {
            throw new IllegalArgumentException("未知的应用或 Agent");
        }
    }

    /**
     * 读取已校验归属的会话。
     *
     * @param ownerId 所有者标识
     * @param appId 应用标识
     * @param agentId Agent 标识
     * @param sessionId 会话标识
     * @return 已授权会话
     */
    private Session ownedSession(String ownerId, String appId, String agentId, String sessionId) {
        /** 仓库中的会话。 */
        Session session = sessions.find(sessionId);
        if (session == null) throw new IllegalArgumentException("未知的会话标识");
        session.requireOwner(ownerId, appId, agentId);
        return session;
    }

    /**
     * 构造只包含可信定义和运行标识的系统模板变量。
     *
     * @param definition Agent 定义
     * @param request 用户请求
     * @param sessionId 会话标识
     * @param runId 运行标识
     * @param instructions 已启用的技能说明
     * @return 模板变量快照
     */
    private Map<String, String> promptVariables(AgentDefinition definition, AgentRequest request,
                                                 String sessionId, String runId, List<String> instructions) {
        Map<String, String> variables = new LinkedHashMap<>();
        // 这里的全部变量来自服务端定义或运行上下文，用户文本不允许替换 SYSTEM 模板。
        // Agent 身份来自服务端定义，作为 SYSTEM 模板的可信输入。
        variables.put("agentName", definition.getName());
        variables.put("agentDescription", definition.getDescription());

        // Skill 和运行环境在后续阶段由实际注册信息及执行上下文替换。
        variables.put("skillInstructions", instructions.isEmpty() ? "无" : String.join("\n", instructions));
        variables.put("runtimeContext", "无");

        // 运行关联字段可供项目自定义模板引用，用户输入不会写入 SYSTEM 模板。
        variables.put("appId", request.getAppId());
        variables.put("agentId", request.getAgentId());
        variables.put("sessionId", sessionId);
        variables.put("runId", runId);
        return Collections.unmodifiableMap(variables);
    }
}
