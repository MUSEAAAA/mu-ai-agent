package com.muse.muaiagent.app;

import cn.hutool.core.lang.UUID;
import com.muse.muaiagent.rag.KnowledgeEconomyDocumentLoader;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.KeywordMetadataEnricher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class KnowledgeEconomyAppTest {

    @Resource
    private KnowledgeEconomyApp knowledgeEconomyApp;

    @Test
    void testChat() {
        System.out.println("running testChat");
        String chatId = UUID.randomUUID().toString();
        String message = "你好，我想了解知识经济的基础概念";
        String answer = knowledgeEconomyApp.doChat(message, chatId);
        assertNotNull(answer);
    }

    @Test
    void doChatWithReport() {
        System.out.println("running doChatWithReport");
        String chatId = UUID.randomUUID().toString();
        String message = "请分析当前知识经济的发展趋势";
        KnowledgeEconomyApp.KnowledgeReport report = knowledgeEconomyApp.doChatWithReport(message, chatId);
        assertNotNull(report);
    }

    @Test
    void doChatTestSensitiveWord() {
        System.out.println("running doChatTestSensitiveWord");
        String chatId = UUID.randomUUID().toString();
        String message = "用于测试敏感词";
        assertThrows(IllegalArgumentException.class, () -> {
            knowledgeEconomyApp.doChat(message, chatId);
        });
    }

    @Test
    void doChatWithRag() {
        System.out.println("running doChatWithRag");
        String chatId = UUID.randomUUID().toString();
        String message = "我想了解知识经济的基础知识";
        String answer = knowledgeEconomyApp.doChatWithRag(message, chatId);
        assertNotNull(answer);
    }

    @Resource
    private KnowledgeEconomyDocumentLoader knowledgeEconomyDocumentLoader;

    @Autowired(required = false)
    private KeywordMetadataEnricher keywordMetadataEnricher;

    @Test
    void testMetadataEnricher() {
        if (keywordMetadataEnricher == null) {
            System.out.println("KeywordMetadataEnricher is not available (app.rag.local.enabled=false)");
            return;
        }
        List<Document> docs = knowledgeEconomyDocumentLoader.loadMarkdowns();
        List<Document> enriched = keywordMetadataEnricher.apply(docs);
        enriched.forEach(doc -> {
            System.out.println("===== " + doc.getMetadata().get("filename") + " =====");
            System.out.println("status: " + doc.getMetadata().get("status"));
            System.out.println("keywords: " + doc.getMetadata().get("excerpt_keywords"));
            System.out.println();
        });
    }

    @Test
    void doChatWithTools() {
        testMessage("搜索一下最近知识经济领域的热点新闻");

        testMessage("保存一份知识经济分析报告为文件");

        testMessage("生成一份知识资产管理的PDF文档");
    }

    private void testMessage(String message) {
        String chatId = UUID.randomUUID().toString();
        String answer = knowledgeEconomyApp.doChatWithTools(message, chatId);
        assertNotNull(answer);
    }

    @Test
    void doChatWithMcp() {
        String message = "帮我找一些与知识经济相关的图片";
        String chatId = UUID.randomUUID().toString();
        String answer = knowledgeEconomyApp.doChatWithMcp(message, chatId);
        assertNotNull(answer);
    }
}
