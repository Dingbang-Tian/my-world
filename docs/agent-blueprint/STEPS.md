# 分阶段实施与学习验收

每个阶段都遵循 README 的教学节奏。这里的“完成”指实现与验证完成，理解程度另记 PROGRESS。阶段较大时可拆为多次小节，但不跳过它的验收后直接宣称通过。

参考文件均相对 Agent4J 的 `src/main/java/ink/icoding/llm/`；目标包见 ARCHITECTURE。每阶段开始时读取具体方法，不把参考代码整包搬过去。

## 里程碑与依赖

| 里程碑 | 阶段 | 可展示的结果 |
|---|---|---|
| M1：认识 Agent 核心 | S00–S06 | 不调用真实模型也能完整展示一次工具调用循环 |
| M2：连接真实模型 | S07–S09 | 协议适配、流式、取消与会话历史可验证 |
| M3：代码生成应用 | S10–S12 | 在指定目录读代码、改文件、运行受控验证命令 |
| M4：复杂任务与记忆 | S13–S15 | 计划、子 Agent、摘要压缩形成闭环 |
| M5：原功能全覆盖 | S16–S17 | 三种聊天协议及两类 Embedding 协议均有契约验证 |
| M6：可持久运行 | S18–S21 | 数据库记忆、检查点恢复、SDK 接入、运行治理 |
| M7：项目与学习交付 | S22 | 功能对照、消费者示例、教程和验证报告齐全 |

默认按顺序。S03 提示词可与 S02 后的讨论衔接；S16/S17 的适配可以独立研发，但学习者建议仍顺序完成。不要在未获得用户授权时并行启动代理。

## S00：基线盘点与图纸接入

**学习目标：**知道运行入口、模块依赖、普通对话与 Agent 的差别。

**实现任务：**读取目标项目规则和 Git 状态；把本图纸复制到目标 `docs/agent-blueprint/`；记录 JDK/Maven、模块、测试和现有改动。保留已有配置解密测试。只做文档和必要的环境验证，不重构业务代码。

**验证：**运行 `java -version`、`mvn -version`、目标现有 `mvn test`。依赖不可用、网络限制或基线测试失败时记录准确阻塞和失败位置，不能把后续阶段标为完成。遵守执行环境的联网权限要求。

**产出：**更新 PROGRESS 的基线；建立 `docs/agent-blueprint/notes/S00.md`，记下一张实际依赖图和验证结果。参考来源许可证事实与作者信息记录在来源说明中。

**理解问题：**my-world-app 与 my-world-ai-app 有什么区别？现有 conversationId 字段是否代表会话已实现？

**退出条件：**基线有可复现记录；未解决阻塞明确；下一阶段是 S01。

## S01：建立 AI 模块和可启动装配

**学习目标：**理解依赖方向如何决定扩展边界。

**实现任务：**新增 my-world-ai-framework、my-world-ai-agent 和 my-world-ai-app 的 POM，沿用父版本；更新聚合 modules；ai-app 依赖 agent，agent 依赖 framework；启动模块分别按用途引入 ai 与 ai-app。Spring AI 配置留在 ai 模块，Agent 配置不得直接导入 SpringAiConfiguration。只创建本阶段必要配置与包说明，不生成未来全部接口。

**流程：**启动模块 → AI 应用装配 → Agent 配置 → 模型配置。没有 API Key 的测试使用 test profile 和假模型，不发真实请求。

**验证：**执行新增模块及依赖的编译和应用上下文 smoke；确认只有 my-world-app 被重打包成可执行包；依赖树没有循环，原普通对话配置仍可装配。无需为 POM 文本编写机械单元测试。

**阅读：**参考 pom.xml、agent/AgentClient；目标父 POM 与 SpringAiConfiguration。

**理解问题：**为什么 agent 不能导入 codegen？为什么可复用模块应产出普通 JAR？

## S02：单次模型消息与假模型

**学习目标：**区分“模型生成下一步”与“程序执行下一步”。

**实现任务：**在 ai-framework 的 api 和 model 包增加 ModelRequest、Message/Role、ToolCall/Result、ModelEvent、ModelTurn、ModelGateway 的最小契约；声明 system 角色，区分增量事件和完整结果。建立测试用 ScriptedModelGateway，可按请求返回文本或工具调用。该模块不得引入 Spring AI。

**流程：**测试构造 user 消息 → 假模型返回若干 text delta → 唯一 TurnCompleted → 获取完整 assistant。

