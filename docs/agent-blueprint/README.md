# myWorld Agent 工程图纸与学习路线

本文件是另一会话的总入口。制定日期：2026/10/01。当前交付为工程计划，未实现下列功能、未运行构建或测试。

## 1. 目标与已确定的边界

把 Agent4J 的现有能力迁入 myWorld，形成可由其他业务模块通过 Maven 依赖使用的 Agent 中台。第一款接入应用为代码生成。用户同时以此学习 Agent 的完整实现，因此默认每次只实现一个阶段，并在该阶段形成可运行、可解释、可验证的结果。

用户已确定：

- 目标仓库：`/Users/sebastian/myPorject/myWorld`。
- 参考源码：`/Users/sebastian/myPorject/openSource/agent4j-main`。
- AI 相关模块包括 `my-world-ai-framework`、`my-world-ai`、`my-world-ai-agent`、`my-world-ai-app`。
- `my-world-ai-framework` 负责协议中立的自研模型契约和数据对象，不引入 Spring AI；`my-world-ai` 负责 Spring AI 接入和配置；`my-world-ai-agent` 负责公共 Agent 功能及对外 API；`my-world-ai-app` 负责接入场景，代码生成代码归入 `codegen` 包。
- 现有 `my-world-app` 继续承担 Spring Boot 启动及 Web 入口，它与 `my-world-ai-app` 是两个不同模块。
- Agent4J 的模型协议、工具、技能、流式输出、计划、子 Agent、会话、压缩、序列化、多模态和 Embedding 能力全部纳入功能对照。
- 提示词从 Java 静态代码拆出；通用模板带默认值，应用可以覆盖；第一版启动时加载，热更新列为后续扩展。
- 模型协议、工具和应用可扩展；会话数据库存储纳入此次完整实施路线。
- 不在此次规划中额外拆 `core`、`spi`、`starter`、`storage` 等 Maven 模块，以包划分职责。

## 2. 已核对的项目基线

| 项目 | 当前事实 | 对实施的影响 |
|---|---|---|
| myWorld | Maven 多模块；Java 21；父 POM 声明 Spring Boot 3.5.16、Spring AI 1.1.8 | 这些是本地声明值，不是最新版本推荐；先沿用，验证依赖实际可解析 |
| 模块 | 当前只有 common、ai、app | 新增 ai-framework、ai-agent、ai-app；更新父 POM 和启动模块依赖 |
| AI 接入 | 已有 AiChatService、SpringAiChatAdapter、AiProperties、SpringAiConfiguration | 保留普通对话 API；增加能携带结构化工具调用的模型接口 |
| 现有请求 | AiChatRequest 带 conversationId，但适配器未使用它管理历史 | 不能把已有字段当成已完成会话记忆 |
| 现有流式 | AiChatService.stream 返回 Flux<String> | 新 Agent API 使用结构化事件，普通文本接口保持用途 |
| 配置 | 已有默认系统提示词配置和 ENC 配置解密实现 | 复用配置与密钥处理，不打印、复制真实密钥或密文到学习材料 |
| 数据库 | 检查到的模块 POM 中未接入数据库持久化 | 增加可替换存储；本地 JDBC/H2 文件库作为无需服务的学习实现 |
| Git | 检查时 myWorld 工作区干净，分支为 feat-20260925-projInit-Sebastian | 执行会话必须重新检查，不依赖此时快照，不覆盖后来改动 |
| Agent4J | POM 声明 2.4.0、Java 17；本地参考目录没有可用 Git 元数据 | 用路径、方法和内容摘要记录来源，不虚构参考提交 SHA |
| 入口差异 | MainTest.main 调用 enbeddingTest；agentTest 才是 Agent 示例 | 不直接启动参考 main 作为迁移验证 |

已有代码位置：目标仓库 `my-world-ai/src/main/java/com/dingbang/myworld/ai/` 下的 `application/`、`adapter/`、`config/`、`crypto/`；参考仓库 `src/main/java/ink/icoding/llm/`。

