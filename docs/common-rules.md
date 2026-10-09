# 通用开发规则

## Java 注释与代码风格

- 新建类必须使用中文 Javadoc，包含类描述、`@author Sebastian` 和当天实际日期的 `@since yyyy/MM/dd`。
- 修改已有类时保留原作者和创建日期。
- 新增或修改的方法必须使用 Javadoc，参数使用 `@param`，非 `void` 返回值使用 `@return`，需要调用方关注的异常使用 `@throws`。
- 成员变量、常量和局部变量的说明使用多行 `/** ... */` 文档注释。成员变量的注释放在声明上方；方法体内的临时变量说明使用 `//` 双斜杠注释，不使用局部变量 Javadoc。
- 方法体内的过程性说明使用 `//` 双斜杠注释，方法用途和契约仍写在方法 Javadoc 中。
- 优先使用 Lombok；能使用 `@Data` 解决的普通 DTO、配置类和数据对象，不再叠加过多花哨注解。只有明确需要不可变对象或特殊访问器时才使用 `@Value`、`@Builder` 等注解。
- 尽可能不用 `record`，优先使用 `public class` 配合 Lombok；非必要不使用内部类，条件类、策略类和业务实现都优先拆成独立顶层类。
- 日志统一使用 Lombok 的 `@Slf4j` 注解，不手写 `LoggerFactory`、`Logger` 字段或重复初始化日志对象。

## Spring Boot 业务分层

- 业务模块按传统 Spring Boot 分层组织：`controller`、`service`、`service.impl`、`entity`、`mapper`、`task`、`config` 和 `properties`。
- `entity` 下按用途拆分 `req`、`resp`、`dto` 和 `entity`；`entity.entity` 仅放 MySQL/持久化映射实体，不能把外部接口 DTO 当作数据库实体。
- `controller` 只负责协议适配和参数校验；业务编排放在 `service`，实现放在 `service.impl`；数据读取、持久化和外部数据映射放在 `mapper`；定时任务只放在 `task`。
- Service 实现使用 `@Service`，Controller 使用 `@RestController`，定时任务使用 `@Component`，依赖通过构造器注入；只有确实需要条件装配时才使用 `@Bean` 和配置开关。
- AI 能力通过业务服务接口依赖，不能因为是 AI 项目而把 Controller、Service、Mapper 和 Task 平铺在同一个包中。
- 36Kr 的官方英文名为 `36Kr`。Java 包名不能数字开头，因此使用 `kr36`，例如 `com.dingbang.myworld.news.kr36`。

## 通用能力与集合判断

- 可复用的工具类、MyBatis-Plus 自动填充（例如 `hasSetter`、`hasGetter`、创建时间和修改时间填充）统一放在 `common` 模块，业务模块不复制实现。
- 优先使用 `common` 模块中的集合、字符串、对象和日期工具；没有合适工具时再使用 JDK 或第三方工具包。
- 不写 `!= null && size == 0`、`collection != null && collection.isEmpty()` 等重复判断；使用项目通用的 `CollectionUtils.isEmpty`、`StringUtils` 或对应工具方法。
- 循环优先级为 `ListUtils` > Stream 流 > 增强 `for` > `fori`；只有必须依赖节点索引等场景才使用 `fori`。
- 对外输入、Webhook 和 RSS 数据都要在边界处校验，业务代码只处理已完成校验和转换的数据。
