# 功能保留、改进与验收清单

状态说明：各行以“实现与证据”列为准；未填写证据的能力仍待完成。实施者应记录代码位置、测试名称/命令和结果，不能只勾选“已迁移”。

参考路径根为 Agent4J 的 `src/main/java/ink/icoding/llm/`，目标路径根见 ARCHITECTURE。

## 1. 原有公共能力对照

| ID | 必须保留的能力 | 参考位置 | 目标模块/阶段 | 实现与证据 |
|---|---|---|---|---|
| F01 | Agent 名称、描述、模型、工具和技能定义 | agent/AgentClient | ai-agent，S04/S05/S06 | `AgentDefinition` 含可信模型、工具、技能和回合上限；S06 假模型验证授权工具与技能说明，业务级配置接入待后续阶段 |
| F02 | 普通同步调用、流式文本与最终结果 | AgentSessionResult、LLMResult、ResultHandler | ai-framework/ai-agent，S02/S04/S07/S08 | S07 `OpenAiChatGatewayTest` 与 `OpenAiChatAgentIntegrationTest` 已验证本地 HTTP/SSE、增量和最终结果；真实供应商待验证；S08 本地 SSE 取消、超时与唯一终态已验证 |
| F03 | 工具完整闭环与调用结果回传 | 各模型的 handleToolCallsAndContinue 等方法 | ai-agent，S06 | `DefaultAgentRun` 统一追加 assistant/tool、执行工具并续问；`AgentToolLoopTest` 验证两个 callId 和最终文本；真实协议待 S07 |
| F04 | 工具注解、参数解析和 JSON Schema | core/tool/ToolDescriptor/ToolParam/annotations | ai-agent，S05 | `agent/tool/ToolDescriptor`、`tool/annotation`、`ToolRegistry` 已支持字符串、数值、布尔、枚举和列表/数组并严格校验；`ToolExecutorTest` 本地验证，协议适配待 S07 |
| F05 | 工具准备、执行、完成与错误回调 | ToolStatus、ToolExecutor、ResultHandler | ai-agent，S05/S08 | `ToolExecutor` 发布 PREPARING/CALLING/COMPLETED/FAILED；S06 映射为带 callId 的 Agent 事件并验证顺序，S08 已验证取消后后续工具不启动及工具上下文协作取消 |
| F06 | 工具异常反馈给模型，供模型修正 | ToolExecutor.handleToolError | ai-agent，S06 | S06 已验证参数失败为 `TOOL_VALIDATION_ERROR`、Java 工具不执行、模型据结果发出修正调用；其他异常的模型修正待扩展 |
| F07 | Skill 工具分组、使用说明、去重 | agent/Skill、builtin/skill | agent + codegen，S06/S10 | `AgentSkill` 按 ID 聚合工具和说明；运行时去重并写入 SYSTEM；S10/S11 `CodegenFactory` 只为 codegen 组装文件技能，按 write-enabled 选择 5 或 9 个工具；`CodegenServiceTest` 验证 |
| F08 | 关闭技能与内置工具，作为普通客户端使用 | AgentClient.clearAllSkills、getAllTools | ai-agent，S06/S09 | 定义可使用空 skillIds/toolIds；S04 原无工具对话和 S06 未授权工具错误已验证；管理 API 待 S09 |
| F09 | 多轮完整历史含 assistant 与工具消息 | AgentClientSession.executeCommand | ai-agent，S09/S18 | S09 `SessionRepository` 保存完整已完成交换及版本；S18 `MybatisSessionService` 在同一事务保存状态与唯一序号消息，`MybatisMysqlPersistenceTest` 验证 MySQL 跨上下文继续会话 |
| F10 | Token/轮次触发摘要、摘要回注、压缩事件 | AgentClientSession.summarizeHistory* | ai-agent，S15 | `ContextPolicy`、`ContextAssembler`、`MemorySummary` 与 `DefaultAgentRun.prepareInitialContext`；`ContextMemoryTest` 验证阈值、分块、工具配对、失败、超窗、事件及用量；原始历史保留 |
| F11 | 会话导出和恢复、配置恢复 | AgentClientSession.serialization/fromSerialization | ai-agent，S09/S15 | `SessionExportCodec` 导出可选 `memorySummary` 和完整原始历史；导入校验覆盖位置在交换边界并绑定当前可信定义与策略；`ContextMemoryTest.compressesRoundsAndRestoresSummary` 验证 |
| F12 | 计划创建、步骤执行、进度和失败事件 | Plan、executePlanTool、AgentResultHandler | ai-agent，S13 | `orchestration/Plan`、`PlanRunner`、`CreatePlanTool` 与 `DefaultAgentRun`；`PlanExecutionTest` 验证步骤依赖、STOP/CONTINUE、递归拒绝、共享预算和父取消；`CodegenCommandLoopTest.planCarriesCompileFailureIntoCorrectionStep` 验证真实编译失败与修正，完整 81/81 测试通过 |
| F13 | 子 Agent 定义、独立会话、结果回传 | executeSubAgentTool、CreateSubAgentTool | ai-agent，S14 | `CreateSubAgentTool`、`SubAgentRunner`、`DefaultAgentRun.startSubAgent`；`SubAgentExecutionTest` 验证独立历史、父工具子集、深度、失败、取消及预算；`CodegenServiceTest.delegatesReadOnlyCodeReviewToSubAgent` 验证实际只读文件审查；完整 89/89 通过 |
| F14 | 模型/会话/单次调用选项与覆盖 | LLMModel、AgentClient、AgentClientSession | ai-framework/agent，S07/S09/S16 | `ModelOptions` 逐层覆盖温度、输出上限、推理强度与开关；`RoutedModelGateway` 按 modelId 跨协议路由；`MultiProtocolGatewayTest` 与 `OpenAiChatGatewayTest` 验证调用隔离，真实供应商待验证 |
| F15 | thinking 配置、返回的 reasoning 内容和协议元数据 | 各模型、Message.think/thinkSignature | ai-framework，S07/S16 | Chat 保存/可选回传 `reasoning_content`；Responses 保存加密推理项；Anthropic 保存 `thinking`/签名，旧无签名历史仅显式开启回传；Qwen 推理开关显式配置并有 fixture，见 notes/S16.md |
| F16 | Token 用量回调、查询和汇总 | TokenUsage、LLMResult、AgentSessionResult | ai-framework/agent，S07/S14/S21 | `DefaultAgentRun` 按 modelCallId 去重汇总父子已报告用量；`RunAuditInterceptor` 记录单次及最终合计、`unknown/partial/reported` 状态，未报告不按零处理；`AgentRunControlTest`、`SubAgentExecutionTest` 与 notes/S21.md 验证 |
| F17 | 可开关的请求与 SSE 调试信息 | LLMRequestDebugLogger | ai-framework，S07/S21 | `SafeModelDebugGateway` 由 `-Dmyworld.agent.debug=true` 启用，只记录请求选项、消息/工具数量和流事件类型；内容脱敏。`RunAuditInterceptorTest.modelDebugOmitsRequestAndStreamContent` 验证 |
| F18 | 环境上下文提示、附件与多模态消息 | buildSystemContext、MessageAttachment 等 | agent/ai-framework/codegen，S10/S16 | `AgentRequest`、`MediaContentBlock`、`SessionExportCodec` 支持有界图片附件；三协议图片能力与不支持类型拒绝见 notes/S16.md；环境上下文由可信系统提示词/配置模板提供，音频/视频/文档聊天尚未支持 |