参考目录含 GPL v3 LICENSE。本计划只记录这一来源事实；迁移中保留适用的来源和许可证信息，不把已有类作者统一改成自己的名字。对外发布时另行核对适用的许可证要求。

## 3. 图纸文件及使用顺序

| 文件 | 用途 |
|---|---|
| [ARCHITECTURE.md](./ARCHITECTURE.md) | 模块、包、接口、消息、状态、配置、存储与异常的设计约定 |
| [STEPS.md](./STEPS.md) | S00–S22 共 23 个阶段，每阶段的实现范围、流程、测试、学习问题 |
| [CHECKLIST.md](./CHECKLIST.md) | 全功能对照、明确改进、企业级待办与最终验收 |
| [PROGRESS.md](./PROGRESS.md) | 编码完成与学习掌握分开记录，供跨会话接续 |

这组文档目前存放在参考项目的 `doc/myworld-agent-blueprint/`。开始实施时，把整组文档复制到目标仓库 `docs/agent-blueprint/`，之后只更新目标副本作为进度真相来源。参考副本保留为原始图纸，不双向维护。若目标已有同名文件，先读取比对，不直接覆盖。

## 4. 默认技术决策

以下为基于用户目标采用的实施默认值，尚非已实现事实。另一会话可在有证据时小幅调整，并在 PROGRESS.md 的决策记录写明原因和影响。

1. 四个 AI 模块是普通 JAR；只有现有 my-world-app 执行 Spring Boot 可执行包重打包。
2. 新模型接口采用协议中立的消息和事件；Agent 循环只在 ai-agent 中实现一次。
3. 保留现有 Spring AI 普通对话接入；新 Agent 模型接口优先以 Agent4J 的 HTTP/SSE 实现为参考，做单次模型请求适配。不得把旧实现里的 Agent 循环原样放进适配器。
4. 如使用 Spring AI 实现新模型接口，必须先验证所用版本能关闭内部工具执行并返回原始工具请求，通过同一套适配器契约测试。两条实现路线不能同时对一次请求负责工具循环。
5. 本地首次运行通过 scripted 假模型完成，不依赖真实 Key；真实模型凭据存在时再单独做冒烟验证。
6. 提示词用 YAML 配置内联文本或指向 UTF-8 模板资源；公共默认模板位于 ai-agent，codegen 模板位于 ai-app。启动加载，校验后生成不可变快照。
7. 工具注册用接口及 Spring Bean 集合，支持显式注册；Java ServiceLoader 和远程工具接入是后续扩展，不要求插件动态装载。
8. 先顺序工具、顺序计划、顺序等待子 Agent。并发执行是后续可选优化，不影响保留参考项目能力。
9. 存储先内存，后 JDBC + H2 文件库完成持久化和重启演示；生产数据库尚未指定，不虚称已支持所有数据库。
10. 第一版 SDK 以本项目接口与 DTO 为契约，事件流使用已有技术栈中的 Reactor Flux。核心对象不暴露 OkHttp、Spring AI、JDBC 类型。
11. 代码生成可以在明确配置的工作目录内执行文件工具和命令工具；测试只用临时目录和确定性命令。Shell 的工作目录不是操作系统沙箱。
12. 如目标仓库当前存在其他实现，优先兼容和复用；不为了完全匹配图纸类名进行大范围重命名。

## 5. 教学和编码节奏

默认每次执行一个 S 编号。阶段内部无需反复询问是否继续编辑某个文件。

1. 开始前用几句话说明本阶段解决的问题，画一条输入到输出的最小调用链；指出本阶段要读的 2–4 个参考方法。
2. 用具体例子预测消息、状态和输出。涉及有状态逻辑时先确定有意义的验收场景。
3. 实现该阶段需要的类；不要一次生成后面十个阶段的空壳。
4. 运行相应测试或示例，展示实际消息／事件轨迹。模拟响应与真实模型响应必须标明。
5. 交付：关键文件和方法、真实验证结果、一个设计取舍、2–3 个理解问题、下一阶段入口。
6. 更新 PROGRESS.md。代码通过测试只代表工程完成；只有用户反馈才代表学习掌握。默认停在阶段边界，等待用户回答或说“继续”。

