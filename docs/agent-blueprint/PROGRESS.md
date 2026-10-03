# 工程进度与学习交接

最后更新：2026/10/03（S15 上下文与摘要已完成本地验证，等待学习复述）。

## 1. 当前定位

- 目标项目：`/Users/sebastian/myPorject/myWorld`。
- 当前图纸：目标项目 `docs/agent-blueprint/` 工作副本；参考项目 `doc/myworld-agent-blueprint/` 保留为原始图纸。
- 下一阶段：S16；S15、S14 及此前阶段的待回答问题仍保留。
- 当前阶段已实现内容：S15 的可信上下文策略、保守输入估算、轮次和 Token 阈值、无工具分块摘要、原子覆盖位置、摘要回注与压缩事件、摘要用量和导出恢复；原始历史仍完整保存。
- 当前验证边界：默认 JDK 8 无法构建 Java 21 项目；普通沙箱禁止本地端口绑定和 Java 枚举进程树。完整测试已在允许本地 HTTP fixture 的本机执行环境通过；未调用真实供应商。
- 基线只读观察：2026/10/01 开始时工作区干净，分支 feat-20260925-projInit-Sebastian。
- 本次交付与证据：见 [notes/S15.md](notes/S15.md)。JDK 21 下完整 `mvn -q test` 95/95 通过、无失败/错误/跳过；S15 测试 6/6 通过。未调用真实供应商。

## 2. 阶段状态

工程状态使用“未开始 / 进行中 / 已验证 / 阻塞”；学习状态使用“未开始 / 已讲解 / 待回答 / 已掌握”。只有用户反馈才能标记已掌握。

| 阶段 | 主题 | 工程 | 学习 | 验证/笔记链接 |
|---|---|---|---|---|
| S00 | 基线与图纸接入 | 已验证 | 待回答 | [notes/S00.md](notes/S00.md) |
| S01 | 模块与装配 | 已验证 | 待回答 | [notes/S01.md](notes/S01.md) |
| S02 | 模型契约与假模型 | 已验证 | 已掌握 | [notes/S02.md](notes/S02.md) |
| S03 | 提示词配置 | 已验证 | 待回答 | [notes/S03.md](notes/S03.md) |
| S04 | Agent SDK 普通对话 | 已验证 | 待回答 | [notes/S04.md](notes/S04.md) |
| S05 | 工具系统 | 已验证 | 待回答 | [notes/S05.md](notes/S05.md) |
| S06 | Agent 循环 | 已验证 | 待回答 | [notes/S06.md](notes/S06.md) |
| S07 | Chat 协议 | 已验证 | 待回答 | [notes/S07.md](notes/S07.md) |
| S08 | 流式、取消、预算 | 已验证 | 待回答 | [notes/S08.md](notes/S08.md) |
| S09 | 会话与序列化 | 已验证 | 待回答 | [notes/S09.md](notes/S09.md) |
| S10 | Codegen 与读取工具 | 已验证 | 待回答 | [notes/S10-S11.md](notes/S10-S11.md) |
| S11 | 文件变更工具 | 已验证 | 待回答 | [notes/S10-S11.md](notes/S10-S11.md) |
| S12 | 命令与生成闭环 | 已验证 | 待回答 | [notes/S12.md](notes/S12.md) |
| S13 | 计划 | 已验证 | 待回答 | [notes/S13.md](notes/S13.md) |
| S14 | 子 Agent | 已验证 | 待回答 | [notes/S14.md](notes/S14.md) |
| S15 | 摘要与上下文 | 已验证 | 待回答 | [notes/S15.md](notes/S15.md) |
| S16 | 多协议与多模态 | 未开始 | 未开始 | — |
| S17 | Embedding | 未开始 | 未开始 | — |
| S18 | 数据库存储 | 未开始 | 未开始 | — |
| S19 | 检查点恢复 | 未开始 | 未开始 | — |
| S20 | SDK 消费与入口 | 未开始 | 未开始 | — |
| S21 | 治理与排障 | 未开始 | 未开始 | — |
| S22 | 完整验收 | 未开始 | 未开始 | — |

## 3. 基线验证（S00 填写）

| 检查 | 实际结果 |
|---|---|
| 目标规则文件 | 目标仓库内未找到 AGENTS.md；本会话用户提供的 Java 注释规则适用。未新增 Java 代码。 |
| 当前分支/用户未提交改动 | `feat-20260925-projInit-Sebastian`；S00 开始前 `git status --short` 为空。 |
| Java/Maven | 默认 Java 8u462、Maven 3.6.3；项目声明 Java 21。本机 Homebrew JDK 21.0.12.1 可用，显式设置 `JAVA_HOME` 后 Maven 使用 Java 21。 |
| 原有 mvn test | 默认 JDK 8 下失败于 `my-world-ai` 测试编译，字节码版本 61.0 与 52.0 不兼容；显式 JDK 21 下构建成功，`ConfigSecretCipherTest` 9/9 通过，其余模块无测试。 |
| 凭据是否可用于真实冒烟（仅记录可用性） | 本次进程环境中 `DEEPSEEK_API_KEY`、`MY_WORLD_CONFIG_MASTER_KEY` 均未设置；未尝试真实模型调用。未读取或记录凭据值。 |
| 数据库/容器等本地条件 | 当前 POM 未引入数据库持久化；本机有 Docker CLI，未启动容器或验证 daemon；S00 不要求外部数据库。 |
| 必须先处理的阻塞 | 后续构建须使用 JDK 21；无其他已确认阻塞。 |