**验证：**内容不重复拼接；一次订阅只执行一次脚本；工具调用能保持 callId 和完整参数；脚本异常终止流；缺少终态能被检测。生产代码不硬编码测试答案。

**阅读：**core/model/LLMModel、LLMResult；core/entity/Message、MessageToolCall、MessageToolResult。

**理解问题：**工具描述为何不等于工具执行对象？完整结果为什么不能与 delta 同时追加历史？

## S03：配置化提示词和默认覆盖

**学习目标：**理解 SYSTEM、用户输入、运行上下文与工具说明各自来源。

**实现任务：**agent/prompt 增加模板仓库、加载、渲染与快照；默认通用模板放资源文件。YAML 支持 inline 或 location 二选一；优先级按架构约定。保留旧普通对话 default-system-prompt，明确它与新 Agent 模板配置的适用范围。

**流程：**启动读取模板 → 校验变量 → 合并应用覆盖 → 计算 hash → 根据变量构造 SYSTEM 消息。

**验证：**默认值、项目覆盖、应用覆盖、缺变量、非法 location、空模板、未知模板 key；用户内容不成为模板表达式；同一次运行模板不会中途切换。文件加载失败在启动/注册时暴露。

**阅读：**AgentClientSession.buildMessages/buildPlanSystemPrompt/buildSummarySystemPrompt，以及三个内置 Skill 的内容。

**理解问题：**提示词为什么不能承担工具权限校验？模板重启生效如何测试？

## S04：公共 Agent API，跑通无工具对话

**学习目标：**看清调用、异步事件与最终结果的关系。

**实现任务：**定义 AgentDefinition、AgentService、AgentRequest、AgentRun、AgentResult；实现 prepare/execute/events/result 和阻塞便利入口。用最简单的进程内会话记录串起模板、模型与最终回答，存储抽象在 S09 完善。

**流程：**调用者 prepare → 注册事件 → execute → 组装 system/user → 调用假模型 → 完成结果。

**验证：**prepare 不联网；execute 第二次调用被拒绝；多个观察者不会多发请求；正常/异常均能完成 Future；正确传播 run/session ID。

**阅读：**AgentClient.createSession；AgentClientSession.command/executeCommand；AgentSessionResult。

**理解问题：**哪个操作才真正启动？同步 run 与流式事件是不是两次模型调用？

## S05：工具定义、注册、参数和执行

**学习目标：**理解 Java 工具如何变成模型可见的 Schema。

**实现任务：**实现 Tool<P>、ToolDescriptor、参数 Schema、注解提取、ToolRegistry、ToolExecutor 和结构化结果。用测试里的加法工具演示；执行上下文与模型参数分离。覆盖参考项目所需参数类型，定义同名工具冲突规则。

**流程：**Java 工具 → descriptor/Schema → 模型参数 JSON → 反序列化及校验 → Java execute → 成功/错误结果。

**验证：**必填缺失、类型错误、同名冲突、未知工具、工具抛异常、枚举/列表、回调异常不掩盖主结果。成功事件顺序包含 PREPARING/CALLING/COMPLETED；失败有对应错误事件。

**阅读：**core/tool/Tool、ToolParam、ToolDescriptor、ToolExecutor、annotations；ToolExecutorTest。

**理解问题：**Java 泛型信息怎样被取得？参数错误为什么不能直接当作模型已经执行了工具？

## S06：实现唯一 Agent 循环

**学习目标：**独立解释一次 tool calling 的完整消息序列。

**实现任务：**agent/runtime 处理 ModelTurn；有工具时追加 assistant 调用，调用 ToolExecutor，追加对应 tool 结果，再请求模型；无工具时完成。增加最小轮次限制。Skill 先支持工具分组与说明引用，不引入编排逻辑。

**流程：**“计算 2+3” → 假模型请求 add(c1) → 工具返回 5 → 假模型回答 5。

**验证：**精确断言第二次请求包含 assistant(c1) 与 tool(c1)；多个工具结果正确关联；工具失败可交给模型修正；未知工具有可识别结果；轮次上限停止；整个 run 只完成一次。

**阅读：**OpenAIChatModel.executeAgentLoop/handleToolCallsAndContinue；AgentClientSession.createToolExecutor。理解后把循环迁到 agent/runtime，而非留在 provider 内。

**理解问题：**程序何时知道应继续？为什么工具结果返回后需要再问模型？

