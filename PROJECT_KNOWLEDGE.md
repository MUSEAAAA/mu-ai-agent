# mu-ai-agent 项目知识体系

> 基于 Spring AI Alibaba + DashScope 构建的智能 Agent 项目，涵盖 RAG、工具调用、多模态、MCP 协议等完整 AI 工程化能力。

---

## 目录

1. [项目架构总览](#1-项目架构总览)
2. [Spring AI 核心概念](#2-spring-ai-核心概念)
3. [Agent 架构深度解析](#3-agent-架构深度解析)
4. [RAG 检索增强生成](#4-rag-检索增强生成)
5. [工具调用系统](#5-工具调用系统)
6. [对话记忆（Chat Memory）](#6-对话记忆chat-memory)
7. [多模态图片理解](#7-多模态图片理解)
8. [Advisor 链与可观测性](#8-advisor-链与可观测性)
9. [MCP 协议集成](#9-mcp-协议集成)
10. [数据库与数据源设计](#10-数据库与数据源设计)
11. [配置体系与条件装配](#11-配置体系与条件装配)
12. [面试高频问题](#12-面试高频问题)

---

## 1. 项目架构总览

### 1.1 分层架构

```
[HTTP Request]
     │
     ▼
Controller 层 (AiController / ImgController)
     │
     ├── Agent 路径: /ai/manus/chat
     │     └── muManus (4层 Agent 继承链)
     │           ├── BaseAgent     — 基础循环框架
     │           ├── ReActAgent    — 思考-行动模式
     │           ├── ToolCallAgent — 工具调用执行
     │           └── muManus       — 具体业务 Agent
     │
     ├── 服务路径: /ai/knowledge_economy/chat
     │     └── KnowledgeEconomyService
     │           ├── Advisor 链 (Sensitive → Logger → Memory → RAG)
     │           ├── 工具调用 (@Tool 方法)
     │           └── MCP 工具 (ToolCallbackProvider)
     │
     └── 多模态路径: /ai/image/explain
           └── MultiModalConversationImpl
                 └── DashScope SDK (qwen-vl-plus)
```

### 1.2 请求处理流程（核心路径）

```
用户提问
  → AiController 接收请求
  → KnowledgeEconomyService.doChatWithRag()
    → MessageChatMemoryAdvisor 加载历史 (MySQL/内存)
    → SensitiveWordAdvisor 敏感词拦截
    → MyLoggerAdvisor 日志记录
    → QueryRewriter 改写模糊问题
    → VectorStoreDocumentRetriever 检索相关文档
    → LLM 生成回答 (qwen-plus)
  → 返回给用户
```

---

## 2. Spring AI 核心概念

### 2.1 关键接口梳理

| 接口/类 | 作用 | 本项目的实现/使用 |
|---|---|---|
| `ChatClient` | LLM 交互的统一 API | 两个实例：`chatClient`（普通）、`ragChatClient`（RAG） |
| `ChatModel` | LLM 模型抽象 | DashScope 提供的实现（`dashscopeChatModel`） |
| `ChatMemory` | 对话记忆 SPI | `InMemoryChatMemory`、`JdbcChatMemory` |
| `MessageChatMemoryAdvisor` | 自动注入记忆的 Advisor | 默认挂载到所有 ChatClient |
| `CallAroundAdvisor` | 同步调用拦截器 SPI | `SensitiveWordAdvisor`、`MyLoggerAdvisor` |
| `ToolCallback` | 工具描述接口 | 7 个 `@Tool` 方法 |
| `ToolCallingManager` | 工具调用生命周期管理 | `ToolCallAgent` 手动编排 |
| `VectorStore` | 向量存储 SPI | `SimpleVectorStore`（本地）、`PgVectorStore`（云端） |
| `RetrievalAugmentationAdvisor` | RAG 检索增强 Advisor | 动态创建 + 挂载 |
| `DocumentRetriever` | 文档检索 SPI | `VectorStoreDocumentRetriever` |
| `QueryTransformer` | 查询改写 SPI | `RewriteQueryTransformer` |
| `EmbeddingModel` | 向量化模型抽象 | DashScope 提供的实现 |
| `Document` | 文档数据模型 | 包含 text + metadata（Map） |

### 2.2 ChatClient 两种构建方式

```java
// 方式一：构造器注入（推荐）
this.chatClient = ChatClient.builder(dashscopeChatModel)
    .defaultAdvisors(
        new MessageChatMemoryAdvisor(chatMemory),
        new MyLoggerAdvisor(),
        new SensitiveWordAdvisor()
    )
    .build();

// 方式二：运行时动态添加 advisors
chatClient.prompt()
    .advisors(KnowledgeEconomyRagCustomAdvisorFactory.create(...))
    .call();
```

**面试点**：为什么用构造器注入而不是 `@Autowired`？
- ChatClient 需要绑定到具体 ChatModel，且每次调用可动态传参
- 构造器注入让依赖关系显式化，便于测试

---

## 3. Agent 架构深度解析

### 3.1 四层继承链

```
BaseAgent (abstract)
  │ 字段: name, description, systemPrompt, messageList, chatClient, maxSteps
  │ 方法: run() — 循环调用 step()
  │ 方法: runStream() — SSE 流式版本
  │
  └── ReActAgent (abstract)
        │ 方法: step() = think() → act()
        │
        └── ToolCallAgent (concrete)
              │ think(): LLM → 判断是否要调用工具
              │ act(): 执行工具 → 处理结果
              │ proxyToolCalls = true 关键配置
              │
              └── muManus (@Component)
                    具体业务 Agent，注入具体 tools
```

### 3.2 BaseAgent 核心循环

```java
public String run(String userPrompt) {
    state = AgentState.RUNNING;
    messageList.add(new UserMessage(userPrompt));
    try {
        while (currentStep < maxSteps && state == AgentState.RUNNING) {
            String result = step();
            // result 加入 messageList
            currentStep++;
        }
        state = AgentState.FINISHED;
    } catch (Exception e) {
        state = AgentState.ERROR;
    }
}
```

### 3.3 ToolCallAgent 关键机制

**proxyToolCalls = true 的意义：**

正常情况下，Spring AI 会在 `ChatClient.call()` 内部自动执行工具调用并返回最终文本。设置 `proxyToolCalls = true` 后，LLM 返回的 ToolCall 信息会保留在 ChatResponse 中，**由我们手动控制执行**。

```java
// think() 的核心逻辑
ChatResponse response = chatClient.prompt(prompt)
    .system(systemPrompt)
    .tools(availableTools)
    .call()
    .chatResponse();

List<AssistantMessage.ToolCall> toolCalls = 
    response.getResult().getOutput().getToolCalls();

if (toolCalls.isEmpty()) {
    state = FINISHED;  // Agent 思考完毕
    return false;
}

// act() 的核心逻辑
ToolExecutionResult result = toolCallingManager.executeToolCalls(
    prompt, response);
messageList = result.conversationHistory();
```

**面试点**：为什么需要手动编排工具调用而不是自动执行？
- 自动执行：`ChatClient.call()` → 自动调工具 → 返回文本（黑盒）
- 手动编排：可以控制"思考 → 行动 → 观察"的循环过程，在每个 step 之后决定下一步是继续调用工具还是结束
- 这是 ReAct 模式的核心：LLM 可以"边想边做"，而非一次调用完事

### 3.4 Agent 状态机

```
IDLE ──run()──▶ RUNNING ──step()──▶ RUNNING
                  │                    │
                  │ maxSteps 到达       │ 思考完成
                  ▼                    ▼
               ERROR               FINISHED
```

---

## 4. RAG 检索增强生成

### 4.1 完整 RAG Pipeline

```
[Markdown 文档]
      │
      ▼
KnowledgeEconomyDocumentLoader
  (PathMatchingResourcePatternResolver 扫描 classpath 文件)
  (MarkdownDocumentReader 解析，以 --- 分割章节)
      │
      ▼
MyTokenTextSplitter
  (TokenTextSplitter: chunkSize=200, overlap=100)
      │
      ▼
KeywordMetadataEnricher
  (LLM 自动提取 5 个关键词 → excerpt_keywords 元数据)
      │
      ▼
VectorStore (SimpleVectorStore / PgVectorStore)
  (Embedding 模型向量化 → 存储)
      │
      ▼ (用户查询时)
QueryRewriter (RewriteQueryTransformer)
  (将口语化/模糊问题转为精准检索词)
      │
      ▼
VectorStoreDocumentRetriever
  (向量检索 + FilterExpression 过滤)
  (similarityThreshold=0.8, topK=3)
      │
      ▼
RetrievalAugmentationAdvisor
  (将检索结果注入 Prompt 上下文)
      │
      ▼
LLM 生成回答
```

### 4.2 文档加载细节

```java
// 文件名规范: "xxx - X篇.md"
// 如: "国富论 - 基础篇.md", "资本论 - 基础篇.md"

// 加载方式：通配符扫描
Resource[] resources = resourcePatternResolver
    .getResources("classpath:document/*.md");

// Markdown 文档分割：以 --- (horizontal rule) 为界
// 一个文件可能被分割成多个 Document 对象
MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
    .withHorizontalRuleCreateDocument(true)  // --- 分割
    .withIncludeCodeBlock(false)
    .withIncludeBlockquote(false)
    .withAdditionalMetadata("status", "基础")
    .build();
```

### 4.3 查询改写（Query Rewriting）

```java
// 问题: "怎么赚钱？" → 改写为 "知识经济时代通过创新和知识变现的盈利途径"
// 问题: "AI 会取代人类吗" → 改写为 "人工智能对知识工作者就业结构的影响"

RewriteQueryTransformer transformer = RewriteQueryTransformer.builder()
    .chatClientBuilder(builder)
    .build();
```

**面试点**：为什么需要查询改写？
- 用户提问往往是模糊的、口语化的（"这个怎么看？"）
- 向量检索依赖语义相似度，模糊词直接检索效果差
- LLM 将模糊问题改写为领域相关精确描述后，向量检索命中率大幅提升

### 4.4 自定义 FilterExpression 过滤

```java
Filter.Expression expression = new FilterExpressionBuilder()
    .eq("status", status)  // 按文档分类过滤
    .build();

VectorStoreDocumentRetriever.builder()
    .vectorStore(vectorStore)
    .filterExpression(expression)
    .similarityThreshold(0.8)
    .topK(3)
    .build();
```

### 4.5 本地 vs 云端 RAG

| 维度 | 本地 RAG (SimpleVectorStore) | 云端 RAG (DashScope) |
|---|---|---|
| 向量存储 | JSON 文件（`java.io.tmpdir`） | DashScope 托管 |
| 检索实现 | `VectorStoreDocumentRetriever` | `DashScopeDocumentRetriever` |
| 优势 | 零成本、开发调试方便 | 无需自建向量库，开箱即用 |
| 开关 | `app.rag.local.enabled=true` | `app.rag.cloud.enabled=true` |

---

## 5. 工具调用系统

### 5.1 7 种工具一览

| 工具类 | 方法 | 功能 | 依赖 |
|---|---|---|---|
| `WebSearchTool` | `searchWeb(query)` | 调用 searchapi.io 搜索 | search-api.api-key |
| `WebScrapingTool` | `scrapeWebPage(url)` | Jsoup 抓取网页内容 | Jsoup |
| `FileOperationTool` | `readFile/writeFile` | 读写本地文件 | Hutool |
| `PDFGenerationTool` | `generatePDF(fileName, content)` | iText 生成 PDF 报告 | iText 9 |
| `ResourceDownloadTool` | `downloadResource(url, fileName)` | 下载网络资源 | Hutool |
| `TerminalOperationTool` | `executeTerminalCommand(cmd)` | 执行终端命令 | Runtime.exec |
| `TerminateTool` | `doTerminate()` | 结束 Agent 循环 | — |

### 5.2 工具注册机制

```java
@Configuration
public class ToolRegistration {
    @Bean
    public ToolCallback[] allTools(/* 7个工具注入 */) {
        return ToolCallbacks.from(
            webSearchTool, webScrapingTool,   // → ToolCallback
            resourceDownloadTool, fileOpTool,  // → ToolCallback
            terminalOpTool, pdfGenTool,        // → ToolCallback
            terminateTool                      // → ToolCallback
        );
    }
}
```

**`ToolCallbacks.from()` 的工作原理：**
1. 使用反射扫描对象上的 `@Tool` 注解
2. 解析 `@ToolParam` 获取参数描述
3. 为每个方法生成 `ToolCallback` 实例（包含名称、描述、输入 schema、执行逻辑）
4. 最终被 `ChatClient.prompt().tools(callbacks)` 消费

### 5.3 @Tool 注解规范

```java
@Component
public class WebSearchTool {
    @Tool(description = "搜索互联网信息，当你需要查找最新资讯时可以使用")
    public String searchWeb(
            @ToolParam(description = "搜索关键词") String query) {
        // 调用搜索 API 返回结果
    }
}
```

### 5.4 工具编排示例

当用户说"帮我搜索最新的 AI 新闻，生成一份 PDF 报告"，Agent 会自动：

```
Step 1: think() → 需要搜索 → act() → 调用 WebSearchTool
Step 2: think() → 需要抓取详情 → act() → 调用 WebScrapingTool
Step 3: think() → 需要生成 PDF → act() → 调用 PDFGenerationTool
Step 4: think() → 完成 → FINISHED
```

---

## 6. 对话记忆（Chat Memory）

### 6.1 三种实现对比

| 实现 | 存储方式 | 用途 |
|---|---|---|
| `InMemoryChatMemory` | 内存 Map | 开发调试、无数据库部署 |
| `JdbcChatMemory` | MySQL (chat_memory 表) | 生产持久化 |
| `FileBasedChatMemory` | Kryo 序列化文件 | 实验/参考实现 |

### 6.2 Spring AI ChatMemory 接口

```java
public interface ChatMemory {
    void add(String conversationId, Message message);
    void add(String conversationId, List<Message> messages);
    List<Message> get(String conversationId, int lastN);  // 取最近N条
    void clear(String conversationId);
}
```

### 6.3 记忆注入机制

```java
// 1. ChatClient 构建时挂载 Advisor
new MessageChatMemoryAdvisor(chatMemory)

// 2. 每次请求时传入参数
.advisors(spec -> spec
    .param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)    // 会话ID
    .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))          // 取最近10条
```

### 6.4 MySQL 表结构

```sql
CREATE TABLE chat_memory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id VARCHAR(255) NOT NULL,   -- 会话唯一标识
    message_type VARCHAR(50) NOT NULL,        -- USER/ASSISTANT/SYSTEM
    content TEXT,                              -- 消息内容
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 6.5 `@ConditionalOnMissingBean` 条件装配

```java
@Bean
@ConditionalOnMissingBean(ChatMemory.class)
public ChatMemory inMemoryChatMemory() {
    return new InMemoryChatMemory();
}
```

如果 `JdbcChatMemory` 没有创建（database 未启用），自动 fallback 到内存实现——确保不因为缺记忆组件而启动失败。

---

## 7. 多模态图片理解

### 7.1 为什么直接用 DashScope SDK？

**Spring AI Alibaba 1.0.0-M6.1 尚未提供多模态模型（如图片理解）的 ChatModel 抽象。** 因此直接调用 DashScope SDK 的 `MultiModalConversation` API。

### 7.2 调用链路

```java
// 1. 临时文件处理
File tempFile = File.createTempFile("upload-", ".jpg");
file.transferTo(tempFile);

// 2. 构建 file:// URI
String imagePath = "file:///" + tempFile.getAbsolutePath();

// 3. 构建多模态消息 (image + text)
MultiModalMessage userMessage = MultiModalMessage.builder()
    .role(Role.USER.getValue())
    .content(Arrays.asList(
        Map.of("image", imagePath),
        Map.of("text", question)
    ))
    .build();

// 4. 调用通义千问 VL 模型
MultiModalConversationParam param = MultiModalConversationParam.builder()
    .apiKey(apiKey)         // 必须显式传入
    .model("qwen-vl-plus")  // 支持图片理解的模型
    .messages(List.of(userMessage))
    .build();

MultiModalConversationResult result = new MultiModalConversation().call(param);
```

### 7.3 API Key 注入的坑（重要面试点）

DashScope SDK 内部有两个 key 读取路径：

| 路径 | 读取方式 | 适用场景 |
|---|---|---|
| `MultiModalConversationParam.apiKey` | 参数传入 | `conv.call(param)` 的直接调用 |
| `Constants.apiKey`（静态字段） | 全局变量 | `OSSUtils.getUploadCertificate()` 上传图片到 OSS 时的内部调用 |

**问题**：`MultiModalConversation.call()` 在内部上传图片到阿里云 OSS 时，`OSSUtils` 使用的是 `ApiKey.getApiKey(null)` → `Constants.apiKey`，**不读取参数中的 key**。

**解决方案**：启动时设置全局 key

```java
@PostConstruct
void initDashScopeApiKey() {
    String apiKey = System.getenv("DASHSCOPE_API_KEY");
    if (apiKey == null) apiKey = System.getenv("DASHSCOPEKEY");
    if (apiKey != null) Constants.apiKey = apiKey;
}
```

---

## 8. Advisor 链与可观测性

### 8.1 Advisor 执行顺序

```
Order 0:  SensitiveWordAdvisor  — 敏感词拦截（优先级最高）
Order 10: ReReadingAdvisor      — 查询重读（未启用，演示用）
Order 20: MyLoggerAdvisor       — 全链路日志
          MessageChatMemoryAdvisor — 记忆注入（内置）
          RetrievalAugmentationAdvisor — RAG 增强（动态挂载）
```

### 8.2 CallAroundAdvisor 接口

```java
public interface CallAroundAdvisor extends Ordered {
    // 拦截请求的核心方法
    AdvisedResponse aroundCall(
        AdvisedRequest advisedRequest,
        CallAroundAdvisorChain chain
    );

    // 获取执行顺序（值越小越优先）
    int getOrder();

    // Advisor 名称
    String getName();
}
```

### 8.3 SensitiveWordAdvisor 实现

```java
@Override
public AdvisedResponse aroundCall(AdvisedRequest advisedRequest,
                                   CallAroundAdvisorChain chain) {
    String userText = advisedRequest.userText();
    if (userText.contains("敏感词")) {
        throw new IllegalArgumentException("包含敏感词，拒绝处理");
    }
    return chain.nextAroundCall(advisedRequest);  // 放行
}

// 流式版本类似，使用 StreamAroundAdvisor
```

### 8.4 MyLoggerAdvisor 实现

```java
@Override
public AdvisedResponse aroundCall(AdvisedRequest advisedRequest,
                                   CallAroundAdvisorChain chain) {
    log.info("AI Request: {}", advisedRequest.userText());

    AdvisedResponse response = chain.nextAroundCall(advisedRequest);

    log.info("AI Response: {}",
        response.response().getResult().getOutput().getText());

    return response;
}
```

### 8.5 MessageAggregator（流式日志）

流式场景下，SSE 是逐 token 推送的，无法在第一个包就拿到完整回答。使用 `MessageAggregator` 聚合完整 response：

```java
@Override
public Flux<AdvisedResponse> aroundStream(AdvisedRequest advisedRequest,
                                           StreamAroundAdvisorChain chain) {
    Flux<AdvisedResponse> responses = chain.nextAroundStream(advisedRequest);
    return MessageAggregator.aggregateAdvisedResponse(responses,
        aggregated -> log.info("AI 完整回答: {}", aggregated.response()));
}
```

---

## 9. MCP 协议集成

### 9.1 什么是 MCP（Model Context Protocol）

MCP 是 Anthropic 提出的开放协议，本质是"AI 应用的 USB-C 接口"——让 LLM 通过标准化协议发现和调用外部工具。

### 9.2 本项目 MCP 架构

```
[mu-ai-agent (MCP Client)]
     │ 通过 stdio 协议通信
     │ mcp-servers.json 配置
     ▼
[mu-image-search-mcp-server (MCP Server)]
     │ 独立 Spring Boot 进程
     │ 注册 ImageSearchTool
     ▼
[Pexels API] — 图片搜索引擎
```

### 9.3 MCP 客户端配置

```json
// mcp-servers.json
{
  "mcpServers": {
    "image-search": {
      "command": "java",
      "args": [
        "-jar", "mu-image-search-mcp-server/target/mu-image-search-mcp-server-0.0.1-SNAPSHOT.jar",
        "--spring.ai.mcp.server.stdio=true",
        "--spring.main.web-application-type=none"
      ],
      "description": "图片搜索服务"
    }
  }
}
```

### 9.4 客户端集成要点

```yaml
# 启用 MCP 客户端
spring:
  ai:
    mcp:
      client:
        enabled: true          # 开启 MCP
        type: SYNC             # 同步模式
        stdio:
          servers-configuration: classpath:mcp-servers.json
```

### 9.5 服务端实现

```java
@Configuration
public class ToolConfiguration {
    @Bean
    public ToolCallbackProvider imageSearchTools(ImageSearchTool tool) {
        return MethodToolCallbackProvider.builder()
            .toolObjects(tool)
            .build();
    }
}
```

### 9.6 MCP vs 本地 @Tool

| 维度 | 本地 @Tool | MCP Tool |
|---|---|---|
| 进程 | 同一 JVM | 独立进程（stdio） |
| 通信 | 直接方法调用 | 标准协议（JSON-RPC） |
| 发现机制 | 反射扫描 | 协议协商 |
| 适用场景 | 基础工具 | 独立部署的外部能力 |

---

## 10. 数据库与数据源设计

### 10.1 双数据源架构

```
┌──────────────────────┐    ┌──────────────────────┐
│     MySQL (local)    │    │  PostgreSQL (cloud)   │
│                      │    │                      │
│  chat_memory 表      │    │  vector_store 表     │
│  对话记录持久化       │    │  向量数据存储        │
│  InnoDB, utf8mb4    │    │  HNSW 索引           │
└──────────────────────┘    └──────────────────────┘
        │                            │
        ▼                            ▼
  localJdbcTemplate           cloudJdbcTemplate
  @Qualifier("local")         @Primary + @Qualifier("cloud")
```

### 10.2 DataSourceConfig 要点

```java
@Configuration
@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true")
public class DataSourceConfig {

    // PostgreSQL（主数据源）
    @Primary
    @Bean("cloudDataSource")
    public DataSource cloudDataSource(
            @Value("${spring.datasource.cloud.url}") String url,
            @Value("${spring.datasource.cloud.username}") String user,
            @Value("${spring.datasource.cloud.password}") String pwd) {
        return DataSourceBuilder.create()
            .url(url).username(user).password(pwd)
            .type(HikariDataSource.class)
            .build();
    }

    // MySQL（本地）
    @Bean("localDataSource")
    public DataSource localDataSource(/* ... */) {
        // ...
    }
}
```

### 10.3 PGVector 向量存储

```java
@Bean
VectorStore pgVectorStore(
        @Qualifier("cloudJdbcTemplate") JdbcTemplate jdbcTemplate,
        EmbeddingModel embeddingModel) {

    return PgVectorStore.builder(jdbcTemplate, embeddingModel)
        .dimensions(1536)              // DashScope embedding 维度
        .distanceType(COSINE_DISTANCE)  // 余弦距离
        .indexType(HNSW)               // 高效近似搜索索引
        .initializeSchema(true)         // 自动建表
        .schemaName("public")
        .vectorTableName("vector_store")
        .maxDocumentBatchSize(10000)
        .build();
}
```

---

## 11. 配置体系与条件装配

### 11.1 多 Profile 配置

| Profile | 文件 | 适用场景 | 关键差异 |
|---|---|---|---|
| local（默认） | `application.yml` + `application-local.yml` | 本地开发 | MCP 启用，DB 直连 |
| prod | `application.yml` + `application-prod.yml` | Docker 部署 | MCP 禁用，key 从环境变量读取 |

### 11.2 完整 Feature Flag 清单

```yaml
# application-prod.yml
spring:
  ai:
    mcp:
      client:
        enabled: false           # 禁用 MCP（无 MCP 服务部署）
    dashscope:
      api-key: ${DASHSCOPEKEY}   # 环境变量注入

app:
  rag:
    local:
      enabled: true              # 启用本地 RAG
    cloud:
      enabled: false             # 禁用云端 RAG
  database:
    enabled: false               # 禁用数据库（无 DB 环境用内存模式）
```

### 11.3 `@ConditionalOnProperty` 原理

```java
@Configuration
@ConditionalOnProperty(name = "app.rag.local.enabled", havingValue = "true")
public class KnowledgeEconomyVectorStoreConfig {
    // 只有当 app.rag.local.enabled=true 时才会创建这些 Bean
}
```

**Spring 条件装配机制对比：**

| 注解 | 条件 |
|---|---|
| `@ConditionalOnProperty` | 配置属性匹配 |
| `@ConditionalOnMissingBean` | 指定 Bean 不存在 |
| `@ConditionalOnClass` | 指定类在 classpath |
| `@ConditionalOnExpression` | SpEL 表达式 |

### 11.4 排除自动配置

```java
@SpringBootApplication(
    exclude = DataSourceAutoConfiguration.class,        // 手动管理数据源
    excludeName = "org.springframework.ai.autoconfigure.vectorstore.pgvector.PgVectorStoreAutoConfiguration"
    // 手动创建 PgVectorStore，避免冲突
)
```

---

## 12. 面试高频问题

### 12.1 项目相关

**Q: 你们这个 Agent 和直接调 API 有什么区别？**
> 直接调 API 只能一问一答。我们的 Agent 有四层架构，可以实现"思考→行动→观察"的循环——比如先搜索资料、再分析、再生成报告，整个过程自动编排。

**Q: RAG 的流程是怎样的？为什么效果提升了 40%？**
> 标准 RAG：文档切块 → 向量化 → 检索 → 生成。我们在这个基础上加了两个关键优化：1）查询改写，把"怎么赚钱"这种模糊问题改写为领域相关精确描述；2）关键词 AI 标注，在入库时让 LLM 自动提取关键词辅助检索。测试50组模糊问题，命中率从45%提到85%。

**Q: 为什么有本地和云端两套 RAG？**
> 本地用 SimpleVectorStore 基于内存，开发调试方便且免费。云端用 DashScope 托管的 RAG，适合生产。通过配置开关可以随时切换，互不影响。

**Q: 双数据源的设计考虑是什么？**
> MySQL 存对话记录（结构化数据），PostgreSQL + PGVector 存向量数据。分开的原因：1）两类数据访问模式不同（行式 vs 向量检索）；2）性能隔离，向量检索不会影响对话读写；3）为后续分布式扩展做准备。

### 12.2 技术深度

**Q: Spring AI 的 Advisor 链和拦截器有什么区别？**
> 类似 Servlet Filter / AOP 环绕通知。Advisor 可以：修改用户输入（重读问题）、拦截请求（敏感词）、记录日志、注入上下文（记忆）。通过 `getOrder()` 控制顺序。和 Spring AOP 的区别是 Advisor 感知的是 AI 请求的语义（用户文本、消息历史），而非方法调用的技术细节。

**Q: proxyToolCalls=true 有什么用？**
> 控制工具调用的执行权。默认 Spring AI 自动调工具并返回文本（黑盒）。手动模式（proxy=true）下，我们拿到 LLM 想调的工具列表，自己决定执行时机，实现"思考→行动→观察"的 ReAct 循环。

**Q: 你们是怎么做到让 LLM 调用工具的？**
> Spring AI 的 `@Tool` 注解标注方法，`ToolCallbacks.from()` 扫描生成 ToolCallback（包含方法名、描述、参数 Schema）。调用时传给 ChatClient，LLM 根据工具描述自主决策是否调用。返回结果中包含 ToolCall 信息，我们解析后执行。

**Q: 聊天的历史记录是如何工作的？**
> Spring AI 的 `ChatMemory` 定义 `add/get/clear` 接口，有多种实现。`MessageChatMemoryAdvisor` 在每次请求前自动从 ChatMemory 加载最近 N 条消息拼入 Prompt，请求后再将新消息保存。整个过程对业务代码透明。

### 12.3 部署与运维

**Q: Docker 部署遇到过什么问题？**
> 1）中文文件名在 Linux JAR 里读取路径方式不同（classpath: 通配符扫描 vs ClassPathResource）；2）MCP 自动配置在没有 MCP 服务时会超时导致启动失败，需要在 prod profile 显式禁用；3）DashScope SDK 内部读取 API key 的方式和 Spring AI 不一样，需要额外设置 Constants.apiKey。

**Q: 环境变量命名有什么坑？**
> 微信云托管不支持带下划线的环境变量名（如 DASHSCOPE_API_KEY 报错），改成了 DASHSCOPEKEY。但 DashScope SDK 内部硬编码读 DASHSCOPE_API_KEY，需要在代码里 fallback 处理。

---

## 设计模式总结

| 模式 | 使用位置 | 说明 |
|---|---|---|
| **模板方法** | `BaseAgent.run()` → `step()` | 父类定义算法骨架，子类实现具体步骤 |
| **责任链** | Advisor 链 | 多个 Advisor 按序处理请求，每个决定是否放行 |
| **工厂方法** | `KnowledgeEconomyRagCustomAdvisorFactory` | 封装 Advisor 的创建逻辑，隐藏构建细节 |
| **策略模式** | `ChatMemory` 接口 + 多种实现 | 同一接口不同策略（内存/MySQL/文件） |
| **条件装配** | 全局 Feature Flag | 通过配置控制哪些 Bean 生效 |
| **代理模式** | ToolCallingManager | 代理 LLM 的工具调用请求，手动控制执行 |
