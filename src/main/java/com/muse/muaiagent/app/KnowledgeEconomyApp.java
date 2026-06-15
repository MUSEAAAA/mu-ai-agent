package com.muse.muaiagent.app;

import com.muse.muaiagent.advisor.MyLoggerAdvisor;
import com.muse.muaiagent.advisor.SensitiveWordAdvisor;
import com.muse.muaiagent.prompt.PromptLoader;
import com.muse.muaiagent.rag.KnowledgeEconomyRagCustomAdvisorFactory;
import com.muse.muaiagent.rag.QueryRewriter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;

@Component
@Slf4j
public final class KnowledgeEconomyApp {

    private final ChatClient chatClient;
    private final ChatClient ragChatClient;
    private final ChatMemory chatMemory;

    private String username = "暮色";
    private String field = "知识经济基础";
    private String scene = "知识经济学咨询";

    String systemPrompt = PromptLoader.loadPrompt("prompts/knowledge-economy-system-prompt.st",
            Map.of("field", field,
                    "scene", scene,
                    "username", username
            )
    );

    public KnowledgeEconomyApp(ChatModel dashscopeChatModel, ChatMemory chatMemory) {
        this.chatMemory = chatMemory;

        this.chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(
                        new MessageChatMemoryAdvisor(chatMemory),
                        new MyLoggerAdvisor(),
                        new SensitiveWordAdvisor()
                )
                .build();

        this.ragChatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(
                        new MessageChatMemoryAdvisor(chatMemory),
                        new MyLoggerAdvisor(),
                        new SensitiveWordAdvisor()
                )
                .build();
    }

    public String doChat(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .system(systemPrompt)
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .call()
                .chatResponse();
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

    record KnowledgeReport(String title, List<String> suggestions) {

    }

    public KnowledgeReport doChatWithReport(String message, String chatId) {
        KnowledgeReport report = chatClient
                .prompt()
                .system(systemPrompt + """

                        每次对话后都必须生成知识经济分析报告。

                        输出要求：
                        1. title 字段格式必须为：知识经济分析报告
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
                .entity(KnowledgeReport.class);
        log.info("KnowledgeReport {}", report);
        return report;
    }

    @Autowired(required = false)
    @Qualifier("knowledgeEconomyVectorStore")
    private VectorStore knowledgeEconomyVectorStore;

    @Autowired(required = false)
    @Qualifier("knowledgeEconomyRagCloudAdvisor")
    private Advisor knowledgeEconomyRagCloudAdvisor;

    @Autowired(required = false)
    private QueryRewriter queryRewriter;

    public String doChatWithRag(String message, String chatId) {
        if (knowledgeEconomyVectorStore == null || queryRewriter == null) {
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
                .advisors(KnowledgeEconomyRagCustomAdvisorFactory.createKnowledgeEconomyRagCustomAdvisor(
                        knowledgeEconomyVectorStore, "基础", queryRewriter.getQueryTransformer()))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content {}", content);
        return content;
    }

    @Resource
    private ToolCallback[] allTools;

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