## 4. 架构决策记录

用户已确认：AI 模块分离 Spring AI 与自研框架、SDK 接入、codegen 收敛在 ai-app/codegen、完整功能保留、配置提示词、后续热更新。

图纸默认：单次模型适配器 + 唯一 Agent 循环；沿用现有普通对话；Java 监听器事件；本地 JDBC/H2；顺序编排；按阶段教学。它们来自工程规划，修改时记录证据即可，不把它们误称为用户逐项作出的选择。

| 日期 | 决策/变更 | 原因与证据 | 影响阶段 | 状态 |
|---|---|---|---|---|
| 2026/10/01 | 建立当前四模块工程图纸 | 本会话确认 Spring AI 接入与自研框架需要独立 Maven 依赖边界 | 全部 | 已调整 |
| 2026/10/01 | S00 保持源码与 POM 原状，仅接入文档；测试命令显式选择已安装的 JDK 21 | 默认 Java 8 使 `mvn test` 编译失败；JDK 21 下原有 9 个测试通过 | S00–S01 | 已验证 |
| 2026/10/01 | S01 新模块产普通 JAR，启动模块保留唯一可执行包；配置通过 `@Import` 串联 | 根 POM、离线依赖树、Jar 内容与上下文 smoke 均验证依赖方向 | S01–S02 | 已验证 |
| 2026/10/01 | 装配 smoke 由 JUnit 显式启动 Spring Boot 上下文，使用无 Mockito 的假模型与测试用占位 Key | 本机 Mockito inline 自行附加失败；最终 smoke 1/1 通过且未调用模型 | S01 测试 | 已验证 |
| 2026/10/01 | S02 数据契约使用 Java 8 风格普通不可变类；空白字符串与空集合判断复用 common 工具包；Javadoc 统一为多行 | 用户明确提出风格要求；JDK 21 下契约测试和现有回归共 14/14 通过 | S02 及后续 Java 代码 | 已验证 |
| 2026/10/01 | S02 值对象保留 `final`，统一使用 Lombok `@Data`；接口和实现拆成顶层文件 | 字段全部为 `final`，不会生成 setter；`@Data` 统一生成 getter、值比较和 `toString`；`ContentBlock`/`TextContentBlock`、`ModelEvent`/`TextDelta`/`TurnCompleted` 等不使用内部类；JDK 21 下回归测试通过 | S02 数据对象 | 已验证 |
| 2026/10/01 | 新增 `my-world-ai-framework`，迁移 S02 契约；`my-world-ai` 恢复为 Spring AI 接入模块 | framework 只依赖 common、Lombok；agent 依赖 framework，不再导入 `SpringAiConfiguration`；clean test 和离线依赖树通过，framework/agent 编译依赖中没有 Spring AI | S01/S02 模块边界 | 已验证 |
| 2026/10/02 | 按用户要求移除自研 framework 与 agent 的 Reactor API | `ModelGateway` 改为 Java 8 监听器回调；`AgentRun` 改为 `subscribe(listener)`；保留 JDK 后台执行、有限回放、唯一执行、失败终态和 `CompletionStage`；完整 clean test 通过 | S02/S04 可读性与模块依赖 | 已验证 |
| 2026/10/02 | S03 模板按项目 YAML > 应用资源 > 公共默认资源合并，启动时冻结内容与 hash | `PromptTemplateRegistryTest` 和 `AgentPromptConfigurationTest` 9/9，包含文件变更后重建仓库、SYSTEM 角色与无效配置启动失败 | S03/S04/S15 | 已验证 |
| 2026/10/02 | S04 `prepare` 固定模板与会话，`execute` 唯一启动模型；事件与 Future 共享同一运行 | `DefaultAgentServiceTest` 6/6，含多观察者、历史、失败、会话忙、工具调用拒绝和模板新版本请求 | S04–S09 | 已验证 |
| 2026/10/02 | 保留 `ModelGateway`、`AgentService/AgentRun` 与模型 `Message/ToolCall/ToolResult` 的职责命名；工具注解入 `tool/annotation`，事件和文本实现收平无职责的 `*Impl` 包 | 对照真实单次模型、运行 API、模型消息和 Java 工具边界；包调整后全项目构建验证 | S02–S07 | 已验证 |
| 2026/10/02 | S05 工具类型由 `Tool<P>.parameterType()` 显式提供；注册时拒绝不支持的参数结构，运行按授权快照解析与执行 | `ToolExecutorTest` 覆盖成功、非法参数、未知/冲突工具、异常、枚举/列表及显式描述 | S05–S06 | 已验证 |
| 2026/10/02 | S06 在 `DefaultAgentRun` 内集中续问；模型请求只携带纯描述，工具执行前校验 callId 与回合上限 | `AgentToolLoopTest` 验证多调用配对、历史、参数纠错、重复标识与回合上限；全项目 clean test 通过 | S06–S08 | 已验证 |
| 2026/10/03 | S07 以 JDK HttpClient 实现一次请求的 OpenAI Chat SSE 网关；Spring 应用只装配模型配置，工具执行仍由 Agent 负责 | 本地 HTTP fixture 覆盖分片、多工具索引、usage 尾块、HTTP 错误、断流、未知结束原因及实际 Agent 纠错闭环；全项目回归通过 | S07–S08 | 已验证 |
| 2026/10/03 | S08 用可信 AgentLimits 限制全局时间、回合、工具和字符；状态锁裁定唯一终态，取消令牌贯通 Chat SSE 与工具 | 本地脚本模型、Java 工具、HTTP fixture 覆盖取消、超时、迟到错误、输出限额、事件缺口和 usage 去重；55/55 测试通过 | S08–S09/S12 | 已验证 |
| 2026/10/03 | S09 将进程内会话移至可替换仓库；可信 owner/app/Agent 三重校验，独占执行和版本提交；导出只保留会话数据并在导入时重新绑定当前定义 | `AgentSessionServiceTest` 覆盖隔离、选项、导出恢复、工具配对、版本冲突、忙会话与取消；全项目 58/58 测试通过 | S09/S18 | 已验证 |
| 2026/10/03 | S12 命令能力独立开关，环境白名单与工作目录显式配置；有界异步读取与启动时限；按 runId 返回文件产物和命令报告 | JDK 21 完整 `mvn test` 74/74 通过；命令测试含取消/超时子进程清理 6/6 通过 | S12/S19 | 已验证 |
| 2026/10/03 | S13 在唯一 Agent 运行时内执行顺序计划；步骤模型共用父取消、时限、回合、工具和输出预算；codegen 按配置显式开启计划 | `PlanExecutionTest`、`CodegenServiceTest` 与 `CodegenCommandLoopTest` 验证依赖、失败策略、递归限制、预算、取消和真实 javac 纠错；完整 81/81 通过 | S13/S14 | 已验证 |
| 2026/10/03 | S14 子 Agent 复用唯一 runtime，独立会话与运行标识；父级明确传上下文和已授权工具子集，顺序异步等待，传播取消并收紧剩余预算与深度 | `SubAgentExecutionTest` 覆盖历史隔离、权限、嵌套、预算、失败、取消及单线程执行器；`CodegenServiceTest` 使用真实只读文件工具审查产物；完整 89/89 通过 | S14/S18/S21 | 已验证 |
| 2026/10/03 | S15 在可信定义中配置上下文窗口；仅摘要完整历史交换，分块调用无工具模型，成功后原子更新摘要覆盖位置，原始历史永久保留；摘要与普通调用共用预算 | `ContextMemoryTest` 覆盖轮次/Token 阈值、工具交换、分块、防重复、失败、超长、超窗、事件、用量、导出恢复；完整回归见 notes/S15.md | S15/S16/S18 | 已验证 |

