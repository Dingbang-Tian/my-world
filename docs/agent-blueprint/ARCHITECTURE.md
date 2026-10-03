# 架构与行为契约

本文件约束最终实现方向，类名是建议名。按阶段引入，未进入的阶段不先生成空类。

## 1. 模块与包

依赖方向分为两条链：`my-world-app → my-world-ai → my-world-common`（Spring AI 普通对话）以及 `my-world-app → my-world-ai-app → my-world-ai-agent → my-world-ai-framework → my-world-common`（自研 Agent 框架）。`my-world-ai` 不被 Agent 模块依赖；未来若由 Spring AI 实现 `ModelGateway`，只能由 Spring AI 适配模块单向依赖 framework。禁止 framework 依赖 Spring AI，禁止 ai 反向依赖 agent，禁止 agent 依赖 codegen。

```text
my-world-ai-framework / com.dingbang.myworld.aiframework
  api/                  协议中立的单次模型请求、响应、事件和模型能力接口
  model/                协议中立的消息、内容块、工具调用和工具结果
  provider/             模型实例注册、modelId 解析、能力检查（后续阶段）
  adapter/              自研协议适配器（后续阶段）

my-world-ai / com.dingbang.myworld.ai
  application/          保留已有 AiChatService 等普通对话接口
  adapter/               Spring AI ChatClient 适配
  config/ crypto/        Spring AI 配置与密钥实现

my-world-ai-agent / com.dingbang.myworld.agent
  api/                  AgentService、AgentRun、请求、结果、事件、查询和异常
  runtime/              唯一 Agent 循环、执行状态、取消、限额
  tool/                 工具接口、描述、注册、校验、执行与事件
    annotation/         供工具实现类和参数字段使用的注解
  skill/                工具组合及使用说明
  prompt/               模板加载、变量渲染、默认值、快照
  session/              会话、消息追加、并发控制、序列化
  memory/               上下文选取、摘要、压缩阈值
  orchestration/        顺序计划、子 Agent，及对应的两个编排工具
  persistence/          存储接口；memory/、jdbc/ 实现
  observation/          事件监听、指标、审计、脱敏
  config/               Spring 装配、配置属性

my-world-ai-app / com.dingbang.myworld.aiapp
  codegen/api/          CodegenService、请求、结果
  codegen/application/  代码生成流程、Agent 定义装配
  codegen/tool/         九个文件工具与命令工具、参数、工作目录策略
  codegen/skill/        文件、命令技能说明及组合
  codegen/config/       工具开关、模型、工作目录与模板配置
  resources/prompts/codegen/  代码生成及相关技能模板（实际在 src/main/resources 下）

my-world-app / com.dingbang.myworld
  web/codegen/          薄的演示入口、状态查询、SSE、取消
  resources/            application.yml、环境配置、应用级覆盖
```

公共工具机制归 agent；具有操作系统副作用的代码生成工具归 ai-app/codegen；`create_plan` 和 `create_sub_agent` 归 agent/orchestration。以后其他应用可选择注册这些工具或提供自己的实现，agent 无需反向导入应用类。

命名按真实职责区分：`ModelGateway` 是单次模型调用入口，`AgentService/AgentRun` 是一次 Agent 运行的公共 API，`ToolCall/ToolResult` 属于模型消息数据，`Tool/ToolRegistry/ToolExecutor` 属于可信 Java 工具机制。当前无独立的远程 Agent 客户端职责，因此不引入泛化的 `agent/client`；模型消息数据也不改称 `AgentMessage`。简单事件和文本内容实现与接口同包，避免仅为 `eventImpl/contentImpl` 增加一层包。

公共 API 的请求／结果放 framework 或 agent 的 api 包；扩展者还可使用明确标为扩展契约的 tool、skill、prompt、persistence 接口。公开契约使用顶层接口和实现类，不把内部实现类、Spring AI 模型对象或数据库连接暴露给调用方。

## 2. 单次模型接口

模型适配器只执行一轮请求。请求里可声明工具，响应可要求调用工具，但适配器不执行 Java 工具、不循环续问。

S06 已把可信工具描述转换为 `ModelToolDefinition`（名称、用途、JSON Schema 字符串）并附在每次 `ModelRequest`。`DefaultAgentRun` 在每轮独立监听器的完成回调后调度下一步：工具回合按 callId 记录完整助手消息、逐个执行并记录工具消息，随后再次调用同一个 `ModelGateway`；文本正常结束才提交整段交换到进程内会话。异常工具结果会作为普通 TOOL 消息交给模型，模型回合限制在下一批工具执行前检查。协议适配器仍只负责单轮转换。

