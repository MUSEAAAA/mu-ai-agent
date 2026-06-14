package com.muse.muaiagent.controller;

import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.muse.muaiagent.service.MultiModalConversationCall;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/ai")
public class ImgController {

    @Resource
    private MultiModalConversationCall multiModalConversationCall;

    @PostMapping(value = "/image/explain", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String explainImage(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "question", defaultValue = "请解释这张图片") String question
    ) throws IOException, NoApiKeyException, UploadFileException {

        if (file == null || file.isEmpty()) {
            return "图片不能为空";
        }

        return multiModalConversationCall.imgCall(file, question);
    }
}