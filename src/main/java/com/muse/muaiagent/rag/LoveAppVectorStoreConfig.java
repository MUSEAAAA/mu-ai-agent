package com.muse.muaiagent.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.KeywordMetadataEnricher;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
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
public class LoveAppVectorStoreConfig {
    @Resource
    private LoveAppDocumentLoader loveAppDocumentLoader;
    @Resource
    private MyTokenTextSplitter myTokenTextSplitter;

    @Value("${app.rag.local.store-path:${java.io.tmpdir}/mu-ai-agent/love-app-vector-store.json}")
    private String storePath;

    @Bean
    VectorStore loveAppVectorStore(EmbeddingModel dashscopeEmbeddingModel, KeywordMetadataEnricher keywordMetadataEnricher) {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel)
                .build();

        File vectorStoreFile = new File(storePath);
        if (vectorStoreFile.isFile()) {
            simpleVectorStore.load(vectorStoreFile);
            return simpleVectorStore;
        }

        // 加载文档
        List<Document> documents = loveAppDocumentLoader.loadMarkdowns();
        //自主切分,并不建议使用这个切词器
        // List<Document> documentSplit = myTokenTextSplitter.splitCustomized(documents);
        //simpleVectorStore.add(documentSplit);
        List<Document>enriched =keywordMetadataEnricher.apply(documents);
        simpleVectorStore.add(enriched);

        File parentDirectory = vectorStoreFile.getParentFile();
        if (parentDirectory != null) {
            parentDirectory.mkdirs();
        }
        simpleVectorStore.save(vectorStoreFile);
        return simpleVectorStore;

    }
    @Bean
    public KeywordMetadataEnricher  KeywordMetadataEnricher(ChatModel dashscopeChatModel) {
        return new KeywordMetadataEnricher(dashscopeChatModel,5);
    }
}





//@Configuration
//public class LoveAppVectorStoreConfig {
//
//    @Resource
//    private LoveAppDocumentLoader loveAppDocumentLoader;
//
//    @Bean
//    VectorStore loveAppVectorStore(EmbeddingModel dashscopeEmbeddingModel) {
//        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel)
//                .build();
//        // 加载文档
//        List<Document> documents = loveAppDocumentLoader.loadMarkdowns();
//        simpleVectorStore.add(documents);
//        return simpleVectorStore;
//    }
//}

