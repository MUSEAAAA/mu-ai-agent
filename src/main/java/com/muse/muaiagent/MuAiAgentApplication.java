package com.muse.muaiagent;

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

}