**里程碑 M1：**打印本次真实测试产生的消息轨迹，并由用户复述每一步。

## S07：接入 OpenAI Chat 协议

**学习目标：**把假模型换成 HTTP/SSE，观察 Agent 循环保持不变。

**实现任务：**在 ai-framework 中实现或适配协议中立的单次 Chat 请求契约，解析普通内容、reasoning、工具片段和 usage；引入 modelId 注册和调用级选项。Spring AI 的具体适配保留在 ai 模块，并通过单向适配实现 framework 契约；新路径不得自动调用内部工具执行器。

**流程：**Agent → ModelGateway → 本地 SSE fixture → tool call → Agent 执行 → 第二次 fixture → 回答。凭据可用时另做一个只读 smoke。

**验证：**分片 arguments、多个 call index、跨片工具名、空 delta、usage 尾块、HTTP 401/429/500、断流、未知 finish reason；参数覆盖不修改共享模型；最终调用次数与工具次数准确。

**阅读：**OpenAIChatModel.ask/buildRequestBody/executeAgentLoop；MiMoReasoningHistoryTest 和 StreamCompletionTest。具体 SDK 选项如有疑问，先查本地依赖源码，必要时查该版本官方文档。

**理解问题：**模型供应商、协议和 modelId 有何区别？自动工具执行为何会与外层 Agent 循环冲突？

## S08：流式终态、取消、超时与预算

**学习目标：**理解业务完成、网络关闭和线程结束是不同事件。

**实现任务：**完善运行状态机、全局 deadline、max rounds/tools、输出上限、有界事件缓存；实现取消向 provider 订阅传播。定义配置/用户/协议错误与普通工具错误策略。阻塞任务使用专门执行器。

**验证：**完成后迟到错误不变失败；取消后不再启动下一个工具；超时无 Future 悬挂；重复 cancel 幂等；消费者异常不破坏工具执行；缓存溢出有清晰错误；usage 不重复累计。关闭进程相关验证留 S12。

**阅读：**LLMResult.fail、三个模型的完成分支、StreamCompletionTest。

**理解问题：**为什么收到 HTTP 200 也可能执行失败？为什么取消不能只给前端返回一个状态？

## S09：会话历史、隔离与导出恢复

**学习目标：**区分 Agent 定义、一次会话和一次运行。

**实现任务：**引入 SessionRepository、MessageRepository 或等价窄接口及内存实现；保存全量消息与配对工具结果；按 owner/app 隔离。单会话独占执行、版本校验；session 导出/导入含 schemaVersion，不带客户端与密钥。

**流程：**第一轮读取事实 → 保存历史 → 第二轮追问；导出 → 重新绑定 AgentDefinition → 导入 → 继续对话。

**验证：**两个会话互不泄露；同会话同时执行返回 SESSION_BUSY；工具历史可用；导出后导入与原消息语义一致；导入不扩大工具权限；取消后的不完整交换不会原样污染下一请求。

**阅读：**AgentClientSession.serialization/fromSerialization/executeCommand；AgentClientSessionHistoryTest。

**理解问题：**同一个模型实例共享是否等于历史共享？为什么不能序列化一个运行中的线程来恢复 Agent？

**里程碑 M2：**用本地协议服务演示多轮对话和取消，注明是否做过真实供应商验证。

## S10：代码生成应用与只读文件工具

**学习目标：**理解应用如何组装公共 Agent，而不修改核心循环。

**实现任务：**在 ai-app/codegen 实现 CodegenService 和 AgentDefinition 配置，提供代码生成及文件技能模板；迁移 list_directory_tree、view_file、search_files、search_in_file、search_in_directory。建立统一 WorkspacePolicy，先在读操作中使用。

**流程：**代码生成应用接收“理解这个小项目” → 选择只读工具 → Agent 查询临时目录 → 输出结构说明。

**验证：**目录深度、文件范围/行号、大文件截断、多个关键词及上下文、空目录、编码、符号链接越界；所有工具只注册在指定应用。通过假模型验证完整应用接入。

**阅读：**对应五个 builtin Tool/Param、FileSystemSkill、MainTest.agentTest 的装配部分。

**理解问题：**哪些代码属于 codegen，哪些属于中台？默认关闭一个工具时为什么还要检查执行入口？

## S11：文件创建、编辑、移动和删除

**学习目标：**理解模型建议与真实文件修改之间的执行边界。