“保留”指能力与有效行为，不要求兼容 Agent4J 所有类名、错误行为或 JSON 私有实现。若对参考导出 JSON 提供导入支持，必须专门列格式版本与迁移测试；第一版默认只保证新系统自身的导出/导入，不冒称兼容旧格式。

## 2. 十二个内置工具逐项核对

| 工具名 | 必须覆盖的行为 | 归属 | 阶段 | 实现与证据 |
|---|---|---|---|---|
| list_directory_tree | 目录树、深度控制、文件/目录区分 | ai-app/codegen/tool | S10 | `FileTools`；`FileToolsTest` 临时目录验证 |
| view_file | 全文/范围、1 起始行号、大文件截断及总行数提示 | 同上 | S10 | `FileTools`；`FileToolsTest` 临时目录验证 |
| search_files | 按文件名关键词查找，多个关键词 | 同上 | S10 | `FileTools`；`FileToolsTest` 临时目录验证 |
| search_in_file | 文件内搜索，多个关键词，匹配行与上下文 | 同上 | S10 | `FileTools`；`FileToolsTest` 临时目录验证 |
| search_in_directory | 递归内容搜索，文件名/行号/上下文 | 同上 | S10 | `FileTools`；`FileToolsTest` 临时目录验证 |
| create_file | 文件内容写入、必要父目录创建、明确覆盖策略 | 同上 | S11 | `FileTools` + `WorkspacePolicy`；`FileToolsTest` 验证版本和拒绝副作用 |
| edit_file | replace、insert、append，行号边界和换行处理 | 同上 | S11 | `FileTools` + `WorkspacePolicy`；`FileToolsTest` 验证版本和拒绝副作用 |
| move_file | 移动或重命名、目标父目录、目标冲突 | 同上 | S11 | `FileTools` + `WorkspacePolicy`；`FileToolsTest` 验证版本和拒绝副作用 |
| delete_file | 删除文件，目标缺失与非文件的明确结果 | 同上 | S11 | `FileTools` + `WorkspacePolicy`；`FileToolsTest` 验证版本和拒绝副作用 |
| execute_command | 平台 shell、cwd、退出码、输出、超时、取消 | 同上 | S12 | `ExecuteCommandTool`；`ExecuteCommandToolTest` 与 `CodegenCommandLoopTest` 验证本机执行、限额、取消和编译纠错；可枚举进程的环境中子进程取消/超时回收 2/2 通过，见 notes/S12.md |
| create_plan | 创建并执行顺序计划，步骤上下文和事件 | ai-agent/orchestration | S13 | `CreatePlanTool` 仅在可信定义授权时提供；步骤提示词、结果与工具产物显式传递，`PlanExecutionTest` 和 `CodegenServiceTest.executesEnabledPlanWithSharedFileTools` 验证 |
| create_sub_agent | 创建子任务会话、继承授权能力、返回结果 | ai-agent/orchestration | S14 | `CreateSubAgentTool` 与 `SubAgentRunner`；模型只能请求父授权工具子集，省略 `toolIds` 时无工具；独立会话与 runId、配对父 callId 的结果由 `SubAgentExecutionTest` 验证 |