| 契约 | 输入／输出与职责 |
|---|---|
| ModelGateway | 根据 modelId 查找适配器，执行 `generate(request, listener)` 并通过 `ModelEventListener` 回调事件 |
| ModelRequest | modelId、消息快照、工具描述列表、调用级选项、deadline、关联 ID |
| ModelOptions | thinkingEnabled（可空）、temperature（可空）、maxOutputTokens 等；不修改共享模型配置 |
| ModelEvent | TextDelta、ReasoningDelta、ToolCallDelta、Usage、TurnCompleted；异常通过 `listener.onError(error)` 传播 |
| ModelTurn | 完整 assistant 消息、完整工具调用列表、标准化结束原因、本轮用量、必要的协议元数据 |
| ModelCapabilities | streaming、tools、reasoning、image/audio/video/document、embedding 等能力声明 |
| EmbeddingGateway | 单独的向量输入输出契约，不能拿向量模型发起聊天 |

一次 `generate(request, listener)` 调用触发一次模型调用。适配器必须按顺序回调零到多条事件，最后恰好调用一次 `onComplete()` 或 `onError(error)`；runtime 每轮只调用一次，不再另调同步方法取结果。ModelTurn 是该轮完整结果的真相来源，文本 delta 用于展示，不能把二者重复追加到历史。

providerId、protocol、modelId 不混为一谈：同一协议可接多个供应商，同一供应商可配置多个模型实例。自定义协议通过实现适配器和注册器接入，不通过每加一个供应商就修改核心 switch。

未知模型、未配置凭据、请求使用不支持的工具／附件能力：在联网前返回可识别的配置或能力错误。不得静默丢弃附件、工具或 thinking 选项。模型专有字段限制在适配层。

现有 AiChatService 继续服务文本对话；初期保持 `my-world-ai` 内的 SpringAiChatAdapter。自研 Agent 只依赖 `my-world-ai-framework`；待新 gateway 稳定后，如果需要让 Spring AI 提供自研 gateway，再增加单向的 Spring AI → framework 适配，必须先通过原有普通对话回归，避免同时维护两套已分叉的业务逻辑。

## 3. 消息与工具的中立表示

Message 至少包含：messageId、role、contentBlocks、toolCalls、toolResults、providerMetadata。角色明确支持 SYSTEM、USER、ASSISTANT、TOOL；Anthropic 这类单独传 system 的协议由适配器转换。

contentBlocks 区分文本、图片、音频、视频、文档等；附件记录 MIME、名称、来源（内存／URL／本地资源引用）。按参考实现实际支持的模型组合做能力矩阵，不承诺任意模型都支持所有媒体。大附件存储引用，不无上限地把二进制写进历史 JSON。

ToolCall 至少包含 callId、name、argumentsJson；ToolResult 包含 callId、状态、内容、错误码、是否截断。服务端发来的 reasoning/signature 等仅按协议需要透传和保存元数据，不假定所有模型都会提供。

必须成立的消息约束：

- 工具调用由 assistant 发起；工具结果能通过 callId 找到该调用。
- arguments 分片按本次请求的 index/callId 聚合，得到完整 JSON 后才能执行。
- 普通工具失败生成结构化失败结果，可回传模型纠正；取消、超预算等执行控制不能伪装成普通工具成功。
- 同一轮多个工具默认顺序执行；不擅自并发有写操作的工具。
- 历史保存完整调用与结果，不只保留用户文本和最终回答。
- 下一请求不携带孤立的工具结果或缺结果的历史调用；中断导致的不完整交换在恢复阶段处理。

## 4. SDK 及执行生命周期

| 建议 API | 语义 |
|---|---|
| AgentService.prepare(request) → AgentRun | 校验和构建运行句柄；不启动模型或工具执行 |
| AgentRun.execute() | 显式启动一次；第二次调用拒绝，不能重复发请求 |
| AgentRun.subscribe(listener) | 注册同一运行的事件监听器，并回放有限历史；注册本身不启动第二次执行 |
| AgentRun.getResult() → CompletionStage<AgentResult> | 同一运行的最终结果；正常、失败、取消都能结束，不永久悬挂 |
| AgentRun.cancel() | 幂等取消，传递到模型订阅、子任务和受管进程 |
| AgentService.run(request) → AgentResult | 同步便利入口，内部 prepare/execute/等待；文档明确阻塞及超时 |
| SessionService | 创建、查询、导出／导入会话，按 owner/app 校验访问边界 |

默认 SDK 使用流程：prepare → `subscribe(listener)` → execute → 等待 result。`DefaultAgentRun` 使用 JDK `Executor` 调度模型调用；阻塞进程读取或 JDBC 应在明确的执行器中调度。

