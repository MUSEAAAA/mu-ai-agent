package com.muse.muaiagent.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.KeywordMetadataEnricher;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.util.List;

@Configuration
@ConditionalOnProperty(name = "app.rag.local.enabled", havingValue = "true")
public class KnowledgeEconomyVectorStoreConfig {
    @Resource
    private KnowledgeEconomyDocumentLoader knowledgeEconomyDocumentLoader;

    @Resource
    private MyTokenTextSplitter myTokenTextSplitter;

    @Value("${app.rag.local.store-path:${java.io.tmpdir}/mu-ai-agent/knowledge-economy-vector-store.json}")
    private String storePath;

    @Bean
    VectorStore knowledgeEconomyVectorStore(EmbeddingModel dashscopeEmbeddingModel, KeywordMetadataEnricher keywordMetadataEnricher) {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel)
                .build();

        File vectorStoreFile = new File(storePath);
        if (vectorStoreFile.isFile()) {
            simpleVectorStore.load(vectorStoreFile);
            return simpleVectorStore;
        }

        List<Document> documents = knowledgeEconomyDocumentLoader.loadMarkdowns();
        List<Document> enriched = keywordMetadataEnricher.apply(documents);
        simpleVectorStore.add(enriched);

        File parentDirectory = vectorStoreFile.getParentFile();
        if (parentDirectory != null) {
            parentDirectory.mkdirs();
        }
        simpleVectorStore.save(vectorStoreFile);
        return simpleVectorStore;
    }

    @Bean
    public KeywordMetadataEnricher keywordMetadataEnricher(ChatModel dashscopeChatModel) {
        return new KeywordMetadataEnricher(dashscopeChatModel, 5);
    }
}
