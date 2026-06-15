package com.muse.muaiagent.cheatmemory;

import jakarta.annotation.PostConstruct;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 基于mySql + JDBCTemplate 实现的对话记忆持久化
 */
//@Component
//@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true")
public class JdbcChatMemory implements ChatMemory {
    private final JdbcTemplate localJdbcTemplate;

    public JdbcChatMemory(@Qualifier("localJdbcTemplate") JdbcTemplate localJdbcTemplate) {
        this.localJdbcTemplate = localJdbcTemplate;
    }

    /**
     * 保存单条消息
     * @param conversationId
     * @param message
     */
    @Override
    public void add(String conversationId, Message message) {
       add(conversationId,List.of(message));
    }

    /**
     * 保存多条消息
     * @param conversationId
     * @param messages
     */
    @Override
    public void add(String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO chat_memory(conversation_id, message_type, content)
                VALUES (?, ?, ?)
                """;

        for (Message message : messages) {
            localJdbcTemplate.update(
                    sql,
                    conversationId,
                    message.getMessageType().name(),
                    message.getText()
            );
        }
    }

    /***
     * 获取多条数据
     * @param conversationId
     * @param lastN
     * @return
     */
    @Override
    public List<Message> get(String conversationId, int lastN) {
        if (lastN <= 0) {
            return List.of();
        }

        String sql = """
                SELECT message_type, content
                FROM chat_memory
                WHERE conversation_id = ?
                ORDER BY id DESC
                LIMIT ?
                """;

        List<Message> messages = localJdbcTemplate.query(
                sql,
                (rs, rowNum) -> {
                    String messageType = rs.getString("message_type");
                    String content = rs.getString("content");
                    return toMessage(messageType, content);
                },
                conversationId,
                lastN
        );

        // 因为 SQL 是倒序查最近 N 条，所以这里要反转回正常聊天顺序
        Collections.reverse(messages);

        return messages;
    }

    /**
     * 清空某个会话得记忆
     * @param conversationId
     */
    @Override
    public void clear(String conversationId) {
        String sql = """
                DELETE FROM chat_memory
                WHERE conversation_id = ?
                """;
        localJdbcTemplate.update(sql, conversationId);
    }

    /**
     * 将数据还原成Spring Ai的类型
     * @param messageType
     * @param content
     * @return
     */
    private Message toMessage(String messageType, String content) {
        if ("USER".equalsIgnoreCase(messageType)) {
            return new UserMessage(content);
        }

        if ("ASSISTANT".equalsIgnoreCase(messageType)) {
            return new AssistantMessage(content);
        }

        if ("SYSTEM".equalsIgnoreCase(messageType)) {
            return new SystemMessage(content);
        }

        // 兜底处理
        return new UserMessage(content);
    }

    @PostConstruct
    public void initTable() {
        localJdbcTemplate.execute("""
          CREATE TABLE IF NOT EXISTS chat_memory (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              conversation_id VARCHAR(255) NOT NULL,
              message_type VARCHAR(50) NOT NULL,
              content TEXT,
              create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
          ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
      """);
    }
}