## 5. S00 阶段交接（历史）

```text
阶段：S00 基线盘点与图纸接入
目标与已跑通流程：读取原始图纸 → 盘点目标模块与入口 → 复制图纸 → JDK 21 下运行原有 Maven 测试
已改文件（路径/核心方法）：docs/agent-blueprint/ 下五份图纸与 notes/S00.md；无 Java 方法改动
新增依赖或配置：无；构建命令临时指定已安装的 JDK 21
验证命令、结果和环境：见 notes/S00.md；JDK 21 下 mvn test 成功，9 个配置解密测试通过
对照 CHECKLIST 的条目及证据：S00 仅建立基线；A01–A18 未进入功能验收
来源实现与新实现的差异：本阶段未迁入来源源码；参考项目含 GPL v3 LICENSE，后续迁移须记录来源与原作者
未完成工作／真实阻塞：S01 尚未开始；默认 JDK 8 不适合项目构建
本阶段用户已理解的内容：已指出 conversationId 只是标识，现有适配器未管理历史；其余回答已反馈，尚待准确复述
待用户回答的问题：见 notes/S00.md 的用户回答与反馈
下一会话最先读取的文件：本 PROGRESS.md、ARCHITECTURE.md、CHECKLIST.md、STEPS.md 的 S01
下一步具体动作：核对 S00 答案，再建立 AI 模块并验证装配
```

## 6. S01 阶段交接

```text
阶段：S01 新模块与启动装配
目标与已跑通流程：启动模块 → AI 应用配置 → Agent 配置 → 现有模型/普通对话配置；假模型没有收到请求
已改文件（路径/核心方法）：根及 app POM；`my-world-ai-framework`、`my-world-ai-agent`、`my-world-ai-app` POM；AgentModuleConfiguration、AiApplicationConfiguration；ModuleAssemblySmokeTest；详见 notes/S01.md
新增依赖或配置：ai-agent 依赖 ai-framework，ai-app 依赖 ai-agent，app 分别依赖 ai 与 ai-app；测试配置 s01-smoke-test.yml 使用非真实占位 Key
验证命令、结果和环境：JDK 21 下 clean test 成功；framework 契约 4/4，原配置解密 9/9，装配 smoke 1/1；离线 dependency:tree 显示 framework/agent 编译依赖无 Spring AI；新模块均为普通 JAR
对照 CHECKLIST 的条目及证据：S01 完成模块与装配前提；A01–A18 功能场景尚未实现
来源实现与新实现的差异：参考 AgentClient 单类持有模型/工具/技能；目标先设 Maven 边界，未迁入源码
未完成工作／真实阻塞：S02 消息契约已完成；Spring AI 与自研 framework 的真实 gateway 适配尚未实现；无已确认实施阻塞；本机测试避免 Mockito inline 附加
本阶段用户已理解的内容：已指出反向依赖会形成循环，也能指出装配测试未验证 DeepSeek 等远程连通；已澄清测试只覆盖 Spring 配置与 AiChatService Bean 创建，见 notes/S01.md
待用户回答的问题：用“库与入口”区分两类 JAR；S00 模块职责与运行职责还需复述
下一会话最先读取的文件：本 PROGRESS.md、notes/S01.md、notes/S02.md、ARCHITECTURE.md、CHECKLIST.md、STEPS.md 的 S03
下一步具体动作：回答 S02 学习问题后，引入配置化提示词及默认覆盖
```

