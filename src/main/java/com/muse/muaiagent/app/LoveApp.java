package com.muse.muaiagent.app;

import com.muse.muaiagent.advisor.MyLoggerAdvisor;
import com.muse.muaiagent.advisor.SensitiveWordAdvisor;
import com.muse.muaiagent.prompt.PromptLoader;
import com.muse.muaiagent.rag.LoveAppRagCustomAdvisorFactory;
import com.muse.muaiagent.rag.QueryRewriter;
import com.muse.muaiagent.tools.ToolRegistration;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.CorePublisher;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;

@Component
@Slf4j
public final class LoveApp {

    private final ChatClient chatClient;
    private final ChatClient ragChatClient;
    private final ChatMemory chatMemory;

    private String username = "暮色";
    private String relationshipStatus = "单身";

    String systemPrompt = PromptLoader.loadPrompt("prompts/love-system-prompt.st",
           Map.of("relationshipStatus", relationshipStatus,
                   "scene", "恋爱咨询",
                   "username", username
           )
           );
    // Previous database/PGVector injection is retained for future re-enabling:
    // @Resource(name = "pgVectorVectorStore")
    // private VectorStore pgVectorVectorStore;

    public LoveApp(ChatModel dashscopeChatModel, ChatMemory chatMemory) {
        this.chatMemory = chatMemory;

        // 普通对话 ChatClient：带聊天记忆
        this.chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(
                        new MessageChatMemoryAdvisor(chatMemory),
                        new MyLoggerAdvisor(),
                        new SensitiveWordAdvisor()
                )
                .build();

        // RAG 专用 ChatClient：不带聊天记忆
        this.ragChatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(
                        new MessageChatMemoryAdvisor(chatMemory),
                        new MyLoggerAdvisor(),
                        new SensitiveWordAdvisor()
                )
                .build();
    }

    /**
     * AI 基础对话(支持多轮对话)
     * @param message
     * @param chatId
     * @return
     */
    public String doChat(String message, String chatId) {

        ChatResponse chatResponse = chatClient
                .prompt()
                .system(systemPrompt)
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .call()
                .chatResponse();
        chatResponse.getResult().getOutput().getText();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content {}", content);
        return content;
    }

    public Flux<String> doChatByStream(String message, String chatId) {
        return chatClient
                .prompt()
                .system(systemPrompt)
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .stream()
                .content();

    }

    public void clearChatMemory(String chatId) {
        chatMemory.clear(chatId);
    }

    record LoveReport(String title, List<String> suggestions) {

    }

    /**
     * AI 恋爱报告输出(结构化输出)
     * @param message
     * @param chatId
     * @return
     */
    public LoveReport doChatWithReport(String message, String chatId) {
        LoveReport loveReport = chatClient
                .prompt()
                .system(systemPrompt + """

                    每次聊天后都必须生成恋爱报告。

                    输出要求：
                    1. title 字段格式必须为：用户的恋爱报告
                    2. suggestions 字段必须是字符串列表
                    3. suggestions 必须包含 4 条建议
                    4. 每一条建议必须是独立的字符串元素
                    5. 禁止把多条建议合并到一个字符串里
                    6. 每条建议控制在 30 到 60 个字之间
                    """)
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .call()
                .entity(LoveReport.class);
        log.info("LoveReport {}", loveReport);
        return loveReport;
    }
    //TODO 这个我仍需要一段讲解
    @Autowired(required = false)
    @Qualifier("loveAppVectorStore")
    private VectorStore loveAppVectorStore;

    @Autowired(required = false)
    @Qualifier("loveAppRagCloudeAdvisor")
    private Advisor loveAppRagCloudedAdvisor;

    @Autowired(required = false)
    private QueryRewriter queryRewriter;

    public String doChatWitchRag(String message, String chatId) {
        if (loveAppVectorStore == null || queryRewriter == null) {
            log.info("Local RAG is disabled; falling back to normal chat");
            return doChat(message, chatId);
        }

        ChatResponse chatResponse = ragChatClient
                .prompt()
                .system(systemPrompt)
                .user(message)
                .advisors(spec -> spec.param(
                        CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                //.advisors(new MyLoggerAdvisor())
                //应用RAG检索增强问答
                //.advisors(loveAppRagCloudedAdvisor)
                //下面这一段是使用postgreSql来实现RAG的库
                //.advisors(new QuestionAnswerAdvisor(pgVectorVectorStore))
                //基于RAG知识库问答
                //.advisors(new QuestionAnswerAdvisor(loveAppVectorStore))
                .advisors(LoveAppRagCustomAdvisorFactory.createLoveAppRagCustomAdvisor(loveAppVectorStore, "已婚", queryRewriter.getQueryTransformer()))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content {}", content);
        return content;

    }
    @Resource
    private ToolCallback[] allTools;
    /**
     * AI 恋爱报告输出调用工具
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithTools(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .system(systemPrompt)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .tools(allTools)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content {}", content);
        return content;
    }

    @Autowired(required = false)
    private ToolCallbackProvider toolsCallbackProvider;
    /**
     * AI 恋爱报告输出调用MCP工具
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithMcp(String message, String chatId) {
        if (toolsCallbackProvider == null) {
            throw new IllegalStateException("MCP service is disabled");
        }

        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .tools(toolsCallbackProvider)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content {}", content);
        return content;
    }

}

//public LoveApp(ChatModel dashscopeChatModel , ChatMemory chatMemory ) {
//    //这个chatClient定义了默认的拦截器
//    //初始化基于文件的对话记忆
//    //String fileDir = System.getProperty("user.dir")+"/tmp/chat-memory";
//    //ChatMemory chatMemory = new FileBasedChatMemory(fileDir);
//    //初始化基于内存的对话记忆
//    // ChatMemory chatMemory = new InMemoryChatMemory();
//    chatClient = ChatClient.builder(dashscopeChatModel)
//
//            .defaultAdvisors(
//                    //new MessageChatMemoryAdvisor(chatMemory),
//                    //自定义日志拦截器
//                    new MyLoggerAdvisor()
//                    , new SensitiveWordAdvisor()
////                     , new ReReadingAdvisor()
//            )
//            .build();
//
//}
