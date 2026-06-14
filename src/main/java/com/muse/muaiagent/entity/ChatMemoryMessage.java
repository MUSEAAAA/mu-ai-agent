package com.muse.muaiagent.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatMemoryMessage {

    private Long id;

    private String conversationId;

    private String messageType;

    private String content;

    private LocalDateTime createTime;
}