## 7. S02 阶段交接

```text
阶段：S02 单次模型消息与假模型
目标与已跑通流程：ModelRequest → ScriptedModelGateway.generate → ModelEventListener 回调 TextDelta → 唯一 TurnCompleted(ModelTurn) → onComplete
已改文件（路径/核心方法）：my-world-ai-framework 的 aiframework/model、aiframework/api 契约、ModelEventListener/ValidatingModelEventListener、ScriptedModelGateway、ModelGatewayContractTest；my-world-ai 下的 S02 文件已移除；详见 notes/S02.md
新增依赖或配置：无；使用 JDK 监听器、Lombok 和 my-world-common 工具包
验证命令、结果和环境：JDK 21 下 mvn -q test 成功；S02 4/4，原有 10/10，共 14/14
对照 CHECKLIST 的条目及证据：F02 的单轮流式及完整结果契约完成一部分；真实协议、Agent 最终结果与取消尚未完成；framework/agent 编译依赖未引入 Spring AI
来源实现与新实现的差异：参考 LLMModel 中的工具处理没有搬进模型适配器；新契约显式保留 SYSTEM 和完整工具调用数据
未完成工作／真实阻塞：S03 尚未开始；尚无真实模型适配器和供应商连通验证
本阶段用户已理解的内容：已能说明 ModelGateway、ModelRequest、流式事件、角色和结束状态的关系，也能指出当前没有 Agent harness、工具循环和会话记忆；工具描述与执行、增量与完整历史的细节由后续阶段继续巩固
待用户回答的问题：S02 无阻塞问题；S00/S01 仍有待复述内容
下一会话最先读取的文件：本 PROGRESS.md、notes/S02.md、ARCHITECTURE.md、CHECKLIST.md、STEPS.md 的 S03
下一步具体动作：核对 S02 回答后实施 S03，保持一次只完成一个阶段
```

## 8. S03 阶段交接

```text
阶段：S03 配置化提示词和默认覆盖
目标与已跑通流程：Spring 启动绑定配置 → 加载默认/应用/项目模板 → 校验、计算 hash → 渲染变量 → 构造 SYSTEM 消息
已改文件（路径/核心方法）：ai-agent 的 prompt 包、AgentPromptConfiguration/Properties、四份 prompts/agent 资源；AgentModuleConfiguration 导入新配置；详见 notes/S03.md
新增依赖或配置：ai-agent 增加 spring-boot、Lombok 与测试依赖；项目配置前缀 my-world.agent.prompts；原 my-world.ai.default-system-prompt 不变
验证命令、结果和环境：JDK 21 下 mvn -q clean test 成功；S03 9/9，原有 14/14，共 23/23；无真实模型调用
对照 CHECKLIST 的条目及证据：A15 模板优先级、重载后 hash 和快照基础部分完成；请求级使用待 S04
来源实现与新实现的差异：参考 AgentClientSession 与三种 Skill 将文字内嵌 Java；新实现用资源文件，并生成 Role.SYSTEM；未复制源码
未完成工作／真实阻塞：S04 Agent SDK 与运行时接入未开始；热更新属于后续扩展；无 S04 实施阻塞
本阶段用户已理解的内容：待回答；不能因测试通过标记掌握
待用户回答的问题：见 notes/S03.md 的三个理解问题
下一会话最先读取的文件：本 PROGRESS.md、notes/S03.md、ARCHITECTURE.md、CHECKLIST.md、STEPS.md 的 S04
下一步具体动作：先核对 S03 理解问题，再实施 S04 无工具 Agent 对话
```

## 9. S04 阶段交接

```text
阶段：S04 公共 Agent API 与无工具对话
目标与已跑通流程：prepare → 订阅事件 → execute → SYSTEM/USER 单次模型请求 → 文本增量事件 → 完整助手结果与进程内历史
已改文件（路径/核心方法）：ai-agent 的 api 包、runtime 包；AgentService.prepare/run、DefaultAgentRun.execute/complete/fail；详见 notes/S04.md
新增依赖或配置：framework 与 ai-agent 移除 reactor-core；运行时由消费方提供 ModelGateway、PromptRepository 与 AgentDefinition 集合，可选传入 JDK Executor，无新外部凭据配置
验证命令、结果和环境：JDK 21 下 mvn -q clean test 成功；S04 6/6，原有 23/23，共 29/29；本地脚本模型，无真实供应商调用
对照 CHECKLIST 的条目及证据：A01 本地无工具流程已验证；F02、F09、A11、A12、A15 相应基础部分完成，真实协议、工具、取消和持久化仍待后续阶段
来源实现与新实现的差异：参考 AgentClientSession.command/executeCommand 与 AgentSessionResult；新 API 显式区分准备、观察、启动及同一 Future，未复制源码
未完成工作／真实阻塞：S05 工具系统未开始；真实供应商适配、取消、持久化分属后续阶段；无已确认 S05 实施阻塞
本阶段用户已理解的内容：待回答；工程测试通过不等于学习掌握
待用户回答的问题：见 notes/S04.md 的三个理解问题；S03 变量渲染边界仍待复述
下一会话最先读取的文件：本 PROGRESS.md、notes/S04.md、ARCHITECTURE.md、CHECKLIST.md、STEPS.md 的 S05
下一步具体动作：核对 S04 回答后实施 S05 工具定义、注册、参数解析和执行
```