**实现任务：**迁移 create_file、edit_file（replace/insert/append）、move_file、delete_file；所有路径走统一策略。加入修改前版本/hash、产物记录、输出上限，定义覆盖已有文件和删除目录的明确规则。

**流程：**读文件 → 返回当前 hash → 模型请求修改 → 校验 hash/路径 → 执行 → 返回变更摘要。

**验证：**行边界、空文件、换行符、父目录创建、目标冲突、移动两端越界、符号链接父目录、读后并发变更、重复删除；拒绝操作时文件未改变。使用 JUnit 临时目录，不碰用户真实项目。

**阅读：**四个写工具及 param；记录对参考行为的修正，如拒绝无意覆盖，而非悄悄丢弃功能。

**理解问题：**规范化路径后为什么还要检查真实父目录？为什么模型说“已修改”不能替代执行结果？

## S12：命令执行及第一条生成闭环

**学习目标：**理解 Agent 如何用外部验证结果修正自己的产物。

**实现任务：**迁移 execute_command；异步有界读取输出，启动时计时，支持显式 cwd、shell 选择、退出码、取消与进程树回收。配置环境 allowlist；代码生成应用按配置开启命令能力。

**流程：**生成临时 HelloAgent.java → 使用当前 JDK 编译/运行 → 捕获失败 → 修正 → 验证成功 → 返回文件及执行报告。实际学习例子用确定性脚本模型，真实 LLM 版本另记。

**验证：**成功、非零退出码、空输出、大输出、无换行的长任务、超时、父取消、子进程清理；测试命令为受控 fixture，不执行参考示例的大任务。仅对当前平台执行验证，其余 shell 选择可单元测试。

**阅读：**ExecuteCommandTool、ExecuteCommandParam、CommandExecutionSkill，特别检查读取输出和 waitFor 的先后关系。

**理解问题：**为什么先 readLine 到结束再 waitFor(60s) 不能保证总共 60 秒？cwd 能否限制 shell 访问目录外文件？

**里程碑 M3：**保存生成文件、验证输出、事件轨迹，并解释从业务入口到工具再到模型的整条链路。

## S13：计划创建与顺序执行

**学习目标：**区分模型提供步骤与程序控制步骤状态。

**实现任务：**实现 Plan、PlanStep、PlanRunner 及 create_plan；步骤模型调用复用同一 runtime；明确先前结果传递、失败策略、步骤上限、禁用递归计划；转发计划生命周期事件。

**流程：**计划“生成源文件→编译→修正” → 顺序执行 → 每步结果传入下一步 → 汇总真实状态。

**验证：**第二步能看到第一步产物；STOP/CONTINUE 策略；父取消；步骤失败不会汇总成全成功；计划内部工具与主任务采用同一权限与预算。

**阅读：**Plan、CreatePlanTool/Param、AgentClientSession.executePlanTool/buildPlanSystemPrompt。

**理解问题：**Plan 对象为什么不是执行器？仅提示词写“不要再建计划”能否代替程序限制？

## S14：子 Agent 与父子上下文

**学习目标：**理解独立上下文、继承配置和共享服务的区别。

**实现任务：**实现 create_sub_agent/SubAgentRunner；创建独立会话与 runId；传入受控工具子集、任务和选定上下文；父任务等待；传播取消与剩余预算，限制深度。消除 lastTask 等共享可变通信方式。

**流程：**父 Agent 委派“检查生成代码” → 子 Agent 使用只读工具检查 → 结果回父 tool call → 父总结。

**验证：**子历史不混入父历史，只回传约定结果；工具权限不能扩大；超过深度拒绝；子失败可解释；父取消终止子；父子 usage 按 modelCallId 去重。

**阅读：**CreateSubAgentTool/Param、AgentClientSession.executeSubAgentTool、AgentResultHandler。

**理解问题：**新建 Agent 对象是否自动并行？共享模型服务时哪些数据必须是请求级？

## S15：上下文窗口与摘要记忆

**学习目标：**理解保存了所有历史，不代表每次都应该把全部历史发给模型。

**实现任务：**增加 ContextAssembler、MemorySummary、阈值策略和摘要调用；摘要模板可配置；记录覆盖序号；不删除持久化原始历史。请求前检查窗口，完成后可预摘要；明确 round/token 阈值与估算来源。

**流程：**历史积累 → 超阈值 → 无工具摘要请求 → 原子更新摘要覆盖位置 → 下一请求使用摘要+未覆盖完整交换。

