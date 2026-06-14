# mu-ai-agent 项目文档

> 基于 Spring AI Alibaba + DashScope(通义千问) 的恋爱咨询 AI 助手

---

## 目录

1. [项目架构总览](#1-项目架构总览)
2. [配置层：DataSourceConfig](#2-配置层-datasourceconfig)
3. [配置层：application-local.yml](#3-配置层-application-localyml)
4. [AI 对话核心：LoveApp](#4-ai-对话核心-loveapp)
5. [Prompt 模板加载：PromptLoader](#5-prompt-模板加载-promptloader)
6. [聊天记忆：JdbcChatMemory](#6-聊天记忆-jdbcchatmemory)
7. [RAG 文档加载：LoveAppDocumentLoader](#7-rag-文档加载-loveappdocumentloader)
8. [向量库配置：LoveAppVectorStoreConfig](#8-向量库配置-loveappvectorstoreconfig)
9. [向量库配置：PgVectorVectorStoreConfig](#9-向量库配置-pgvectorvectorstoreconfig)
10. [RAG Advisor 工厂：LoveAppRagCustomAdvisorFactory](#10-rag-advisor-工厂-loveappragcustomadvisorfactory)
11. [查询重写：QueryRewriter](#11-查询重写-queryrewriter)
12. [阿里云知识库（备用）：LoveAppRagCloudeAdvisorConfig](#12-阿里云知识库备用-loveappragcloudeadvisorconfig)
13. [自定义 Advisor：MyLoggerAdvisor / SensitiveWordAdvisor / ReReadingAdvisor](#13-自定义-advisor)
14. [多模态图片理解](#14-多模态图片理解)
15. [Controller 层](#15-controller-层)
16. [完整调用流程](#16-完整调用流程)

---

## 1. 项目架构总览

```
┌─────────────────────────────────────────────────────────┐
│                    Controller 层                         │
│     HealthController  /  ImgController                   │
└──────────────────────┬──────────────────────────────────┘
                       │ HTTP 请求
┌──────────────────────▼──────────────────────────────────┐
│                    LoveApp (核心业务类)                    │
│                                                          │
│  ┌─────────────┐    ┌──────────────┐                    │
│  │ chatClient  │    │ ragChatClient│                    │
│  │ (普通对话)   │    │ (RAG对话)    │                    │
│  └──────┬──────┘    └──────┬───────┘                    │
│         │                  │                             │
│         ▼                  ▼                             │
│  Advisor 链          Advisor 链                          │
│  ┌──────────┐       ┌───────────┐                       │
│  │ChatMemory│       │ChatMemory │                       │
│  │MyLogger  │       │MyLogger   │                       │
│  │Sensitive │       │Sensitive  │                       │
│  └──────────┘       │RAG Advisor│ ← 工厂创建             │
│                      └───────────┘                       │
└──────────────────────┬──────────────────────────────────┘
                       │
          ┌────────────┼────────────┐
          ▼            ▼            ▼
   ┌──────────┐ ┌──────────┐ ┌──────────┐
   │ MySQL    │ │DashScope │ │ PGVector │
   │ 聊天记忆  │ │ 大模型   │ │ 向量库    │
   └──────────┘ └──────────┘ └──────────┘
```

### 项目核心功能

| 功能 | 说明 |
|------|------|
| 多轮对话 | 支持上下文记忆的 AI 聊天 |
| 结构化输出 | AI 返回 Java 对象（恋爱报告） |
| RAG 检索增强 | 从知识库检索相关文档辅助回答 |
| 多模态理解 | 上传图片并提问 |
| 敏感词过滤 | 拦截包含敏感词的输入 |
| 查询重写 | 模糊问题自动改写后检索 |

---

## 2. 配置层：DataSourceConfig

**文件**：`src/main/java/com/muse/muaiagent/config/DataSourceConfig.java`

### 作用

项目中需要两个数据库：MySQL（存聊天记录）+ PostgreSQL（存向量），Spring 需要知道哪个数据源用在什么地方。这个类就是注册两个独立的数据源，并打上标签区分。

### 配置结构

```
spring.datasource.cloud  →  PostgreSQL（@Primary 默认）
spring.datasource.local  →  MySQL（用 @Qualifier 指定）
```

### 代码解析

```java
@Primary  // 标记为默认数据源，不指定 @Qualifier 时就用这个
@Bean(name = "cloudDataSource")
public HikariDataSource cloudDataSource(...) { ... }

@Bean(name = "cloudJdbcTemplate")
public JdbcTemplate cloudJdbcTemplate(...) { ... }
```

```java
// 没有 @Primary，必须用 @Qualifier("localXxx") 才能注入
@Bean(name = "localDataSource")
public HikariDataSource localDataSource(...) { ... }

@Bean(name = "localJdbcTemplate")
public JdbcTemplate localJdbcTemplate(...) { ... }
```

### 调用时需要什么

- **Cloud (PGVector)**：`@Qualifier("cloudJdbcTemplate") JdbcTemplate`
- **Local (MySQL)**：`@Qualifier("localJdbcTemplate") JdbcTemplate`

### 为什么这样设计

一个应用连接两个数据库，如果只有一个 `JdbcTemplate` Bean，Spring 不知道你要用哪个。所以：
- Cloud 加 `@Primary` → 不指定时默认用这个
- Local 命名不同 → 用 `@Qualifier("localJdbcTemplate")` 显式指定

---

## 3. 配置层：application-local.yml

**文件**：`src/main/resources/application-local.yml`

### 作用

所有敏感配置（数据库地址、API Key）都在这里。`application.yml` 里通过 `spring.profiles.active: local` 激活它。

### 配置项说明

```yaml
spring:
  datasource:
    cloud:                          # → 注入 DataSourceConfig.cloudDataSourceProperties()
      url: jdbc:postgresql://...    # 阿里云 RDS PostgreSQL
      username: my_user
      password: xxx

    local:                          # → 注入 DataSourceConfig.localDataSourceProperties()
      url: jdbc:mysql://localhost:3306/db08
      username: root
      password: 1234

  ai:
    dashscope:
      api-key: sk-xxx              # DashScope API 密钥
      chat:
        options:
          model: qwen-plus          # 使用的通义千问模型

    vectorstore:
      pgvector:
        index-type: HNSW            # 向量索引类型
        dimensions: 1536            # 向量维度
        distance-type: COSINE_DISTANCE  # 余弦距离
```

---

## 4. AI 对话核心：LoveApp

**文件**：`src/main/java/com/muse/muaiagent/app/LoveApp.java`

### 作用

项目的核心业务类，对外提供三个对话方法。

### 构造方法

```java
public LoveApp(ChatModel dashscopeChatModel, ChatMemory chatMemory) {
```

**参数**：
| 参数 | 类型 | 来源 | 作用 |
|------|------|------|------|
| `dashscopeChatModel` | `ChatModel` | Spring 自动注入（DashScope） | 调用通义千问大模型 |
| `chatMemory` | `ChatMemory` | Spring 自动注入（JdbcChatMemory） | 读写聊天历史 |

**内部创建了两个 ChatClient**：

```java
// 1. 普通对话客户端
this.chatClient = ChatClient.builder(dashscopeChatModel)
    .defaultAdvisors(
        new MessageChatMemoryAdvisor(chatMemory),  // 聊天记忆
        new MyLoggerAdvisor(),                      // 日志打印
        new SensitiveWordAdvisor()                  // 敏感词拦截
    )
    .build();

// 2. RAG 对话客户端（默认也是一样的 Advisor，后续动态添加 RAG）
this.ragChatClient = ChatClient.builder(dashscopeChatModel)
    .defaultAdvisors(/* 同上 */)
    .build();
```

### 方法 1：doChat — 基础多轮对话

```java
public String doChat(String message, String chatId)
```

| 参数 | 类型 | 说明 |
|------|------|------|
| `message` | `String` | 用户输入的消息 |
| `chatId` | `String` | 会话 ID，同一 ID 共享聊天记忆 |

**执行流程**：
```
doChat("我失恋了", "session-001")
  ↓
chatClient.prompt()
  .system(systemPrompt)                    → 加载恋爱专家提示词
  .user("我失恋了")                         → 用户输入
  .advisors(chatId, retrieveSize=10)       → 读取最近 10 条聊天记录
  .call()                                  → 调用 AI
  ↓
返回 AI 回复文本
```

### 方法 2：doChatWithReport — 结构化输出

```java
public LoveReport doChatWithReport(String message, String chatId)
```

```java
// 返回值类型
record LoveReport(String title, List<String> suggestions) { }
```

| 参数 | 类型 | 说明 |
|------|------|------|
| `message` | `String` | 用户输入 |
| `chatId` | `String` | 会话 ID |

**不同点**：追加了一段 Prompt 要求 AI 按固定格式返回，然后 `.entity(LoveReport.class)` 让 Spring AI 自动把 AI 的 JSON 输出映射成 Java 对象。

### 方法 3：doChatWitchRag — RAG 检索增强对话

```java
public String doChatWitchRag(String message, String chatId)
```

| 参数 | 类型 | 说明 |
|------|------|------|
| `message` | `String` | 用户输入 |
| `chatId` | `String` | 会话 ID |

**关键代码**：
```java
.advisors(LoveAppRagCustomAdvisorFactory.createLoveAppRagCustomAdvisor(
    loveAppVectorStore,              // 向量库（SimpleVectorStore）
    "已婚",                          // 分类过滤
    queryRewriter.getQueryTransformer()  // 查询改写器
))
```

**执行流程**：
```
doChatWitchRag("婚后关系不和谐", "session-002")
  ↓
ragChatClient.prompt() 开始构建请求
  ↓
Advisor 链依次执行：
  1. MessageChatMemoryAdvisor → 读取聊天历史
  2. MyLoggerAdvisor → 打印日志
  3. SensitiveWordAdvisor → 检查敏感词
  4. RetrievalAugmentationAdvisor → 【RAG 核心】
     a. QueryTransformer 改写问题（"婚后关系不和谐" → "已婚...")
     b. VectorStoreDocumentRetriever 去向量库检索
        - 按 category="已婚" 过滤
        - 取 topK=3 条最相似的文档
        - 相似度必须 ≥ 0.8
     c. 把检索到的文档拼进 Prompt
  ↓
发送给 DashScope 大模型
  ↓
返回 AI 回答
```

---

## 5. Prompt 模板加载：PromptLoader

**文件**：`src/main/java/com/muse/muaiagent/prompt/PromptLoader.java`

### 作用

从 `resources/prompts/` 下读取 `.st` 模板文件，替换其中的 `{变量名}` 为实际值。

### 调用方式

```java
String prompt = PromptLoader.loadPrompt("prompts/love-system-prompt.st", Map.of(
    "username", "小白",
    "relationshipStatus", "单身",
    "scene", "恋爱咨询"
));
```

**参数**：
| 参数 | 类型 | 说明 |
|------|------|------|
| `path` | `String` | classpath 下的文件路径 |
| `variables` | `Map<String, Object>` | 要替换的变量键值对 |

### 模板文件 (`love-system-prompt.st`)

```
你是一名深耕恋爱心理领域的专家。

你的用户名称是：{username}
当前用户情感状态是：{relationshipStatus}
当前对话场景是：{scene}
...
```

---

## 6. 聊天记忆：JdbcChatMemory

**文件**：`src/main/java/com/muse/muaiagent/cheatmemory/JdbcChatMemory.java`

### 作用

实现 Spring AI 的 `ChatMemory` 接口，把聊天记录持久化到 MySQL 数据库。应用重启后聊天历史不会丢。

### 实现的接口方法

```java
// Spring AI 的 ChatMemory 接口要求实现这三个方法

void add(String conversationId, List<Message> messages)
// → INSERT INTO chat_memory(conversation_id, message_type, content)

List<Message> get(String conversationId, int lastN)
// → SELECT ... WHERE conversation_id=? ORDER BY id DESC LIMIT lastN

void clear(String conversationId)
// → DELETE FROM chat_memory WHERE conversation_id=?
```

### 数据库表结构（自动创建）

```sql
CREATE TABLE IF NOT EXISTS chat_memory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id VARCHAR(255) NOT NULL,  -- 会话 ID
    message_type VARCHAR(50) NOT NULL,      -- USER / ASSISTANT / SYSTEM
    content TEXT,                            -- 消息内容
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 调用时需要什么

Spring 自动注入，无需手动传参。构造方法中：

```java
public JdbcChatMemory(@Qualifier("localJdbcTemplate") JdbcTemplate localJdbcTemplate)
```

要求容器中有一个名为 `localJdbcTemplate` 的 Bean（连接 MySQL）。

### toMessage() — 数据库记录还原为 Message 对象

```java
private Message toMessage(String messageType, String content)
// "USER" → UserMessage
// "ASSISTANT" → AssistantMessage
// "SYSTEM" → SystemMessage
```

---

## 7. RAG 文档加载：LoveAppDocumentLoader

**文件**：`src/main/java/com/muse/muaiagent/rag/LoveAppDocumentLoader.java`

### 作用

从 `classpath:document/*.md` 读取所有 Markdown 文件，解析成 Spring AI 的 `Document` 对象列表，并附上元数据。

### 调用方式

```java
@Resource
private LoveAppDocumentLoader loveAppDocumentLoader;

List<Document> docs = loveAppDocumentLoader.loadMarkdowns();
```

### 返回的 Document 结构

每个 Document 包含：

```
{
  content: "在社交场合，首先要保持微笑...",
  metadata: {
    "filename": "恋爱常见问题和回答 - 单身篇.md",
    "status": "单身"    ← 从文件名自动提取
  }
}
```

### 文件名 → 分类规则

| 文件名 | status 元数据 |
|--------|-------------|
| `恋爱常见问题和回答 - 单身篇.md` | `"单身"` |
| `恋爱常见问题和回答 - 恋爱篇.md` | `"恋爱"` |
| `恋爱常见问题和回答 - 已婚篇.md` | `"已婚"` |

---

## 8. 向量库配置：LoveAppVectorStoreConfig

**文件**：`src/main/java/com/muse/muaiagent/rag/LoveAppVectorStoreConfig.java`

### 作用

创建 `SimpleVectorStore`（内存向量库），启动时自动加载文档并转成向量。

### 代码

```java
@Bean
VectorStore loveAppVectorStore(EmbeddingModel dashscopeEmbeddingModel) {
    SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel).build();
    List<Document> documents = loveAppDocumentLoader.loadMarkdowns();  // 读文档
    simpleVectorStore.add(documents);                                  // 转向量 + 存储
    return simpleVectorStore;
}
```

### 调用时需要什么

其他类通过 `@Resource` 或 `@Autowired` 注入即可：

```java
@Resource
private VectorStore loveAppVectorStore;
```

### 特点

| 特性 | 说明 |
|------|------|
| 存储位置 | JVM 内存 |
| 持久化 | ❌ 重启后数据丢失 |
| 速度 | 极快（无网络 IO）|
| 适用场景 | 开发测试 / 练习 |

---

## 9. 向量库配置：PgVectorVectorStoreConfig

**文件**：`src/main/java/com/muse/muaiagent/rag/PgVectorVectorStoreConfig.java`

### 作用

创建基于 PostgreSQL + PGVector 扩展的向量库。生产环境使用。

### 代码

```java
@Bean
public VectorStore pgVectorVectorStore(
    @Qualifier("cloudJdbcTemplate") JdbcTemplate jdbcTemplate,  // 连接 PostgreSQL
    EmbeddingModel dashscopeEmbeddingModel                       // 向量化模型
) {
    return PgVectorStore.builder(jdbcTemplate, dashscopeEmbeddingModel)
            .dimensions(1536)
            .distanceType(COSINE_DISTANCE)
            .indexType(HNSW)
            .initializeSchema(true)
            .vectorTableName("vector_store")
            .maxDocumentBatchSize(10000)
            .build();
}
```

### 配置参数

| 参数 | 值 | 说明 |
|------|-----|------|
| `dimensions` | 1536 | 向量维度（需与 Embedding 模型一致） |
| `distanceType` | COSINE_DISTANCE | 余弦相似度 |
| `indexType` | HNSW | 高性能向量索引 |
| `initializeSchema` | true | 首次启动自动建表 |
| `vectorTableName` | vector_store | 数据库中向量表名 |

### 调用时需要什么

```java
@Resource
private VectorStore pgVectorVectorStore;  // 字段名匹配方法名
// 或
@Qualifier("pgVectorVectorStore")
@Resource
private VectorStore vectorStore;
```

### 当前状态

文档加载被注释掉了，需要手动写入数据（比如通过测试类写入）。

---

## 10. RAG Advisor 工厂：LoveAppRagCustomAdvisorFactory

**文件**：`src/main/java/com/muse/muaiagent/rag/LoveAppRagCustomAdvisorFactory.java`

### 作用

根据不同的分类条件，动态创建 `RetrievalAugmentationAdvisor`（RAG 拦截器）。实现笔记中的"工厂模式"。

### 调用方式

```java
Advisor advisor = LoveAppRagCustomAdvisorFactory.createLoveAppRagCustomAdvisor(
    vectorStore,      // 向量库（从哪个库检索）
    "已婚",           // 分类（只查 category="已婚" 的文档）
    queryTransformer  // 查询改写器（检索前改写问题）
);
```

**参数**：
| 参数 | 类型 | 说明 |
|------|------|------|
| `vectorStore` | `VectorStore` | 向量库实例 |
| `status` | `String` | 分类过滤值（单身/恋爱/已婚） |
| `queryTransformer` | `QueryTransformer` | 查询改写器 |

### 返回的 Advisor 做了哪些事

```
用户问题进来
  ↓
1. QueryTransformer 改写问题（让问题更清晰）
  ↓
2. VectorStoreDocumentRetriever 检索：
   - 按 status 过滤（只查对应分类的文档）
   - 相似度阈值 0.8（低于 0.8 的不返回）
   - topK = 3（最多返回 3 条）
  ↓
3. 把检索到的文档拼进 Prompt
  ↓
交给 AI 生成回答
```

---

## 11. 查询重写：QueryRewriter

**文件**：`src/main/java/com/muse/muaiagent/rag/QueryRewriter.java`

### 作用

利用 AI 把模糊的用户问题改写成更清晰、更适合检索的形式。

### 改造效果

```
输入："这个怎么办"
  ↓ 调用 DashScope AI 改写
输出："在恋爱中遇到矛盾和冲突应该如何处理和沟通？"
```

### 调用方式

```java
@Resource
private QueryRewriter queryRewriter;

// 方式一：直接获取改写后的文本
String rewritten = queryRewriter.doQueryRewrite("这个怎么办");

// 方式二：获取 QueryTransformer 对象（用于集成到 Advisor）
QueryTransformer transformer = queryRewriter.getQueryTransformer();
```

### 实现原理

`RewriteQueryTransformer` 内部调用 AI 做改写：

```
改写 Prompt（框架自动构造）：
"请将以下查询改写得更适合文档检索：[用户原始问题]"
  ↓
DashScope 模型返回改写后的问题
  ↓
包装成 Query 对象返回
```

---

## 12. 阿里云知识库（备用）：LoveAppRagCloudeAdvisorConfig

**文件**：`src/main/java/com/muse/muaiagent/rag/LoveAppRagCloudeAdvisorConfig.java`

### 作用

使用阿里云托管的知识库（DashScope RAG）作为检索源。当前被注释未启用，因为需要额外付费。

### 代码

```java
DashScopeApi dashScopeApi = new DashScopeApi(apiKey);
DocumentRetriever retriever = new DashScopeDocumentRetriever(
    dashScopeApi,
    DashScopeDocumentRetrieverOptions.builder()
        .withIndexName("恋爱大师")  // 阿里云知识库的索引名
        .build()
);
```

### 与自定义 RAG 的区别

| 方式 | 存储 | 检索 | 费用 |
|------|------|------|------|
| 阿里云知识库 | 阿里云托管 | 阿里云 API | 按量付费 |
| PGVector + 工厂 | 自建 PostgreSQL | 自己控制参数 | 仅服务器成本 |

---

## 13. 自定义 Advisor

### MyLoggerAdvisor

**文件**：`src/main/java/com/muse/muaiagent/advisor/MyLoggerAdvisor.java`

**作用**：在每次 AI 调用前后，打印用户输入和 AI 回复的日志。

```java
请求时 → log.info("AI Request: {}", request.userText());
响应后 → log.info("AI response: {}", response);
```

**顺序**：`getOrder() = 20`（在敏感词拦截之后执行）

### SensitiveWordAdvisor

**文件**：`src/main/java/com/muse/muaiagent/advisor/SensitiveWordAdvisor.java`

**作用**：如果用户输入包含"敏感词"则抛出异常，阻止发送给 AI。

```java
if (text.contains("敏感词")) {
    throw new IllegalArgumentException("输入内容包含敏感词");
}
```

**注意**：当前只拦截了字面量"敏感词"，实际使用可以扩展为敏感词列表或正则。

### ReReadingAdvisor（未启用）

**文件**：`src/main/java/com/muse/muaiagent/advisor/ReReadingAdvisor.java`

**作用**：要求 AI 把问题读两遍再回答，提高推理质量。

```java
// 改写后的 Prompt：
"{re2_input_query}
 Read the question again: {re2_input_query}"
```

当前被注释掉，可在需要深度分析的场景启用。

---

## 14. 多模态图片理解

### 接口

```java
// MultiModalConversationCall
String imgCall(MultipartFile file, String question) throws ...;
```

### 实现（MultiModalConversationImpl）

**流程**：
```
上传图片 + 问题
  ↓
保存为临时文件
  ↓
构造 MultiModalMessage（图片路径 + 问题文本）
  ↓
调用 DashScope qwen-vl-plus 多模态模型
  ↓
返回识别结果
  ↓
删除临时文件
```

### Controller 端点

```
POST /api/image/explain
  Content-Type: multipart/form-data
  Parameters:
    - file: 图片文件（MultipartFile）
    - question: 问题（可选，默认"请解释这张图片"）
```

---

## 15. Controller 层

### HealthController

```java
GET /api/health/health → "ok"
```

简单的健康检查接口。

### ImgController

```java
POST /api/image/explain → 图片理解
```

---

## 16. 完整调用流程

### 普通对话流程

```
用户请求 → LoveApp.doChat("你好", "session-1")
  ↓
LoveApp 内部：
  chatClient.prompt()
    .system(恋爱专家提示词)
    .user("你好")
    .advisors(chatId="session-1", 最近10条历史)
    .call()
  ↓
Advisor 链执行：
  ① MessageChatMemoryAdvisor → 从 MySQL 读取聊天历史，注入 Prompt
  ② MyLoggerAdvisor → 打印"AI Request: 你好"
  ③ SensitiveWordAdvisor → 检查敏感词
  ④ MessageChatMemoryAdvisor（调用后） → 将本次对话保存到 MySQL
  ↓
调用 DashScope(qwen-plus) 生成回答
  ↓
返回 AI 回复文本
```

### RAG 对话流程

```
用户请求 → LoveApp.doChatWitchRag("婚后关系不和谐", "session-2")
  ↓
LoveApp 内部：
  ragChatClient.prompt()
    .system(恋爱专家提示词)
    .user("婚后关系不和谐")
    .advisors(chatId, 最近10条历史)
    .advisors(RAG Advisor)  ← 通过工厂创建的
    .call()
  ↓
Advisor 链执行：
  ① MessageChatMemoryAdvisor → 读取历史
  ② MyLoggerAdvisor → 打印日志
  ③ SensitiveWordAdvisor → 检查敏感词
  ④ RetrievalAugmentationAdvisor:
     a. 查询重写器改写问题（可选）
     b. 从 SimpleVectorStore 检索文档
        - 过滤条件：status="已婚"
        - 相似度阈值：0.8
        - 返回条数：topK=3
     c. 把检索到的文档拼入 Prompt
  ↓
发送给 DashScope 生成回答
  ↓
返回 AI 回复文本
```