## 10. S05 阶段交接

```text
阶段：S05 工具定义、注册、参数和执行
目标与已跑通流程：Java Tool → ToolDescriptor/Schema → ToolRegistry 授权快照 → ToolCall JSON 严格解析 → Java execute → 同 callId 的 ToolResult 与阶段事件
已改文件（路径/核心方法）：ai-agent 的 tool/、tool/annotation/；framework 的简单事件和文本内容实现包收平；ToolExecutorTest；详见 notes/S05.md
新增依赖或配置：ai-agent 显式声明已有版本的 jackson-databind；不引入真实模型凭据
验证命令、结果和环境：JDK 21 下 mvn -q clean test 成功；S05 7/7，全项目 36/36；本地 Java 工具，没有远程模型调用
对照 CHECKLIST 的条目及证据：F04 的注解与参数 Schema、F05 的工具阶段与结构化错误已有本地证据；A03 校验失败不执行已验证，模型纠正待 S06
来源实现与新实现的差异：参考从 execute 方法反射找参数类且有宽松解析；目标显式 parameterType、未知字段拒绝、可信上下文不进入模型 JSON；未复制源码
未完成工作／真实阻塞：S06 尚未将工具描述与 ToolExecutor 接入 Agent 循环；模型协议适配和取消分属 S07/S08；无已确认 S06 实施阻塞
本阶段用户已理解的内容：待回答；工程测试通过不等于学习掌握
待用户回答的问题：见 notes/S05.md 的三个理解问题；S03/S04 复述仍待用户反馈
下一会话最先读取的文件：本 PROGRESS.md、notes/S05.md、ARCHITECTURE.md、CHECKLIST.md、STEPS.md 的 S06
下一步具体动作：实现一次工具调用后继续模型的唯一 Agent 循环，并验证 assistant/tool 的 callId 配对历史
```

## 11. S06 阶段交接

```text
阶段：S06 唯一 Agent 工具循环
目标与已跑通流程：授权工具/技能 → ModelRequest 携带工具描述 → ASSISTANT 工具调用 → TOOL 同 callId 结果 → 同一 ModelGateway 续问 → 完整消息序列提交会话
已改文件（路径/核心方法）：framework 的 ModelToolDefinition/ModelRequest；agent 的 AgentDefinition、AgentSkill、DefaultAgentService.prepare、DefaultAgentRun.completeRound/RoundListener、AgentEvent、InMemoryAgentSession；详见 notes/S06.md
新增依赖或配置：无；默认最大模型回合数 8，可由可信 AgentDefinition 设置；仍用本地假模型和 JDK Executor
验证命令、结果和环境：JDK 21 下 mvn -q clean test 成功，全项目 40/40；S06 新增 4 个测试，调整既有未授权工具测试；无真实供应商调用
对照 CHECKLIST 的条目及证据：F01/F03/F05/F06/F07/F08/F09 的 S06 部分、A02/A03；本地两工具 callId、参数纠错、未知工具、重复标识与轮次上限均有断言
来源实现与新实现的差异：参考 OpenAIChatModel 中的递归续问改为 agent/runtime 唯一循环；模型契约只携带工具描述，工具执行不进入协议适配器；未复制源码
未完成工作／真实阻塞：S07 OpenAI Chat 单次协议适配尚未开始；取消、持久化、外部副作用恢复属于后续阶段；无已确认 S07 实施阻塞
本阶段用户已理解的内容：待回答；工程通过不代表学习掌握
待用户回答的问题：见 notes/S06.md 的三个理解问题；S05 和更早阶段仍有待复述内容
下一会话最先读取的文件：本 PROGRESS.md、notes/S06.md、ARCHITECTURE.md、CHECKLIST.md、STEPS.md 的 S07
下一步具体动作：核对 S06 消息轨迹复述后实现 S07 本地 HTTP/SSE fixture 与单次协议适配
```

## 12. S07 阶段交接

```text
阶段：S07 接入 OpenAI Chat 协议
目标与已跑通流程：ModelRequest → modelId 注册选择 → HTTP/SSE 分片聚合 → Agent 收到完整 TOOL_CALLS → 本地工具执行 → 再次 HTTP 请求 → 最终文本与用量
已改文件（路径/核心方法）：framework 的 protocol/openai、ModelOptions、ModelTokenUsage、ReasoningDelta/UsageReported、ModelRequest/ModelTurn；agent 的 AgentEvent/AgentResult/DefaultAgentRun；ai-app 的 OpenAiChatConfiguration/Properties；测试与装配 smoke；详见 notes/S07.md
新增依赖或配置：framework 显式声明 jackson-databind；ai-app 显式声明 spring-boot-autoconfigure；my-world.ai.chat.enabled 与 models.<modelId> 属性可装配网关，默认关闭
验证命令、结果和环境：显式 JDK 21 下 mvn -q clean test 成功，45/45；本地 127.0.0.1 HTTP fixture；未调用真实供应商或使用凭据
对照 CHECKLIST 的条目及证据：F02/F03/F04/F14/F15/F16 的 S07 范围、OpenAI Chat 与多模型配置的本地部分、A01/A02/A03/A12 的相应协议证据；取消及调试日志等留后续阶段
来源实现与新实现的差异：协议层仅实现单次请求，工具回传与续问保持在 DefaultAgentRun；保留现有 SpringAiChatAdapter 的普通对话路径，未复制参考源码
未完成工作／真实阻塞：S08 取消/超时/预算、真实供应商冒烟及其特定字段、S09 会话持久化尚未验证；无已确认 S08 实施阻塞
本阶段用户已理解的内容：待回答；工程测试通过不等于学习掌握
待用户回答的问题：见 notes/S07.md；S06 和更早阶段的待复述内容仍保留
下一会话最先读取的文件：本 PROGRESS.md、notes/S07.md、ARCHITECTURE.md、CHECKLIST.md、STEPS.md 的 S08
下一步具体动作：核对 S07 理解问题后实施 S08 的流式终态、取消、超时与预算
```