每个参数的名称、默认值和匹配语义，实施时对照对应 Tool 与 builtin/param 文件共同确认。发现 README/注释和方法体不同，以实际实现作为分析起点，并记录最终采用的行为。前三类技能（文件、命令、编排）同样全部提供，只按应用配置启用。

## 3. 模型与数据能力对照

| 协议/能力 | 验收要求 | 阶段 | 实现与证据 |
|---|---|---|---|
| OpenAI Chat 及兼容供应商 | 单次请求、SSE、tool calls、usage、取消、结束原因 | ai-framework，S07/S08 | S07 本地 HTTP fixture 验证单次请求、SSE、多 index 分片、usage 尾块、结束原因、401/429/500 与断流；真实供应商及S08 已验证取消后后续工具不启动及工具上下文协作取消/独立冒烟 |
| OpenAI Responses | 独立请求/事件/工具结果映射；与统一模型契约一致 | ai-framework，S16 | `MultiProtocolGateway` 的 Responses 编码/聚合；`MultiProtocolGatewayTest.responsesToolLoopAndImage` 本地 fixture 验证工具、推理、usage、图片、续问 |
| Anthropic Messages | system、content blocks、tool_use/result、reasoning 元数据和完成事件 | ai-framework，S16 | `MultiProtocolGateway` 的 Anthropic 编码/聚合；`MultiProtocolGatewayTest.anthropicToolLoopAndSignature` 验证系统、工具、推理签名、用量、结束 |
| MiMo/Qwen 等参考兼容处理 | 实际字段与历史处理有 fixture，不依赖供应商名猜测全部能力 | ai-framework，S16 | 模型实例显式启用无签名推理历史、Chat `reasoning_content` 回传和 Qwen 推理开关；对应 `MultiProtocolGatewayTest`、`OpenAiChatGatewayTest` fixture |
| 附件和多模态聊天 | 根据参考实现列出具体媒体/协议支持表；不支持的组合显式拒绝 | ai-framework，S16 | `MediaContentBlock` 与各协议本地能力检查；图片来源/MIME 矩阵与不支持组合见 notes/S16.md；音频/视频/文档聊天未实现 |
| OpenAI Embedding | 批量文本、维度、索引、向量和 usage | ai-framework，S17 | `HttpEmbeddingGateway`；`HttpEmbeddingGatewayTest.openAiBatchIndexesAndUsage` 验证 |
| DashScope 多模态 Embedding | 文本、图片、视频、融合、维度与向量类型 | ai-framework，S17 | `HttpEmbeddingGateway`；`HttpEmbeddingGatewayTest.dashScopeFusionAndMediaSources` 验证 URL、data URI、内存媒体与融合 |
| 多模型配置/切换 | 通过 modelId 查找，请求参数隔离；不修改 singleton 来模拟会话选项 | ai-framework，S07/S16 | `OpenAiChatConfiguration` 按 modelId 组合 Chat/Responses/Anthropic；`EmbeddingConfiguration` 独立注册向量模型；fixture 验证调用隔离 |
| 自定义模型协议扩展 | 增加适配器及注册即通过契约，不改 Agent 循环 | ai-framework，S16/S22 | `RoutedModelGateway` 注册任意 `ModelGateway` 实现即可复用 Agent 循环；外部自定义适配器的端到端契约测试留待 S22 |

