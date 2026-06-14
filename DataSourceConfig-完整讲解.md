# DataSourceConfig 完整讲解

## 一、这个文件是干什么的

让 Spring 应用同时连接**两个数据库**：

```
云端 PostgreSQL → 给 RAG 向量库用
本地 MySQL     → 给聊天记忆用
```

每个数据库都需要一套"连接装备"，而 Spring 需要用 `@Bean` 来声明这套装备。

---

## 二、连接一个数据库需要什么

```
yml 配置文件                           Java 对象
（原始信息）                            （可用的工具）
                                    
地址: jdbc:postgresql://...           
账号: my_user              →        DataSource（连接池）
密码: ***                              ↓
驱动: org.postgresql.Driver        JdbcTemplate（SQL执行器）
```

每次要连一个数据库，都需要三步：

| 步骤 | 产物 | 类比 |
|------|------|------|
| ① 读取配置 | `DataSourceProperties` | 从说明书上抄下地址和密码 |
| ② 创建连接池 | `DataSource`（HikariCP） | 造一根水管接到数据库 |
| ③ 创建执行器 | `JdbcTemplate` | 装个水龙头，以后用水拧开就行 |

两个数据库就是：**2 × 3 = 6 个对象**

---

## 三、先看完整代码（带编号）

```java
@Configuration
public class DataSourceConfig {

    // ==================== 第 1 组：云端 PostgreSQL ====================

    @Primary                                                    // ①
    @Bean(name = "cloudDataSourceProperties")                   // ②
    @ConfigurationProperties(prefix = "spring.datasource.cloud")// ③
    public DataSourceProperties cloudDataSourceProperties() {   // ④
        return new DataSourceProperties();                      // ⑤
    }

    @Primary
    @Bean(name = "cloudDataSource")
    public HikariDataSource cloudDataSource(
            @Qualifier("cloudDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Primary
    @Bean(name = "cloudJdbcTemplate")
    public JdbcTemplate cloudJdbcTemplate(
            @Qualifier("cloudDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }

    // ==================== 第 2 组：本地 MySQL ====================

    @Bean(name = "localDataSourceProperties")
    @ConfigurationProperties(prefix = "spring.datasource.local")
    public DataSourceProperties localDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "localDataSource")
    public HikariDataSource localDataSource(
            @Qualifier("localDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean(name = "localJdbcTemplate")
    public JdbcTemplate localJdbcTemplate(
            @Qualifier("localDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }
}
```

两组代码结构**完全对称**，区别只有：

| | 云端 | 本地 |
|--|------|------|
| Bean 名字都用 `cloud*` | ✅ | ❌ 用 `local*` |
| yml 前缀 `spring.datasource.cloud` | ✅ | ❌ `spring.datasource.local` |
| @Primary（默认首选） | ✅ | ❌ |

所以看懂云端这一组，本地那一组自然就懂了。

---

## 四、逐行拆解云端这一组

### 方法 1：cloudDataSourceProperties

```java
@Primary                                                    // ①
@Bean(name = "cloudDataSourceProperties")                   // ②
@ConfigurationProperties(prefix = "spring.datasource.cloud")// ③
public DataSourceProperties cloudDataSourceProperties() {   // ④
    return new DataSourceProperties();                      // ⑤
}
```

#### 第④行：返回类型 `DataSourceProperties`

`DataSourceProperties` 是 Spring Boot 内置的类，专门用来装载数据库连接信息。它内部有这些字段：

| 字段 | 对应 yml 写法 | setter |
|------|--------------|--------|
| `url` | `url: jdbc:xxx` | `setUrl()` |
| `username` | `username: xxx` | `setUsername()` |
| `password` | `password: xxx` | `setPassword()` |
| `driverClassName` | `driver-class-name: xxx` | `setDriverClassName()` |

**注意：yml 里的 `driver-class-name`（kebab-case）会被 Spring 自动翻译成 Java 里的 `driverClassName`（camelCase），这叫 relaxed binding。**

#### 第⑤行：`new DataSourceProperties()`

创建了一个**空对象**，此时它里面什么值都没有：

```
DataSourceProperties {
    url = null
    username = null
    password = null
    driverClassName = null
}
```