AgentRequest 至少包含 appId、agentId、sessionId（可空则新建）、用户输入、附件、requestId 和经过授权的上下文。modelId、工具集、工作目录、限制从可信 AgentDefinition 或 SDK 调用上下文解析，不能任由 Web 用户传入任意工具与本地路径。

AgentDefinition：身份、描述、modelId、promptRef、skillIds、toolIds、limits。AgentResult：runId、sessionId、terminalStatus、finalText、usage、stopReason、结构化错误与产物引用。

终态固定为 COMPLETED、FAILED、CANCELLED、TIMED_OUT、LIMIT_EXCEEDED、INTERRUPTED。持久化后的 RUNNING 任务在进程重启时归为 INTERRUPTED，经显式恢复创建新 run，保留原 run 的终态及关联。

事件含 runId、sessionId、sequence、timestamp、类型和结构化 payload；覆盖文本、reasoning（如有）、工具三阶段与错误、计划步骤、子 Agent、压缩、用量、终态。sequence 在单 run 内单调递增，父子通过 parentRunId 关联，不能仅用工具名识别调用。

事件缓存有界。建议默认重放最近 512 条；过慢消费者或历史缺口发出可识别错误，客户端改查完整结果／持久化事件。不能让无限文本流耗尽内存。持久化前只承诺同进程有限重放，S18 后才能提供重启后的事件查询。

## 5. 唯一 Agent 循环

```text
校验 AgentDefinition → 获取会话独占执行权 → 创建本次运行
  → 加入 user 消息 → 组装提示词及上下文 → 检查预算
  → 调用一次 ModelGateway
  → 聚合本轮流式事件与完整 ModelTurn
  → 有工具调用？
       是：追加 assistant 调用 → 逐个校验和执行工具 → 追加结果 → 下一轮
       否：记录最终 assistant → 必要时更新摘要 → 完成运行
  → 任意终态均释放会话执行权、关闭订阅和受管资源
```

示例轨迹：`USER:读取说明文件 → ASSISTANT:call c1/view_file → TOOL:c1/文件内容 → ASSISTANT:总结`。应在 S06 测试中真实断言这条序列。

HTTP 成功不代表模型语义完成；EOF 也不一律视作成功。按协议的结束事件判定终态，处理 length/拒绝/错误等结束原因。finish_reason 后可能还有 usage，不能提前取消造成用量丢失。完成后因本地主动取消产生的迟到错误不覆盖已确定终态。

错误分类至少包含：INVALID_REQUEST、CONFIGURATION_ERROR、UNSUPPORTED_CAPABILITY、MODEL_ERROR、TOOL_NOT_FOUND、TOOL_VALIDATION_ERROR、TOOL_EXECUTION_ERROR、POLICY_DENIED、SESSION_BUSY、VERSION_CONFLICT、PERSISTENCE_ERROR、TIMEOUT、CANCELLED、LIMIT_EXCEEDED。

取消／超时由单一状态机竞争完成，重复回调只能产生一个终态。模型失败与普通工具失败策略不同；模型流收到部分输出后不透明重试整个回合，写工具执行后不重试整段 Agent 流程。

## 6. 工具与技能扩展

Tool<P> 描述名称、说明、参数类型／Schema，并接收 P 和 ToolExecutionContext 执行，返回 ToolExecutionResult。Context 包含 run/session、授权工作目录、deadline、取消令牌、审计关联；不把这些可信值放进模型可随意填写的参数。

支持接口显式描述和 @ToolInfo/@Param 风格注解。注册时生成并验证 Schema，首次至少覆盖参考项目使用的字符串、数值、布尔、列表、枚举、必填；复杂嵌套结构必须支持或明确拒绝，不能生成看似合法但不可执行的 Schema。

同名不同工具拒绝注册；多个 skill 引用同一工具身份可去重。运行开始解析工具集为快照；禁用工具既不发给模型，也不能通过手工伪造名称调用。

Skill 是工具 ID 列表加使用说明模板，不持有 lastCreatedPlan/lastTask 这样的跨运行可变状态。计划／子 Agent 从参数直接生成当前 run 的执行对象，通过显式的运行上下文接入 runtime。

工具拦截扩展点提供 before/after/error，可用于策略、审计与指标。普通扩展工具无需修改 Agent 循环。远程工具及 MCP 列为后续接口实现，不在必做阶段引入。

## 7. 提示词与配置

模板类别：agent/system、agent/plan-step、agent/summary、agent/sub-agent；codegen/system、codegen/skills/files、codegen/skills/command。模板使用明确的占位符（建议 {{name}}）；缺必需变量在执行前报错；不使用任意 Java/SpEL 表达式作为模板求值。