离线协议验证、真实供应商验证、平台执行验证分开记录。某平台/供应商没有环境可测，应保留未验证标记，不把缺凭据解释为接口已验证可用。

## 4. 此次明确改进的参考行为

| 参考实现中需要关注的点 | 新实现要求 | 对应验收 |
|---|---|---|
| 每个具体模型中实现 Agent 循环 | 循环集中到 agent；模型仅执行一轮 | 同一假模型/三协议通过同一循环测试 |
| systemPrompt 使用 fromUser 构造 | 模型中立消息显式支持 SYSTEM，适配器按协议转换 | 请求体角色断言 |
| 回调注册注释与实际 execute 行为不一致 | 显式 prepare/execute，禁止重复启动，文档说明阻塞边界 | 生命周期测试 |
| 提示词、技能说明写在 Java 中 | 配置/资源外置，默认与覆盖可测试 | 模板覆盖与变量验证 |
| 计划步骤结果未明确成为下一步输入 | 显式传递前序结果及产物，失败有实际状态 | 两步骤依赖场景 |
| 特殊工具通过 lastCreatedPlan/lastTask 传状态 | 当前 run 参数和结果直接传递，无跨运行可变中转 | 两会话并发无污染 |
| 子 Agent 能共享模型/工具实例 | 服务可共享，状态/权限/配置快照独立 | 配置隔离和授权子集测试 |
| 命令先读输出再限时等待 | 总 deadline 从启动开始，并发有界读取 | 无换行阻塞/持续输出超时测试 |
| 摘要完成后清空活动历史 | 数据库保留原始消息，摘要只改变上下文选取 | 历史可查、摘要覆盖一致 |
| 运行中的模型与工具状态难以恢复 | 持久化状态、意图、结果，未知副作用进入待处理 | 故障注入和检查点恢复 |
| 不同来源的 usage 可能难以汇总 | 每次模型调用有唯一 ID，根预算集中计数 | 父子与计划去重测试 |

本表是迁移设计方向；执行时再次核对参考方法当前内容。不能把这里的问题描述自动变成对参考仓库的修复任务。

## 5. 必做企业级基础待办（纳入 S00–S22）