#### 第③行：`@ConfigurationProperties(prefix = "spring.datasource.cloud")`

这一行是关键。它的作用是：**去 yml 里找到以 `spring.datasource.cloud` 开头的所有配置项，逐条调用这个对象的 setter 填进去**。

yml 里的内容：

```yaml
spring:
  datasource:
    cloud:                          ← 前缀匹配到这里
      url: jdbc:postgresql://...    ← 调用 setUrl("jdbc:postgresql://...")
      username: "my_user"           ← 调用 setUsername("my_user")
      password: "Lzc15919313092"    ← 调用 setPassword("Lzc15919313092")
```

执行完第③步后，对象变成了：

```
DataSourceProperties {
    url = "jdbc:postgresql://rm-cn-..."
    username = "my_user"
    password = "Lzc15919313092"
    driverClassName = null          ← yml 的 cloud 段没写 driver-class-name，
                                      后面会通过 URL 自动推导
}
```

**所以这个方法执行完毕后，Spring 容器里就有了一个叫 `cloudDataSourceProperties` 的对象，里面装着云数据库的连接信息。**

#### 第②行：`@Bean(name = "cloudDataSourceProperties")`

给这个对象起个名字，放到 Spring 容器里。别人可以用 `@Qualifier("cloudDataSourceProperties")` 精准拿到它。

如果不给名字呢？Spring 默认用方法名当 bean 名，也就是 `cloudDataSourceProperties`。这里显式写 `name` 是更清晰的写法。

#### 第①行：`@Primary`

**意思：当有多个同类型的 bean 时，默认用我这个。**

因为后面还会有一个 `localDataSourceProperties`，两个都是 `DataSourceProperties` 类型。如果不加 `@Primary`，别人直接写：

```java
@Autowired
DataSourceProperties properties;  // Spring 会懵逼：有两个，你要哪个？
```

加了 `@Primary` 后，默认给云端的。

---

### 方法 2：cloudDataSource

```java
@Primary
@Bean(name = "cloudDataSource")
public HikariDataSource cloudDataSource(
        @Qualifier("cloudDataSourceProperties") DataSourceProperties properties) {
    return properties.initializeDataSourceBuilder()
            .type(HikariDataSource.class)
            .build();
}
```

#### 参数：`@Qualifier("cloudDataSourceProperties") DataSourceProperties properties`

**Spring 看到这个参数，就会从容器里找到名字叫 `cloudDataSourceProperties` 的那个对象传进来。**

所以 `properties` 就是方法 1 创建的那个已经填好配置的对象：

```
properties.url      = "jdbc:postgresql://..."
properties.username = "my_user"
properties.password = "Lzc15919313092"
```

#### 方法体：`properties.initializeDataSourceBuilder()...build()`

把这一行拆成三步：

```java
// 第 1 步：properties.initializeDataSourceBuilder()
// 这是 DataSourceProperties 自带的方法
// 它会做两件事：
//   a) 把 url 转成 jdbcUrl（关键翻译）
//   b) 返回一个 DataSourceBuilder 对象

// 第 2 步：.type(HikariDataSource.class)
// 指定创建 HikariCP 连接池（Spring Boot 默认就是这个，不写也行）

// 第 3 步：.build()
// 真正创建 HikariDataSource 对象
// 此时 HikariCP 已经拿到了：
//   jdbcUrl = "jdbc:postgresql://..."
//   username = "my_user"
//   password = "Lzc15919313092"
```

**最终返回一个已经连上阿里云 PG 的连接池，放入容器，名字叫 `cloudDataSource`。**

---

### 方法 3：cloudJdbcTemplate

```java
@Primary
@Bean(name = "cloudJdbcTemplate")
public JdbcTemplate cloudJdbcTemplate(
        @Qualifier("cloudDataSource") DataSource ds) {
    return new JdbcTemplate(ds);
}
```

最简单的一步。

- 参数 `@Qualifier("cloudDataSource")` → 从容器拿刚刚建好的连接池
- `new JdbcTemplate(ds)` → 用这个连接池创建 SQL 执行器
- 以后所有通过 `cloudJdbcTemplate` 执行的 SQL，都会发到阿里云 PG

---

## 五、为什么偏要经过 DataSourceProperties？

很多人会想：既然最终要的是 DataSource，为什么不直接创建？