每个模板 key 的查找优先级：项目配置的覆盖 → 应用资源提供的覆盖 → agent 公共默认值。`null/未配置` 表示继承；空字符串视为配置错误，关闭某块用显式 enabled=false。用户任务作为 USER 消息传递，不能自动升级为 SYSTEM 模板。

系统上下文、skills、压缩摘要与用户历史按明确槽位拼装；技能说明不得凭名字被当成工具授权。首次读取所有模板后生成不可变配置快照，记录模板 ID 与内容 hash；同一运行不随配置变化而改变。后续热更新在运行边界切换版本。

以下仅为目标配置形状，具体绑定类在 S03/S07 落地，不是当前项目可直接使用的配置：

```yaml
my-world:
  ai:
    models:
      codegen-main:
        protocol: openai-chat
        base-url: ${AI_BASE_URL}
        api-key: ${AI_API_KEY}
        model: ${AI_MODEL}
  agent:
    storage:
      type: memory # S18 可切 jdbc
    prompts:
      system:
        location: classpath:/prompts/agent/system.md
      summary:
        location: classpath:/prompts/agent/summary.md
    limits:
      max-model-rounds: 20
      max-tool-calls: 50
      run-timeout: 5m
      tool-timeout: 60s
      max-sub-agent-depth: 2
      max-output-chars: 200000
    memory:
      trigger-rounds: 20
      reserve-output-tokens: 4096
  ai-app:
    codegen:
      model-id: codegen-main
      workspace-root: ${CODEGEN_WORKSPACE}
      system-prompt:
        location: classpath:/prompts/codegen/system.md
      files-enabled: true
      command-enabled: false # 能力保留，实际运行环境明确开启
```

数值是可覆盖的学习默认值，S08/S15 需按实际模型上下文能力验证。TemplateRegistry 按模板 key 允许应用资源覆盖，不依赖 classpath 同名文件的偶然加载顺序。API Key 沿用 ENC/环境变量能力，日志只输出非敏感模型 ID 和模板 hash。

## 8. 代码生成与执行边界

业务流程：输入需求与受信任的 workspace 配置 → CodegenService 创建／复用会话 → 注册文件/命令/编排技能 → 调用 AgentService → 收集变更文件、命令验证结果和最终说明。

九个文件工具保持参考语义，见 CHECKLIST.md。路径既可接受根内的绝对路径，也可接受相对路径；统一按授权 workspace 解析。检查 `..`、符号链接、移动源和目标、创建目标现存父目录的 real path；拒绝目录外写入。跨运行文件修改使用 hash/版本预条件避免覆盖读后变更。

路径约束不是完整 OS 隔离：可执行 shell 会访问进程权限允许的资源。命令工具第一版只面向受信任的本地学习工作区；若用于不可信多人服务，需要后续容器或独立执行器，不把设置 cwd 宣称成沙箱。

命令显式使用工作目录和过滤后的环境。stdout/stderr 异步读取且有输出上限，同时计时等待进程；不能先阻塞读完输出再开始超时计时。取消或超时尽力终止进程树、关闭资源，记录未能终止的情况。支持 macOS/Linux shell 和 Windows PowerShell；只在实际测试的平台上声称已通过执行验证。

所有写入、移动、删除记录产物与操作结果。文件快照用于人工恢复，不声称 Git/文件系统/数据库之间有原子事务。Shell 参数内含任意脚本时，不能依靠简单关键词黑名单证明安全。

## 9. 计划与子 Agent

PlanRunner 创建不可变步骤定义和独立可更新状态。默认顺序执行；后续步骤明确获得之前已完成步骤的结构化摘要与产物，不仅再次带入原始父历史。步骤状态 PENDING/RUNNING/SUCCEEDED/FAILED/SKIPPED；失败默认停止，CONTINUE 必须为显式策略。计划可配置步骤上限。

计划步骤的模型调用也走唯一 runtime；明确限制步骤再次 create_plan，避免自嵌套；工具授权和执行器逻辑与主任务统一。计入顶层总预算。不能把某个步骤失败后依旧返回的 completed 文本当计划成功。

SubAgentRunner 创建独立会话和 childRunId；任务说明和选定上下文显式传入，不自动复制父全部历史。工具范围为父授权集合的子集，工作目录不扩大；模型服务可共享，历史和请求选项不能共享可变状态。第一版顺序等待，深度和全树预算有上限；父取消向子传播。

