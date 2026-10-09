# 36氪新闻日报

应用使用 [36氪官方 RSS 订阅中心](https://www.36kr.com/rss-center)公布的文章和快讯 RSS。每天筛选最近 24 小时的新闻，按原文链接去重，最多取 25 条，交给项目现有的 `AiChatService` 生成中文摘要。日报正文附有全部采用新闻的原文链接。

默认订阅地址为 `https://www.36kr.com/feed-article` 和 `https://www.36kr.com/feed-newsflash`。裸域名 `36kr.com` 的订阅路径可能返回 HTML 安全检测页面，因此抓取使用 `www.36kr.com`。

业务代码位于 `com.dingbang.myworld.news.kr36`：RSS 外部数据映射在 `mapper`，日报接口和实现位于 `service`/`service.impl`，日报和飞书响应位于 `entity.resp`，RSS 和飞书正文 DTO 位于 `entity.dto`，飞书请求位于 `entity.req`，定时任务位于 `task`，手动预览接口位于 `controller`。`entity.entity` 已预留；当前实现不落 MySQL，当前预览接口也没有请求体。

## 启用

日报服务和预览接口随应用启动；只有配置推送目标后才会创建每日推送任务。支持通用 JSON Webhook 和飞书自定义机器人，使用以下环境变量配置：

```bash
export MYWORLD_NEWS_DIGEST_WEBHOOK_URL='https://your-receiver.example/digest'
```

本地启动默认使用 `dev` profile，开发配置已接入飞书 Webhook；部署环境可通过 `MYWORLD_NEWS_DIGEST_WEBHOOK_URL` 覆盖地址。生产环境使用 `prod` profile，不依赖 `local` profile。

默认在北京时间每天 08:00 执行。可用 `MYWORLD_NEWS_DIGEST_CRON` 和 `MYWORLD_NEWS_DIGEST_ZONE` 调整时间。Webhook 收到的 JSON 格式为：

```json
{
  "date": "2026-10-05",
  "title": "2026-10-05 36氪新闻日报",
  "content": "AI 摘要和原文链接"
}
```

当目标为飞书 `open.feishu.cn/open-apis/bot/v2/hook/...` 时，自动改用飞书文本消息格式：

```json
{
  "msg_type": "text",
  "content": {
    "text": "AI 摘要和原文链接"
  }
}
```

开发环境的飞书自定义关键词已配置为 `tudoubing`，发送时自动添加在消息正文开头。可通过 `MYWORLD_NEWS_DIGEST_FEISHU_KEYWORD` 覆盖；生产环境也通过此环境变量配置。关键词需要与飞书机器人的安全设置一致。

请求还带有 `X-Idempotency-Key: 36kr-digest-YYYY-MM-DD`。通用 Webhook 收到 2xx 响应视为成功；飞书还必须返回业务状态 `code=0`，HTTP 200 的业务失败不会记为已发送。飞书 JSON 请求体上限为 20 KB。日志记录 RSS 条数、筛选结果、AI 输入输出长度、推送渠道、HTTP 状态和飞书业务结果，不打印 Webhook 中的机器人凭据。

成功日期写入运行目录下的 `data/36kr-digest-last-sent.txt`，避免单实例重启后同日再次推送。多实例部署应改用共享的日期记录和锁。

## 真实链路集成测试

`Kr36NewsDigestIntegrationTest` 只使用 `@SpringBootTest` 和 `@Autowired`，直接调用 `DailyNewsDigestTask#run`。加载与应用启动相同的 `application.yml` 和当前 profile 配置，本地默认 `dev`，使用开发配置中的飞书 Webhook。测试进程使用与应用启动相同的 API Key 或配置解密主密钥。

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  mvn -pl my-world-app -am -Dtest=Kr36NewsDigestIntegrationTest test
```

通过 `SPRING_PROFILES_ACTIVE=prod` 可以使用生产配置。测试执行真实任务并使用当前环境的发送状态文件；同日已经成功推送时，任务会跳过。`run` 内部记录执行失败日志，需查看日志和飞书消息确认推送结果。

## 更换推送渠道

若目标渠道不接受上述 JSON，提供一个 Spring Bean 实现 [`NewsDigestDelivery`](../my-world-app/src/main/java/com/dingbang/myworld/news/kr36/service/NewsDigestDelivery.java) 即可。没有配置推送目标时，服务和预览接口仍可使用，但不会创建定时推送任务。具体的邮件、企业微信或钉钉适配器可在确定渠道后接入。

可通过 `my-world.news.digest.feed-urls`、`lookback`、`max-items`、`max-description-chars`、`max-prompt-chars` 和 `state-file` 调整抓取与总结范围。RSS 只提供标题及摘要时，AI 总结也只依据这些信息，不抓取付费或文章全文。抓取请求有超时和大小限制，且仅接受 36氪域名的 HTTPS RSS 地址与原文链接。
