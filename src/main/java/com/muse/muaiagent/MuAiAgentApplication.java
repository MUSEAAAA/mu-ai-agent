package com.muse.muaiagent;

import com.alibaba.dashscope.utils.Constants;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(
        exclude = DataSourceAutoConfiguration.class,
        excludeName = "org.springframework.ai.autoconfigure.vectorstore.pgvector.PgVectorStoreAutoConfiguration"
)
public class MuAiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(MuAiAgentApplication.class, args);
    }

    @PostConstruct
    void initDashScopeApiKey() {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = System.getenv("DASHSCOPEKEY");
        }
        if (apiKey != null && !apiKey.isEmpty()) {
            Constants.apiKey = apiKey;
        }
    }
}