**验证：**轮次/Token 触发、摘要失败、摘要超长、工具交换不可切半、不能丢最新用户输入、已超窗口时明确错误、摘要用量计入预算、摘要导出恢复一致。

**阅读：**AgentClientSession 的 summarizeHistoryIfNeeded、shouldSummarizeHistory、summarizeHistory、buildMessages；相关历史测试。

**理解问题：**摘要为何是有损记忆？如何避免多轮摘要后重复加入被覆盖的原消息？

**里程碑 M4：**用小阈值的假模型演示压缩，再逐项解释实际发送的上下文。

## S16：Responses、Anthropic、reasoning 与附件

**学习目标：**理解同一个 Agent 循环怎样服务不同协议。

**实现任务：**补齐 Responses 和 Anthropic 的单次请求适配；统一 system、tools、tool results、stream completion、usage；保留参考支持的 reasoning 历史、签名和旧历史兼容场景；补齐按能力支持的消息附件。

**流程：**同一模型契约测试与同一 Agent 用例，分别接三套本地协议 fixture，结果语义一致。

**验证：**工具结果配对、交错 delta、推理元数据、各协议结束事件、取消后的迟到失败、真实断连、附件 MIME/大小/不支持类型、请求选项不串会话。MiMo/Qwen 兼容逻辑只放适配器，并以实际字段 fixture 验证。

**阅读：**OpenAIResponseModel、AnthropicModel、Message/MessageAttachment/MemoryMultipartFile、MiMoReasoningHistoryTest、StreamCompletionTest。

**理解问题：**为什么不能只替换 URL 就支持不同协议？能力检查应该在哪一层？

## S17：Embedding 能力保留

**学习目标：**理解向量生成与聊天/工具循环是两条不同接口。

**实现任务：**在 ai 实现 EmbeddingGateway、输入、向量结果和 usage；适配 OpenAI 文本批量 Embedding 与 DashScope 文本/图片/视频及融合输入；尺寸和模型参数配置化。

**验证：**输入构造、批量索引顺序、向量维度、usage、错误、文本接口拒绝不支持媒体、URL/data URI/内存媒体序列化；通过本地 fixture 验证，不把它描述成已具备完整 RAG。

**阅读：**EmbeddingModel/Embedding/EmbeddingResult、EmbeddingInput、两个 Embedding 实现、EmbeddingModelTest。

**理解问题：**有了向量接口后，知识库检索还缺哪些环节？为什么不能把 embedding modelId 传给 Agent 聊天接口？

**里程碑 M5：**更新 CHECKLIST 的模型与工具对照，每一项有实际位置和测试。

## S18：数据库会话和执行记录

**学习目标：**区分对话内容、执行状态和压缩摘要的存储职责。

**实现任务：**实现 MySQL + MyBatis-Plus Service/Mapper 存储与版本化迁移；覆盖 ARCHITECTURE 的 session/message/run/event/tool/summary/plan 表及 codegen 产物记录；数据库事务、唯一约束、乐观锁、短租约落实。memory 与 mysql 可配置替换。

**流程：**发起会话 → 记录工具和最终结果 → 正常关闭 → 新进程/新容器实例打开同一文件库 → 加载会话继续追问。

**验证：**重启恢复历史与摘要、消息序号唯一、owner/app 隔离、并发版本冲突、请求幂等键、数据库错误、不能持久化客户端和 Key；长模型请求不占数据库事务。存储实现通过同一契约测试。

**技术选择：**按用户最新要求使用 MySQL、MyBatis-Plus 和 Flyway；生产运行脚本使用 MySQL 方言，并在真实 MySQL 测试库验证。H2/JDBC 旧实现仅留在测试源码用于历史回归。

**理解问题：**为什么只保存 finalText 不够恢复？为什么持久化了 runId 仍不代表外部工具“只执行一次”？

## S19：检查点和中断恢复

**学习目标：**理解数据库事务无法撤销已经发生的文件或进程副作用。

**实现任务：**记录执行意图和完成结果；启动扫描未结束 run 并标 INTERRUPTED；实现显式 resume 接口，新 run 关联旧 run。支持复用可确认完成的步骤；不确定的副作用进入 NEEDS_REVIEW 类恢复状态，而非静默重跑。

**流程：**计划执行一部分 → 故障 → 加载检查点 → 区分成功/失败/不确定工具 → 从安全边界继续 → 新 run 汇总。

