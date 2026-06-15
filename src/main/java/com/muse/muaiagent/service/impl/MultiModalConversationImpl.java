package com.muse.muaiagent.service.impl;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.alibaba.dashscope.utils.Constants;
import com.muse.muaiagent.service.MultiModalConversationCall;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

@Service
public class MultiModalConversationImpl implements MultiModalConversationCall {

    @Value("${spring.ai.dashscope.api-key}")
    private String dashscopeApiKey;

    @Override
    public String imgCall(MultipartFile file, String question)
            throws NoApiKeyException, UploadFileException, IOException {

        // 确保 DashScope SDK 能拿到 API key（OSSUtils 需要）
        Constants.apiKey = dashscopeApiKey;

        // 1. 把 MultipartFile 保存成本地临时文件
        String originalFilename = file.getOriginalFilename();
        String suffix = ".jpg";

        if (originalFilename != null && originalFilename.contains(".")) {
            suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        File tempFile = File.createTempFile("upload-", suffix);
        file.transferTo(tempFile);

        try {
            // 2. 把本地文件转换成 file URI
            String path = tempFile.getAbsolutePath().replace("\\", "/");

            if (!path.startsWith("/")) {
                path = "/" + path;
            }

            String imagePath = "file://" + path;


            System.out.println("imagePath = " + imagePath);
            System.out.println("tempFile exists = " + tempFile.exists());
            System.out.println("tempFile length = " + tempFile.length());

            MultiModalConversation conv = new MultiModalConversation();

            // 3. 注意：这里不能传 MultipartFile，要传 imagePath
            MultiModalMessage userMessage = MultiModalMessage.builder()
                    .role(Role.USER.getValue())
                    .content(Arrays.asList(
                            Collections.singletonMap("image", imagePath),
                            Collections.singletonMap("text", question)
                    ))
                    .build();

            // 4. model 要用支持图片理解的多模态模型
            MultiModalConversationParam param = MultiModalConversationParam.builder()
                    .apiKey(dashscopeApiKey)
                    .model("qwen-vl-plus")
                    .messages(Collections.singletonList(userMessage))
                    .build();

            MultiModalConversationResult result = conv.call(param);

            return result.getOutput().toString();

        } finally {
            // 5. 调用完成后删除临时文件
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }
}