- [x] 稳定 SDK 与结构化事件，不暴露供应商客户端类型。`AgentSdk`、`CodegenSdk` 的独立 Maven 消费者和 dev SSE 测试见 notes/S20.md。
- [ ] ModelGateway、Tool、PromptRepository、SessionRepository 等扩展契约；至少模型和存储具备可替换实现。
- [ ] 应用级 AgentDefinition、modelId、toolIds、skillIds 和提示词覆盖。
- [ ] 运行次数/工具次数/时间/输出/子层级限制，可被应用配置收紧。
- [ ] 取消传播、单终态、资源清理，不留下无人管理的执行任务。
- [ ] 文件路径与工作目录策略、工具授权、参数校验和输出限制。
- [ ] owner/app 范围的会话访问校验，以及单会话并发冲突处理。
- [ ] 完整会话、摘要、运行事件、工具结果、计划步骤和产物持久化。
- [x] requestId 数据库唯一约束；不将其解释为外部副作用 exactly-once。见 `MybatisMysqlPersistenceTest` 和历史回归 `JdbcPersistenceRecoveryTest.rejectsConcurrentLeaseAndDuplicateRequest`。
- [x] 启动中断标记与显式恢复；未知副作用为 `NEEDS_REVIEW`，不盲目重跑。见 `JdbcPersistenceRecoveryTest`。
- [x] 日志脱敏、追踪关联、Token/耗时指标、配置和模板 hash 记录。见 `RunAuditInterceptor`、`SafeModelDebugGateway`、`RunAuditInterceptorTest` 和 notes/S21.md。
- [ ] 确定性回归案例、协议契约测试、SDK 消费者 smoke、平台/供应商验证边界。
- [x] Flyway MySQL V1 迁移脚本及真实 MySQL 跨上下文继续会话测试；H2/JDBC 历史回归保留在测试目录，见 notes/S18.md。
- [x] 对外代码生成入口默认按配置启用，身份来源清晰。`CodegenController` 仅在 `my-world.codegen.enabled=true` 时注册，ownerKey 来自服务端配置；本地开发仍可通过 dev profile 提供该配置，见 notes/S20.md。

## 6. 后续扩展待办（不算第一版功能缺失）

P1 表示完成本计划后优先考虑；P2 表示需求出现时再做。不要为完成这些待办拖延原功能验收。

| 优先级 | 功能想法 | 价值 | 前提 |
|---|---|---|---|
| P1 | 提示词数据库版本、发布、回滚、热更新 | 无需重启调整策略，能定位结果使用的版本 | 模板快照和 hash 已稳定 |
| P1 | 长期记忆：事实、来源、有效期、修正/删除 | 跨会话复用可信信息 | 原始记录与摘要已分离；定义记忆写入策略 |
| P1 | 检索增强：切分、索引、召回、引用 | 面向项目文档与知识库 | Embedding 已完成；选定索引存储 |
| P1 | 人工确认/审批型工具流程 | 敏感变更前挂起并等待人处理 | 持久化运行状态与恢复 |
| P1 | 容器/独立工作进程执行器 | 用于不可信代码、多用户环境的真实隔离 | 文件/命令接口及取消协议明确 |
| P1 | 接入项目身份系统、多租户配额、数据保留策略 | 正式多用户服务 | owner/app 验证、审计与数据库实现 |
| P1 | 生产数据库适配与集成测试 | 对接最终选定的 MySQL/PostgreSQL 等环境 | 数据库选择与部署环境确定 |
| P1 | 结构化输出 Schema 与业务结果校验 | 应用可靠消费生成结果 | 模型能力及错误契约稳定 |
| P2 | 模型路由、降级、熔断和缓存 | 根据成本/延迟/可用性选模型 | 保留 provider 元数据，不跨协议错误重放历史 |
| P2 | 并行子 Agent 和 DAG 计划 | 提升独立任务吞吐 | 配额、依赖、写冲突、取消和结果汇总已定义 |
| P2 | MCP/远程工具适配器 | 接入外部工具生态 | 工具认证与授权边界明确 |
| P2 | 分布式任务队列、租约心跳和调度恢复 | 长任务跨实例运行 | 持久化运行机与幂等策略完善 |
| P2 | 自动评价、提示词 A/B 与成本质量对比 | 用数据改进 Agent 行为 | 回归案例、版本和指标数据 |
| P2 | 可视化调试台和生成差异审阅 | 提升排障与代码审查体验 | 事件、产物和状态 API 稳定 |