**验证：**执行前故障、执行后落库前故障、结果已保存、取消任务重启不自启、未知 shell 结果不重复执行、旧进程标识不误杀；异常历史交换恢复后仍满足协议约束。

**理解问题：**为什么不能 promise exactly-once？重启后能恢复哪些状态，哪些必须重新判断？

## S20：SDK 消费者与应用入口

**学习目标：**理解框架对外契约如何让第二个调用方无需知道内部实现。

**实现任务：**完善 SDK 示例与普通 JAR 打包；在 ai-app/codegen 对外提供 CodegenService；在启动模块增加开发环境入口：POST 创建任务、GET 状态、GET 事件流、POST 取消，按需要增加恢复入口。控制器只做输入输出映射。

**流程：**请求进入 codegen → 返回 runId/sessionId → 订阅结构化 SSE → 查询终态和产物；SDK 消费者直接调用 AgentService 跑同一任务。

**验证：**用独立临时消费者工程通过 Maven 普通依赖编译/运行，无需再新增永久生产模块；消费者只使用公开包；无模型凭据时假模型仍可验证；重复 requestId 不启动重复任务；非法 workspace/tool/prompt 输入不能提升权限。

**部署边界：**Web 入口默认 dev/local 演示；ownerKey 由可信上下文提供。未接身份系统前不标为可公开暴露的多用户服务。网络断开是否取消任务采用配置，默认观察连接断开不取消后台任务。

**理解问题：**为什么核心接口放 agent 而不是 codegen？SSE 断开是否等于任务应该取消？

## S21：运行治理与排障

**学习目标：**从一条失败任务中定位模型、工具、上下文或持久化哪一层出了问题。

**实现任务：**补齐结构化日志、run/parent/trace 关联、模型耗时、工具耗时、usage、限制和审计；调试日志脱敏与开关；请求配置快照可追踪。通过拦截器扩展审计策略，避免散落在每个工具里。

**验证：**usage 聚合不双算；缺用量显示 unknown 而非 0；API Key 与敏感工具输出不出现在默认日志；多会话请求选项互不污染；只读能力配置确实拒绝写工具；取消资源清理与限额有证据。

**阅读：**LLMRequestDebugLogger/TokenUsage、对应测试；目标已有错误码、异常处理和日志工具。

**理解问题：**Token 限额如何在生成过程中近似控制？已有结果不确定时，为什么重试需要区分模型与工具？

**里程碑 M6：**拿一条失败轨迹，从请求 ID 定位到工具、参数校验和最终错误；演示数据库重启后续聊。

## S22：完整功能验收、回归案例与学习交付

**学习目标：**能从用户需求出发解释整个系统，并独立实现一个扩展点。

**实现任务：**逐项收敛 CHECKLIST；整理启动配置、SDK 示例、协议能力表、错误处理、数据库迁移/恢复方法；建立确定性回归案例集。清理未兑现的对外能力声明，保持后续扩展在独立待办区。

**验收流程：**无工具对话 → 工具调用 → 多轮历史 → 文件生成/编辑 → 命令验证 → 计划 → 子 Agent → 摘要 → 数据库恢复 → 中断恢复 → 三协议 → Embedding。

**验证：**运行必要的全量测试和 package/消费者 smoke；已有 myWorld 普通对话/配置解密回归；检查三 AI 模块都是普通 JAR；记录 JDK/依赖、命令、结果和未执行的真实模型/其他平台测试。不为提高数量反复运行已充分验证的检查。

**回归案例：**简单生成、编译错误修复、工具参数错误、提示词覆盖、长上下文、命令超时、子任务失败、写操作不确定、跨会话隔离。模型真实输出可非确定，契约和文件副作用必须可确定断言。

**个人实践：**由用户新增一个业务只读工具，完成注册、描述、参数和测试；能说明新模型适配应改哪里，新增应用为何不用改 Agent 循环。

**退出条件：**全部必做功能有证据；扩展清单有明确优先级；PROGRESS 的工程状态与学习状态分别准确；新会话可从文档重现运行。

## 阶段交付格式

```text
阶段编号与目标：
本次跑通的输入 → 状态变化 → 输出：
关键文件与方法（绝对路径及准确行号）：
参考实现哪些被保留、哪些被调整以及原因：
测试命令与真实结果：
运行证据属于假模型 / 本地协议服务 / 真实供应商：
设计取舍与剩余限制：
需要用户回答的 2–3 个理解问题：
下一阶段及其前提：
```
