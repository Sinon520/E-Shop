# E-Shop AI 客服服务

> P5 阶段。设计与取舍见 `docs/ai-customer-service.md`。
> 当前进度：**P5-1 链路打通**（单轮对话 + 规则降级），流式 / RAG / 工具调用在后续阶段。

## 技术栈

FastAPI + LangChain 1.3 + LangGraph 1.2 + 智谱 GLM-4-Flash（可切 DeepSeek / Ollama）。
版本矩阵：`docs/version-matrix.md` 第六节。

## 启动

```powershell
# 1. 准备配置（只需一次）
Copy-Item .env.example .env
#    如需真实模型，把 AI_CLOUD_API_KEY 填上；不填则自动走规则兜底

# 2. 启动（默认 8087，带热重载）
.\scripts\start-ai.ps1
```

服务地址：`http://127.0.0.1:8087`
经网关访问：`http://127.0.0.1:9000/api/ai/chat`（需先启动网关）

## 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/health` | 健康检查，返回模型通道与会话存储类型 |
| POST | `/chat` | 非流式对话（脚本 / 降级用） |
| POST | `/chat/stream` | **SSE 流式对话**（P5-2） |

### SSE 事件协议

每个事件形如 `data: {...}\n\n`：

| type | 载荷 | 说明 |
|---|---|---|
| `meta` | `{sessionId, provider, checkpointer, historyCount}` | 流的第一个事件 |
| `status` | `{text}` | 节点中间态，如「正在查阅售后规则…」 |
| `delta` | `{text}` | token 增量 |
| `done` | `{answer, intent, provider, sources, historyCount, links}` | 收尾，带完整回答与跳转链接 |
| `error` | `{message}` | 生成失败兜底 |

前端用 `fetch` + `ReadableStream` 解析（`EventSource` 只支持 GET，无法带请求体）。

请求：

```json
{ "message": "七天无理由怎么算？", "sessionId": "可选，缺省由服务端生成" }
```

响应（与 Java 侧同构）：

```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "sessionId": "guest-xxxxxxxx",
    "answer": "支持 7 天无理由退货……",
    "intent": "policy",
    "provider": "rules",
    "loggedIn": false,
    "sources": [{ "type": "rule", "key": "return-policy" }]
  }
}
```

## 知识库（RAG）

原文在 `data/kb/*.md`，灌库脚本会切块 → 向量化 → 写入 ES（索引 `eshop_kb_embedding_3`，dims=2048，cosine）。

```powershell
# 首次或文档变更后执行（需要 ES 已启动 + 配好 AI_CLOUD_API_KEY）
python -m app.kb.ingest            # 增量：按 chunk_id 覆盖
python -m app.kb.ingest --rebuild  # 重建：删索引后重新灌
```

前置：Elasticsearch 8.18 需带 IK 插件（缺失会自动降级 standard 分词）。

检索是混合召回：向量（0.7）+ BM25（0.3），两路各自归一化后加权融合。
BM25 侧会先做疑问词归一化（剔除「怎么办 / 可以 / 吗」等），否则 FAQ 类文档会因为虚词被过度匹配。

## 工具调用（P5-4）

| 工具 | 目标 | 需要登录 |
|---|---|---|
| `query_my_orders` | `order:8085/page` | 是 |
| `get_order_detail` | `order:8085/{orderNo}` | 是 |
| `search_products` | `product:8082/spu/search` | 否 |
| `check_stock` | `stock:8083/{skuId}` | 否 |

**身份红线**：`userId` 由 `build_tools()` 闭包捕获，工具参数 schema 里没有这个字段——模型只能决定查什么，不能决定查谁。未登录时订单类工具根本不会被绑定。

**直连而非走网关**：AI 服务只有 `X-User-Id`、没有用户 JWT，绕回网关必然 401。
注意各 Java 服务的控制器**没有类级前缀**（网关已 `StripPrefix=2` 去掉 `/api/order`），直连要写 `/page`、`/{orderNo}`、`/{skuId}`。

依赖：订单工具需要 `eshop-order` 在跑，而 order 服务启动强依赖 RocketMQ（namesrv + broker），没起会直接启动失败。

**跳转链接**：工具在返回文本的同时，把 `{label, url, kind}` 收进侧通道（`build_tools(..., sink)`），随 `done.links` 下发。
URL 一律由后端按真实 id 拼接，**不让模型生成**；前端渲染成按钮，点击即跳转。

## 评测

```powershell
python -m eval.run_eval              # 30 条用例，输出意图/工具/关键词三项准确率
python -m eval.run_eval --cat policy # 只跑某一类
```

通过标准：意图 ≥85%、工具路由 ≥90%、关键词 ≥85%。当前实测 100% / 100% / 96.7%，平均 4.76s。
配套冒烟脚本：`scripts/smoke-test-ai.ps1`（28 项，含限流与越权拦截）。

## 模型通道

`AI_PROVIDER` 控制：

| 取值 | 行为 |
|---|---|
| `cloud` | 强制云端，失败直接降级规则 |
| `local` | 强制本地 Ollama |
| `rules` | 不调用模型，纯关键词 FAQ |
| `auto` | 有云端 key → 云端；否则探测 Ollama；都不行 → 规则 |

未配置 Key 时 `provider` 返回 `rules`，功能可演示、不报错——保证 clone 仓库即可运行。

## 身份约定

`user_id` 只从网关 `X-User-Id` 请求头读取，**不接受请求体传入，也不会写进提示词**。
未登录时该值为空，后续版本据此裁剪订单类工具。