## 12A. S08 阶段交接

```text
阶段：S08 流式终态、取消、超时与预算
目标与已跑通流程：execute → 全局时限及运行预算 → 单次模型流 → 顺序工具 → 唯一终态 → 取消传播和会话释放
已改文件（路径/核心方法）：framework 的 CancellationToken、ModelExecutionContext、OpenAiChatGateway.generate/readEvents；agent 的 AgentLimits、DefaultAgentRun.execute/cancel/completeRound、AgentEventPublisher/Subscription、ToolExecutionContext.checkActive；详见 notes/S08.md
新增依赖或配置：无新 Maven 依赖；AgentDefinition 可接收 AgentLimits，旧构造器保留默认预算
验证命令、结果和环境：JDK 21 下 mvn -q -o clean test 成功，全项目 55/55；仅本地假模型与 127.0.0.1 HTTP fixture
对照 CHECKLIST 的条目及证据：F02/F05 的取消与流式终态、OpenAI Chat 取消、A11 的模型断流/取消/超时部分；命令超时待 S12
来源实现与新实现的差异：参考 LLMResult.fail 用 Future 的唯一完成竞争；本项目由 Agent 统一判定终态，协议适配器只做一轮，不递归执行工具
未完成工作／真实阻塞：S09 会话导出恢复尚未开始；命令进程取消与副作用恢复分别待 S12/S19；真实供应商尚未冒烟，无已确认 S09 实施阻塞
本阶段用户已理解的内容：待回答；工程测试不等于学习掌握
待用户回答的问题：见 notes/S08.md；S07 和更早阶段待复述内容仍保留
下一会话最先读取的文件：本 PROGRESS.md、notes/S08.md、ARCHITECTURE.md、CHECKLIST.md、STEPS.md 的 S09
下一步具体动作：核对 S08 终态与取消问题后实施 S09
```

## 12B. S09 阶段交接

```text
阶段：S09 会话历史、隔离与导出恢复
目标与已跑通流程：首轮完成 → 版本化完整历史 → 导出 JSON → 新服务绑定当前 AgentDefinition 并导入 → 第二轮追问
已改文件（路径/核心方法）：agent/session 的 SessionRepository、InMemorySessionRepository、SessionExportCodec、AgentSessionService；AgentRequest.ownerId/modelOptions；DefaultAgentService 会话入口；DefaultAgentRun 历史快照、版本提交和会话选项
新增依赖或配置：无新增 Maven 依赖；显式 ownerId 构造器供多用户应用使用，旧构造器继续以 appId 作为 ownerId
验证命令、结果和环境：JDK 21 下 mvn -q -o clean test 成功，全项目 58/58；本地假模型和本地 HTTP fixture，未调用真实供应商
对照 CHECKLIST 的条目及证据：F09/F11/F14、A12；AgentSessionServiceTest 3 个场景，S06 工具历史回归继续通过
来源实现与新实现的差异：只序列化会话数据，不序列化模型客户端、线程、工具实例或凭据；导入重新使用当前可信定义与工具注册表
未完成工作／真实阻塞：S18 数据库存储、S15 摘要及 S19 未知副作用恢复仍待后续阶段；真实供应商尚未冒烟，无 S10 实施阻塞
本阶段用户已理解的内容：待回答；工程测试不等于学习掌握
待用户回答的问题：见 notes/S09.md；S08 和更早阶段待复述内容仍保留
下一会话最先读取的文件：本 PROGRESS.md、notes/S09.md、ARCHITECTURE.md、CHECKLIST.md、STEPS.md 的 S10
下一步具体动作：实施 S10 代码生成应用与只读文件工具
```

## 12C. S10–S11 阶段交接