用户如果明确要求连续完成多个阶段，可按指定范围继续；仍按阶段保留讲解、验证和交接记录。学习模式不等于放松测试，也不等于一次把全部代码写完再解释。

## 6. Java 注释规则（复制到新会话仍有效）

- 新建 Java 类使用中文 Javadoc 类说明、`@author Sebastian`、`@since yyyy/MM/dd`；日期用该类实际创建当天，不直接照搬本文日期。
- 修改已有类保留原作者和创建日期。迁入已有代码时保留适用的来源标注。
- 新增／修改方法使用 Javadoc，参数用 `@param`、非 void 用 `@return`、需要调用方了解的异常用 `@throws`。
- 新增／修改字段、常量、局部变量在声明上方使用 `/** ... */` 说明；普通行注释不能代替。参数由方法 Javadoc 说明。
- 注释与行为一致：是否阻塞、何时执行、是否仅注册回调，应说明真实语义。

## 7. 新会话启动提示词

把下面整段发给在 myWorld 项目中开启的新会话：

```text
请作为实现伙伴和源码教练，按工程图纸逐步完成我的 Agent 学习项目。

目标项目：/Users/sebastian/myPorject/myWorld
参考项目：/Users/sebastian/myPorject/openSource/agent4j-main
初始图纸：/Users/sebastian/myPorject/openSource/agent4j-main/doc/myworld-agent-blueprint/README.md
后续工作副本：/Users/sebastian/myPorject/myWorld/docs/agent-blueprint/

若工作副本存在，先读工作副本 README.md、PROGRESS.md、ARCHITECTURE.md、CHECKLIST.md，
再读 STEPS.md 中下一待执行阶段；否则先读初始图纸及其四个关联文件，并在 S00 复制到工作副本。
不要只凭此提示词重新设计架构。

确定的模块是：my-world-ai-framework（自研模型契约）、my-world-ai（Spring AI 接入）、my-world-ai-agent（公共 Agent）、
my-world-ai-app（AI 应用，代码生成归 codegen 包）。现有 my-world-app 是启动模块。
完整保留对照表中的功能；提示词从配置／资源加载；接口支持模型、工具、提示词和存储扩展。

默认每次只执行一个阶段。先解释本阶段调用链，然后实现、验证、展示轨迹，
最后给出 2–3 个理解问题并更新进度，等我说继续。第一次从 S00 开始。
S00 是基线盘点与文档接入，不要直接跳到全量实现。

代码完成和学习掌握分开记录。测试没有运行或未通过必须直说。
遵守图纸里的 Java Javadoc 规则和目标项目现有规则，保留已有改动。
不要调用参考 MainTest 的真实生成任务；测试使用假模型、临时目录和本地协议服务。
不要自动创建新会话或并行子代理。必要的技术调整写入决策记录。
当前授权是在目标项目实施当前阶段；不包含发布、推送、部署或执行模型任意生成的系统命令。
```

接续时可简写：“读取 myWorld/docs/agent-blueprint/PROGRESS.md，继续下一个未完成阶段；先确认我的上一阶段理解问题，再按图纸实现。”

## 8. 完成定义

所有必做阶段工程验收通过；CHECKLIST.md 的保留功能均有实现位置和验证证据；普通 Maven 依赖可以调用 Agent；代码生成形成完整流程；持久化会话重启后可继续；中断恢复不盲目重复有副作用的工具；提示词可配置且没有协议双循环。

学习完成另行判断：用户能解释一次工具调用、一次计划、一次子任务、一次上下文压缩、一次会话恢复各自的消息与状态变化。
