package com.muse.muaiagent.demo.invoke;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/**
 * Http调用AI
 */
public class HttpAiInvoke {
    public static void main(String[] args) {
        // 从环境变量获取 API Key（更安全）
        String apiKey = TestApiKey.API_KEY;
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("请设置环境变量 DASHSCOPE_API_KEY");
            return;
        }

        String url = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation";

        // 构建请求体 JSON
        JSONObject requestBody = JSONUtil.createObj()
                .set("model", "qwen-plus")
                .set("input", JSONUtil.createObj()
                        .set("messages", JSONUtil.createArray()
                                .put(JSONUtil.createObj()
                                        .set("role", "system")
                                        .set("content", "You are a helpful assistant."))
                                .put(JSONUtil.createObj()
                                        .set("role", "user")
                                        .set("content", "你是谁？"))))
                .set("parameters", JSONUtil.createObj()
                        .set("result_format", "message"));

        // 发送 POST 请求
        try (HttpResponse response = HttpRequest.post(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(requestBody.toString())
                .execute()) {

            // 打印响应状态码和内容
            System.out.println("Status: " + response.getStatus());
            System.out.println("Response Body: " + response.body());

            // 如需解析返回的消息内容，可按如下方式处理
            if (response.isOk()) {
                JSONObject jsonResponse = JSONUtil.parseObj(response.body());
                String reply = jsonResponse.getByPath("output.choices[0].message.content", String.class);
                System.out.println("AI 回复: " + reply);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}