子用量和计划用量按 modelCallId 去重后计入根任务，避免既计本地又计事件造成双算。子结果以关联父 callId 的 tool 消息返回。

## 10. 会话、上下文与数据库

必须区分：完整历史（事实记录）、本轮选取的上下文（实际发给模型）、摘要（有损压缩）、长期事实（后续扩展）。压缩不删除数据库原始消息。

一次会话默认只允许一个顶层运行写入；第二个请求返回 SESSION_BUSY。初期内存锁；MySQL 持久化阶段增加数据库持有者/租约与 version 校验，不能认为 JVM 锁能跨进程互斥。多实例完整容错仍是后续扩展，第一版部署以单实例为基线。

| 建议表 | 关键字段与约束 |
|---|---|
| agent_session | id、owner_key、app_id、agent_id、version、lease_owner、lease_until、created/updated_at；索引 owner/app/更新时间 |
| agent_message | id、session_id、seq、run_id、role、body_json、created_at；unique(session_id, seq) |
| agent_run | id、session_id、request_id、parent_run_id、root_run_id、status、model_id、prompt_hash、error、usage_json、started/ended_at；约束应用范围的幂等请求键 |
| agent_run_event | run_id、seq、type、payload_json、created_at；unique(run_id, seq)，支持状态重放 |
| agent_tool_execution | id、run_id、call_id、name、args_json、args_hash、status、result_json、started/ended_at；unique(run_id, call_id) |
| agent_memory_summary | id、session_id、through_message_seq、summary、source_hash、template_hash、created_at；索引 session/覆盖范围 |
| agent_plan / agent_plan_step | plan 与 root/run 关联；step_index、状态、结果、失败原因；unique(plan_id, step_index) |
| codegen_artifact | run_id、path、操作类型、before/after_hash、备份引用；不将整个文件无上限写入事件 |

数据库层使用版本化迁移脚本。运行时使用 MySQL 和 MyBatis-Plus Service/Mapper；真实数据库测试使用独立 MySQL 测试库，H2 旧实现仅供回归。JSON 存在 LONGTEXT，不绑定数据库专有 JSON 查询。数据库实现放现有 agent 模块内；codegen 产物实现放 ai-app/codegen，不倒置依赖。

工具执行前保存意图，执行后保存结果及配对消息；网络等待、模型生成和外部进程不占长数据库事务。副作用已发生但结果尚未落库时，任务状态只能标为不确定，不能宣称“恰好一次执行”。恢复要识别这一窗口。

摘要保留覆盖序号；上下文 = 系统模板 + 有界摘要 + 未覆盖的完整交换 + 当前输入。Token 估算需注明估算；模型反馈用量用于校准。触发判断应在下一次请求前执行，完成后也可预生成摘要。summary 失败保留原数据；如果仍超出可用窗口则明确报错，不静默截断工具配对。

摘要调用禁用工具且受根预算约束；恢复 session 不序列化 API Key、模型客户端、线程、进程或锁。导出含 schemaVersion，导入重新绑定 AgentDefinition、校验 owner/app 和模型/工具引用，不能凭历史 JSON 获得新的工具权限。

## 11. 中断与恢复

重启后将未完成运行标为 INTERRUPTED，保留最后已提交消息、步骤和工具状态。恢复创建新 run 并引用原 run；已确定完成且仍有效的只读工具结果可以复用。

对“执行中但没记录结果”的写文件/命令：检查产物 hash、操作 ID 或由用户选择；未知状态不自动重跑。已取消任务不因启动扫描而自动恢复。重启前的 shell 进程也不能只凭 PID 自动接管或终止，应核对运行标记并暴露需要处理的状态。

恢复行为在确定性测试中覆盖数据库写入前后故障注入。第一版保证可解释的检查点恢复，不承诺任意 JVM 调用栈、网络流或外部进程无损续接。

## 12. 验证与完成证据

默认测试：JUnit + scripted 模型 + 本地 HTTP/SSE fixtures + 临时文件目录；MySQL 集成测试由专用数据库连接环境变量启用，H2 仅保留旧持久化回归。真实模型 smoke 另行启用；缺真实凭据不跳过离线验收，也不能宣称已验证真实供应商。

测试围绕行为边界：消息配对、流终态、超时、取消、会话隔离、提示词覆盖、工具副作用和恢复；不为每个 DTO getter 编写机械测试。每个协议适配器必须通过统一契约及自己的协议 fixture。

修改后验证当前阶段相关模块和依赖；全量测试留给里程碑和最终验收。目标工作区使用 `mvn -pl <模块> -am test`；实际插件/命令以 S00 为准。故障属于既有基线还是新引入需记录，不顺手改造无关 common 工具。
