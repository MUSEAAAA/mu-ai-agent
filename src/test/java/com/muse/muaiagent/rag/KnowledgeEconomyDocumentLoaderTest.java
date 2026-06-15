package com.muse.muaiagent.rag;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class KnowledgeEconomyDocumentLoaderTest {

    @Resource
    private KnowledgeEconomyDocumentLoader loader;
    @Autowired(required = false)
    private VectorStore knowledgeEconomyVectorStore;
    @Autowired(required = false)
    private QueryRewriter queryRewriter;

    @Test
    void loadMarkdowns() {
        loader.loadMarkdowns();
    }

    @Test
    void loadMarkdownsWithCategory() {
        List<Document> docs = loader.loadMarkdowns();
        docs.forEach(doc ->
                System.out.println(doc.getMetadata().get("category") + " | " + doc.getText().substring(0, 20))
        );
    }

    @Test
    void testFilterByCategory() {
        if (knowledgeEconomyVectorStore == null) {
            System.out.println("VectorStore is not available (app.rag.local.enabled=false)");
            return;
        }
        List<Document> all = knowledgeEconomyVectorStore.similaritySearch(
                SearchRequest.builder().query("知识经济概述").topK(10).build()
        );
        System.out.println("无过滤: " + all.size() + " 条");

        List<Document> basic = knowledgeEconomyVectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("知识经济概述")
                        .topK(10)
                        .filterExpression(new FilterExpressionBuilder().eq("status", "基础").build())
                        .build()
        );
        System.out.println("基础过滤: " + basic.size() + " 条");
        basic.forEach(doc ->
                System.out.println("  " + doc.getMetadata().get("status") + " | " + doc.getText().substring(0, 30))
        );

        assertTrue(basic.stream().allMatch(
                doc -> "基础".equals(doc.getMetadata().get("status"))
        ));
    }

    @Test
    void testFilterByCategory2() {
        if (knowledgeEconomyVectorStore == null) {
            System.out.println("VectorStore is not available (app.rag.local.enabled=false)");
            return;
        }
        List<Document> all = knowledgeEconomyVectorStore.similaritySearch(
                SearchRequest.builder().query("知识经济概述").topK(10).build()
        );
        System.out.println("无过滤: " + all.size() + " 条");
        all.forEach(doc ->
                System.out.println("metadata: " + doc.getMetadata() + " | 内容: " + doc.getText().substring(0, 20))
        );
    }

    @Test
    void testQueryRewrite() {
        if (queryRewriter == null) {
            System.out.println("QueryRewriter is not available (app.rag.local.enabled=false)");
            return;
        }
        String result = queryRewriter.doQueryRewrite("这个怎么办");
        System.out.println("改写前：这个怎么办");
        System.out.println("改写后：" + result);
    }
}
