package com.muse.muaiagent.service;

import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface MultiModalConversationCall {

    String imgCall(MultipartFile file, String question)
            throws NoApiKeyException, UploadFileException, IOException;
}