```java
// ❌ 直觉写法——会报错
@Bean
@ConfigurationProperties(prefix = "spring.datasource.cloud")
public DataSource dataSource() {
    return DataSourceBuilder.create().build();
}
```

### 错在哪？

`@ConfigurationProperties` 绑定到 `HikariDataSource` 对象上。

HikariDataSource 用 `setJdbcUrl()` 接收连接地址，但 yml 里你写的是 `url:`。Spring Boot 对**自定义前缀**（非 `spring.datasource`）不做 `url → jdbcUrl` 翻译。

结果：

```
yml 里:   url: jdbc:postgresql://...
HikariCP 收到: setUrl("jdbc:postgresql://...") — 没有这个方法！或者说 Spring 尝试绑了但没映射到 jdbcUrl
HikariCP 实际需要的: setJdbcUrl("jdbc:postgresql://...")
```

最终 `jdbcUrl = null` → 报错 `jdbcUrl is required`。

### DataSourceProperties 做了什么

`DataSourceProperties` 就是专门解决这个翻译问题的：

```
yml 里的 "url: xxx"
    → DataSourceProperties.setUrl("xxx")    ← 完美匹配
    → initializeDataSourceBuilder()
    → 把 url 复制到 jdbcUrl 字段           ← 内部翻译
    → 传给 HikariDataSource.setJdbcUrl()   ← HikariCP 认了
```

---

## 六、Spring 的执行顺序

你可能会担心：这 6 个方法谁先谁后？Spring 会自动处理。

Spring 看到 `@Bean` 方法的参数里有 `@Qualifier("xxx")`，就知道这个方法**依赖**另一个 bean。它会：

1. 先创建被依赖的 bean
2. 再创建当前 bean

依赖链：

```
cloudDataSourceProperties  （没有依赖，最先创建）
        ↓
cloudDataSource            （依赖 cloudDataSourceProperties，等它创建完再执行）
        ↓
cloudJdbcTemplate         （依赖 cloudDataSource，等它创建完再执行）
```

**你不需要写任何顺序代码，Spring 自动排序。**

---

## 七、本地 MySQL 组

云端看懂了，本地就是一模一样地复制，改三个地方：

```java
// 改①：bean 名字
@Bean(name = "localDataSourceProperties")     // 不叫 cloud 了

// 改②：yml 前缀
@ConfigurationProperties(prefix = "spring.datasource.local")

// 改③：不加 @Primary（自动配置默认走云端）
```

为什么本地不加 `@Primary`？因为 Spring Boot 的一些自动功能（比如健康检查、数据源监控）默认找 `@Primary` 的那个。让云端保持 Primary，这些功能不用额外配置。

---

## 八、最终效果

```
                ┌──────────────────────────────────────┐
                │          DataSourceConfig             │
                │                                      │
                │  cloudDataSourceProperties            │
                │    url = jdbc:postgresql://...        │
                │    username = my_user                 │
                │           ↓                           │
                │  cloudDataSource (HikariCP)           │
                │    连接阿里云 PG                       │
                │           ↓                           │
                │  cloudJdbcTemplate                    │
                │    → PgVectorStore 用                  │
                │                                      │
                │  localDataSourceProperties            │
                │    url = jdbc:mysql://localhost:3306   │
                │    username = root                    │
                │           ↓                           │
                │  localDataSource (HikariCP)           │
                │    连接本地 MySQL                      │
                │           ↓                           │
                │  localJdbcTemplate                    │
                │    → JdbcChatMemory 用                │
                └──────────────────────────────────────┘
```

两个数据库，两条链路，各用各的，互不干扰。

---

## 九、速查：遇到报错怎么判断

| 报错信息 | 可能原因 |
|---------|---------|
| `Failed to determine a suitable driver class` | yml 里的 `url` 没读到（检查前缀拼写） |
| `jdbcUrl is required with driverClassName` | 直接绑 HikariDataSource 了，忘了用 DataSourceProperties |
| `NoUniqueBeanDefinitionException` | 忘了加 `@Qualifier`，Spring 不确定你要哪个 bean |
| `Table "chat_memory" not found` | 本地 MySQL 里还没建表，`@PostConstruct` 没执行成功 |