```text
阶段：S10 代码生成应用和只读文件工具；S11 文件创建、编辑、移动和删除
目标与已跑通流程：CodegenService → 只读/可写 AgentDefinition → 假模型调用文件工具 → 同 callId 的工具结果回模型；写操作记录 FileArtifact
已改文件（路径/核心方法）：ai-app/codegen 的 api/application/config/tool；ai-app 资源模板；agent 的 PromptTemplateRegistry 应用模板登记；详见 notes/S10-S11.md
新增依赖或配置：ai-app 测试依赖 spring-boot-starter-test；my-world.codegen.enabled/model-id/workspace/write-enabled，默认不启用，写工具默认关闭
验证命令、结果和环境：JDK 21 下指定 FileToolsTest、CodegenServiceTest 共 8/8 通过；排除 3 个本地 HTTP fixture 类后的模块回归通过；完整测试在此沙箱因 SocketException: Operation not permitted 未完成
对照 CHECKLIST 的条目及证据：五个只读与四个写工具、F07 文件技能；S12 execute_command 尚未实现
来源实现与新实现的差异：对照 Agent4J 对应九个 Tool/Param 和 FileSystemSkill；目标增加相对路径策略、拒绝覆盖、SHA-256 版本校验、输出上限和产物记录；兼容常见参考参数名，未复制参考源码
未完成工作／真实阻塞：S12 命令工具及编译闭环；外部并发/崩溃下的副作用恢复归 S19；HTTP fixture 在沙箱内不能绑定端口
本阶段用户已理解的内容：待回答；工程测试通过不等于学习掌握
待用户回答的问题：见 notes/S10-S11.md；此前阶段问题仍保留
下一会话最先读取的文件：本 PROGRESS.md、notes/S10-S11.md、ARCHITECTURE.md、CHECKLIST.md、STEPS.md 的 S12
下一步具体动作：实现受控命令执行，完成生成、编译、修正和验证闭环
```

## 12D. S12 阶段交接

```text
阶段：S12 命令执行及第一条生成闭环
目标与已跑通流程：CodegenService → 假模型 create_file → execute_command(javac 失败) → view_file(hash) → edit_file → execute_command(javac/java 成功) → 最终回答；FileArtifact 与 CommandReport 按 runId 查询
已改文件（路径/核心方法）：ai-app/codegen/tool/ExecuteCommandTool、api/CommandReport、api/CodegenService、application/CodegenFactory、config/CodegenProperties/CodegenConfiguration；命令模板登记与 S12 两个测试类，见 notes/S12.md
新增依赖或配置：无新 Maven 依赖；my-world.codegen.command-enabled 默认 false，command-environment-allowlist 默认 PATH/JAVA_HOME/LANG/TMPDIR
验证命令、结果和环境：JDK 21 下完整 `mvn test` 74/74 通过、无跳过；可枚举进程的本机环境中命令测试 6/6，含子进程取消/超时回收
对照 CHECKLIST 的条目及证据：execute_command、A04、A11 的命令超时、父取消及子进程清理
来源实现与新实现的差异：参考实现先 readLine 至 EOF 再 waitFor(60s)，此实现启动时计时、独立有界读取、显式 cwd/环境/授权开关并返回结构化报告；未复制参考源码
未完成工作／真实阻塞：命令执行的 OS 隔离不是 cwd 所能提供；普通沙箱禁止枚举后代进程时只能保证直接进程清理；不确定副作用恢复归 S19
本阶段用户已理解的内容：待回答；工程测试不等于学习掌握
待用户回答的问题：见 notes/S12.md；此前阶段待回答内容仍保留
下一会话最先读取的文件：本 PROGRESS.md、notes/S12.md、ARCHITECTURE.md、CHECKLIST.md、STEPS.md 的 S13
下一步具体动作：按 S13 实现计划创建与顺序执行
```

## 12E. S13 阶段交接

```text
阶段：S13 计划创建与顺序执行
目标与已跑通流程：模型 create_plan → PlanRunner 顺序推进 → 同一 Agent runtime 执行每步模型和已授权工具 → 前序结果与产物进入下一步 USER 上下文 → 计划真实状态和事件回父级 → 主模型总结
已改文件（路径/核心方法）：ai-agent/orchestration 的 Plan、PlanStep、PlanRunner、CreatePlanTool、PlanEvent；runtime/DefaultAgentRun 的 startPlan/advancePlan/completePlanStep/finishPlan；api/AgentEvent、AgentLimits；ai-app/codegen 的计划开关和命令步骤结果判断；详见 notes/S13.md
新增依赖或配置：无新 Maven 依赖；my-world.codegen.plan-enabled 默认 false；AgentLimits 新增 maxPlanSteps，旧构造器默认 6 步
验证命令、结果和环境：JDK 21 下完整 `mvn -q test` 81/81 通过、无跳过；普通沙箱本地 HTTP fixture 绑定端口被拒，已在允许 localhost 的本机环境完成全量回归
对照 CHECKLIST 的条目及证据：F12、create_plan、A06；PlanExecutionTest、CodegenServiceTest.executesEnabledPlanWithSharedFileTools、CodegenCommandLoopTest.planCarriesCompileFailureIntoCorrectionStep
来源实现与新实现的差异：参考 Plan 是可变字符串列表，CreatePlanTool 用 lastCreatedPlan 共享可变中转，executePlanTool 失败后仍汇总 completed；新实现用不可变定义、每次运行独立状态、程序控制失败策略与最终状态，步骤复用唯一 runtime 和共享预算；未复制参考源码
未完成工作／真实阻塞：计划步骤内部完整模型对话只在运行中用于下一步，不作为独立会话持久化；父会话保存 create_plan 工具结果中的结构化步骤报告。计划事件仍为进程内有限回放，S18/S19 再做持久化与检查点。无 S14 实施阻塞
本阶段用户已理解的内容：待回答；工程测试不等于学习掌握
待用户回答的问题：见 notes/S13.md；此前阶段待回答内容仍保留
下一会话最先读取的文件：本 PROGRESS.md、notes/S13.md、ARCHITECTURE.md、CHECKLIST.md、STEPS.md 的 S14
下一步具体动作：按 S14 实现独立子 Agent 与父子上下文
```

## 12F. S14 阶段交接