## 7. 最终验收场景

| ID | 输入/故障 | 应观察的结果 |
|---|---|---|
| A01 | 无工具普通任务 | S04 假模型与 S07 本地 HTTP fixture 验证单次文本请求、增量和完整回合；真实供应商待验证 |
| A02 | 需要一个工具的任务 | S06 假模型覆盖同轮两个调用；S07 本地 HTTP/Agent 集成验证完整 callId 历史与续问 |
| A03 | 工具参数非法 | S07 本地 HTTP/Agent 集成验证错误参数不执行 Java 工具、`TOOL_VALIDATION_ERROR` 回传模型，修正调用后仅执行一次 |
| A04 | 生成一个小型 Java 程序 | S12 `CodegenCommandLoopTest` 的确定性假模型在临时 workspace 创建 HelloAgent.java；首次 javac 退出 1，读取 hash 并修正后退出 0，`FileArtifact` 和 `CommandReport` 均可查询 |
| A05 | 插入/替换/追加/移动/删除 | 十二工具对应行为和边界全部有验证 |
| A06 | 两步骤计划有数据依赖 | S13 `PlanExecutionTest.passesPriorArtifactAndReportsLifecycle` 验证下一步读取前一步模型结果和工具产物；`stopAndContinueDoNotClaimSuccess` 与 codegen 编译纠错轨迹验证失败状态准确 |
| A07 | 子 Agent 检查产物 | S14 `CodegenServiceTest.delegatesReadOnlyCodeReviewToSubAgent`：父有写权限，子仅获得 `view_file`，读取临时 Review.java 后父取得配对结果，文件未改；`SubAgentExecutionTest` 验证独立历史和用量不双算 |
| A08 | 对话超压缩阈值 | 原始历史仍可查；下一请求使用摘要；工具交换完整 |
| A09 | 会话正常关闭后重启 | `MybatisMysqlPersistenceTest` 验证 MySQL 上下文重建后的历史和运行记录；H2 历史回归继续验证摘要与后续回答 |
| A10 | 外部副作用后模拟落库失败 | `MybatisMysqlPersistenceTest` 验证 MySQL 上结果未知的命令进入 `NEEDS_REVIEW`、结果已落库的工具可恢复；`JdbcPersistenceRecoveryTest` 保留故障窗口和计划步骤边界的确定性回归 |
| A11 | 模型断流/任务取消/命令超时 | S04/S08 已验证模型和 Agent 终态；S12 验证命令超时、父取消与直接进程回收，并在允许进程枚举的本机环境验证取消和超时后的子进程回收；受限沙箱下只能保证直接进程清理 |
| A12 | 两会话使用不同模型参数 | S09 `AgentSessionServiceTest` 验证不同 owner 会话的历史与温度参数隔离、单次覆盖及同会话 `SESSION_BUSY`；S07 双 modelId 回归继续通过 |
| A13 | 同一任务分别接三协议 fixture | 上层循环无需修改，模型消息语义一致 |
| A14 | 两类 Embedding 输入 | 向量/索引/usage 正确，非法模型类型和媒体组合明确拒绝 |
| A15 | 项目覆盖默认提示词并重启 | 本次请求使用新版本，代码无需改动，模板 hash 可追踪；S03 已验证覆盖优先级与快照，S04 已用本地假模型验证新建服务后的请求 SYSTEM 文本和结果 hash 随文件版本变化，真实进程重启待后续集成验证 |
| A16 | 新建一个自定义工具和模拟协议 | 注册后可用，Agent 核心无新增供应商 switch 或业务判断 |
| A17 | 独立 Maven 消费者依赖 Agent | 普通 JAR 可运行，无需依赖启动模块或 codegen 内部类 |
| A18 | 既有普通对话和 ENC 配置 | 原功能回归通过，凭据不进入默认日志和会话导出 |

最终报告同时写明：已实现范围、通过的测试与命令、真实供应商冒烟结果、未验证平台、仍待处理的限制。若任何必做条目缺实现，项目不得标为完整完成。
