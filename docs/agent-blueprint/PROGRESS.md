# 工程进度与学习交接

最后更新：2026/10/02（S03 已完成工程验证，等待学习复述）。

## 1. 当前定位

- 目标项目：`/Users/sebastian/myPorject/myWorld`。
- 当前图纸：目标项目 `docs/agent-blueprint/` 工作副本；参考项目 `doc/myworld-agent-blueprint/` 保留为原始图纸。
- 下一阶段：S04；S03 已完成优先级和授权边界复述，变量渲染边界待复述，S02 主线已理解，S00/S01 仍有待复述内容。
- 当前阶段已实现内容：S03 公共 Agent 模板资源、项目 YAML 与应用资源覆盖、模板快照及 SYSTEM 消息构造；尚未接入 Agent SDK 或工具循环。
- 当前阻塞：无 S01 实施阻塞。默认 JDK 8 无法构建 Java 21 项目；后续 Maven 命令需显式使用本机 JDK 21。
- 基线只读观察：2026/10/01 开始时工作区干净，分支 feat-20260925-projInit-Sebastian。
- 本次交付与证据：见 [notes/S03.md](notes/S03.md)。JDK 21 下全项目 23 个测试通过，其中 S03 新增 9 个；未调用真实模型。

## 2. 阶段状态

工程状态使用“未开始 / 进行中 / 已验证 / 阻塞”；学习状态使用“未开始 / 已讲解 / 待回答 / 已掌握”。只有用户反馈才能标记已掌握。

| 阶段 | 主题 | 工程 | 学习 | 验证/笔记链接 |
|---|---|---|---|---|
| S00 | 基线与图纸接入 | 已验证 | 待回答 | [notes/S00.md](notes/S00.md) |
| S01 | 模块与装配 | 已验证 | 待回答 | [notes/S01.md](notes/S01.md) |
| S02 | 模型契约与假模型 | 已验证 | 已掌握 | [notes/S02.md](notes/S02.md) |
| S03 | 提示词配置 | 已验证 | 待回答 | [notes/S03.md](notes/S03.md) |
| S04 | Agent SDK 普通对话 | 未开始 | 未开始 | — |
| S05 | 工具系统 | 未开始 | 未开始 | — |
| S06 | Agent 循环 | 未开始 | 未开始 | — |
| S07 | Chat 协议 | 未开始 | 未开始 | — |
| S08 | 流式、取消、预算 | 未开始 | 未开始 | — |
| S09 | 会话与序列化 | 未开始 | 未开始 | — |
| S10 | Codegen 与读取工具 | 未开始 | 未开始 | — |
| S11 | 文件变更工具 | 未开始 | 未开始 | — |
| S12 | 命令与生成闭环 | 未开始 | 未开始 | — |
| S13 | 计划 | 未开始 | 未开始 | — |
| S14 | 子 Agent | 未开始 | 未开始 | — |
| S15 | 摘要与上下文 | 未开始 | 未开始 | — |
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

图纸默认：单次模型适配器 + 唯一 Agent 循环；沿用现有普通对话；Reactor 事件；本地 JDBC/H2；顺序编排；按阶段教学。它们来自工程规划，修改时记录证据即可，不把它们误称为用户逐项作出的选择。

| 日期 | 决策/变更 | 原因与证据 | 影响阶段 | 状态 |
|---|---|---|---|---|
| 2026/10/01 | 建立当前四模块工程图纸 | 本会话确认 Spring AI 接入与自研框架需要独立 Maven 依赖边界 | 全部 | 已调整 |
| 2026/10/01 | S00 保持源码与 POM 原状，仅接入文档；测试命令显式选择已安装的 JDK 21 | 默认 Java 8 使 `mvn test` 编译失败；JDK 21 下原有 9 个测试通过 | S00–S01 | 已验证 |
| 2026/10/01 | S01 新模块产普通 JAR，启动模块保留唯一可执行包；配置通过 `@Import` 串联 | 根 POM、离线依赖树、Jar 内容与上下文 smoke 均验证依赖方向 | S01–S02 | 已验证 |
| 2026/10/01 | 装配 smoke 由 JUnit 显式启动 Spring Boot 上下文，使用无 Mockito 的假模型与测试用占位 Key | 本机 Mockito inline 自行附加失败；最终 smoke 1/1 通过且未调用模型 | S01 测试 | 已验证 |
| 2026/10/01 | S02 数据契约使用 Java 8 风格普通不可变类；空白字符串与空集合判断复用 common 工具包；Javadoc 统一为多行 | 用户明确提出风格要求；JDK 21 下契约测试和现有回归共 14/14 通过 | S02 及后续 Java 代码 | 已验证 |
| 2026/10/01 | S02 值对象保留 `final`，统一使用 Lombok `@Data`；接口和实现拆成顶层文件 | 字段全部为 `final`，不会生成 setter；`@Data` 统一生成 getter、值比较和 `toString`；`ContentBlock`/`TextContentBlock`、`ModelEvent`/`TextDelta`/`TurnCompleted` 等不使用内部类；JDK 21 下回归测试通过 | S02 数据对象 | 已验证 |
| 2026/10/01 | 新增 `my-world-ai-framework`，迁移 S02 契约；`my-world-ai` 恢复为 Spring AI 接入模块 | framework 只依赖 common、Reactor 和 Lombok；agent 依赖 framework，不再导入 `SpringAiConfiguration`；clean test 和离线依赖树通过，framework/agent 编译依赖中没有 Spring AI | S01/S02 模块边界 | 已验证 |
| 2026/10/02 | S03 模板按项目 YAML > 应用资源 > 公共默认资源合并，启动时冻结内容与 hash | `PromptTemplateRegistryTest` 和 `AgentPromptConfigurationTest` 9/9，包含文件变更后重建仓库、SYSTEM 角色与无效配置启动失败 | S03/S04/S15 | 已验证 |

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
目标与已跑通流程：ModelRequest → ScriptedModelGateway 冷流 → TextDelta → 唯一 TurnCompleted(ModelTurn)
已改文件（路径/核心方法）：my-world-ai-framework 的 aiframework/model、aiframework/api 契约、ModelEventStreams.requireCompleted、ScriptedModelGateway、ModelGatewayContractTest；my-world-ai 下的 S02 文件已移除；详见 notes/S02.md
新增依赖或配置：无；复用 Reactor、Lombok 和 my-world-common 工具包
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

## 9. 后续阶段交接模板

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

## 10. 收尾检查

- [ ] 所有必做阶段都有真实验证记录。
- [ ] CHECKLIST 的保留功能和 A01–A18 场景均有证据。
- [ ] 已区分假模型、本地协议 fixture 与真实供应商验证。
- [ ] 数据库正常重启和不确定副作用恢复有记录。
- [ ] 新模块仍为普通 JAR，独立消费者 smoke 已通过。
- [ ] 原有 myWorld 功能回归通过或准确说明遗留基线问题。
- [ ] 后续待办未冒充已实现，学习状态未自动标为掌握。