```text
阶段：S14 子 Agent 与父子上下文
目标与已跑通流程：父模型 create_sub_agent → 可信参数和工具子集校验 → 独立 childSessionId/childRunId 与委派 SYSTEM/USER → 子运行复用唯一 Agent 循环 → 父运行收到配对 tool 结果并总结
已改文件（路径/核心方法）：ai-agent/orchestration 的 CreateSubAgentTool、SubAgentRunner；DefaultAgentRun.startSubAgent/finishSubAgent/mergeChildAccounting；AgentLimits.maxSubAgentDepth；agent/sub-agent.md；ai-app/codegen 的显式 sub-agent-enabled 配置；详见 notes/S14.md
新增依赖或配置：无新依赖；my-world.codegen.sub-agent-enabled 默认 false。子任务 toolIds 必须是父授权工具子集，省略时无工具
验证命令、结果和环境：JDK 21 下完整 mvn -q test 89/89 通过、零失败/错误/跳过；本地 HTTP fixture 需要允许 localhost 绑定的执行环境；脚本模型和临时 workspace，未调用真实供应商
对照 CHECKLIST 的条目及证据：F13、create_sub_agent、A07；F16 父子用量按 modelCallId 去重汇总，S21 指标仍待实现
来源实现与新实现的差异：参考 CreateSubAgentTool 用 lastCreatedAgent/lastTask 跨调用存临时状态并复制全部父工具；新实现每次委派直接传参数，子级只能获父授权子集且不复制父历史
未完成工作／真实阻塞：子会话当前随 S09 仓库保存在内存，数据库持久化在 S18；并行子 Agent 属后续可选优化；无 S15 实施阻塞
本阶段用户已理解的内容：待回答；工程测试不等于学习掌握
待用户回答的问题：见 notes/S14.md；此前阶段待回答内容仍保留
下一会话最先读取的文件：本 PROGRESS.md、notes/S14.md、ARCHITECTURE.md、CHECKLIST.md、STEPS.md 的 S15
下一步具体动作：按 S15 实现上下文窗口与摘要记忆
```

## 12G. S15 阶段交接

```text
阶段：S15 上下文窗口与摘要记忆
目标与已跑通流程：完整历史积累 → 轮次或估算 Token 触发 → 无工具分块摘要 → 原子提交覆盖位置 → 下一请求使用可信 SYSTEM、摘要、未覆盖完整交换与最新用户输入
已改文件（路径/核心方法）：agent/memory 的 ContextPolicy、ContextAssembler、MemorySummary；runtime/DefaultAgentRun.prepareInitialContext/requestSummary/prepareAndInvokeModel；session 的摘要快照与导出恢复；api/AgentEventType.MEMORY_COMPRESSED；codegen 可信阈值配置；详见 notes/S15.md
新增依赖或配置：无新 Maven 依赖；my-world.codegen 可配置上下文窗口、摘要轮次与 Token 阈值、回答预留和摘要长度；my-world.agent.prompts.summary 可覆盖摘要模板
验证命令、结果和环境：JDK 21 下 ContextMemoryTest 6/6、完整 mvn -q test 95/95 通过；普通沙箱禁止本地 HTTP fixture 绑定，已在允许 localhost 的本机环境执行
对照 CHECKLIST 的条目及证据：F10/F11；ContextMemoryTest 验证触发、摘要回注、事件、用量、失败、完整工具交换、窗口错误和导出恢复
来源实现与新实现的差异：参考客户端直接维护摘要和历史；新实现把摘要与覆盖位置作为会话仓库状态，保留原始消息并在导入时校验完整交换边界；未复制参考源码
未完成工作／真实阻塞：估算不是供应商精确 tokenizer，实际窗口值需按模型配置并验证；真实供应商未冒烟；S18 将把摘要状态持久化到数据库仓库；无 S16 实施阻塞
本阶段用户已理解的内容：待回答；工程测试不等于学习掌握
待用户回答的问题：见 notes/S15.md；此前阶段待回答内容仍保留
下一会话最先读取的文件：本 PROGRESS.md、notes/S15.md、ARCHITECTURE.md、CHECKLIST.md、STEPS.md 的 S16
下一步具体动作：按 S16 实现 Responses、Anthropic、reasoning 与附件
```

## 13. 后续阶段交接模板

```text
阶段：
目标与已跑通流程：
已改文件（路径/核心方法）：
新增依赖或配置：
验证命令、结果和环境：
对照 CHECKLIST 的条目及证据：
来源实现与新实现的差异：
未完成工作／真实阻塞：
本阶段用户已理解的内容：
待用户回答的问题：
下一会话最先读取的文件：
下一步具体动作：
```

每阶段另存 `notes/Sxx.md`，包含调用链、事件/消息示例、测试证据和理解问题的回答。不要把完整源码、API Key、个人文件内容复制进学习笔记。

## 14. 收尾检查

- [ ] 所有必做阶段都有真实验证记录。
- [ ] CHECKLIST 的保留功能和 A01–A18 场景均有证据。
- [ ] 已区分假模型、本地协议 fixture 与真实供应商验证。
- [ ] 数据库正常重启和不确定副作用恢复有记录。
- [ ] 新模块仍为普通 JAR，独立消费者 smoke 已通过。
- [ ] 原有 myWorld 功能回归通过或准确说明遗留基线问题。
- [ ] 后续待办未冒充已实现，学习状态未自动标为掌握。
