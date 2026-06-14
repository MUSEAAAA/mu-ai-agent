package com.muse.muaiagent.prompt;


import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;
import org.apache.commons.lang3.StringUtils;


import java.nio.charset.StandardCharsets;
import java.util.Map;

public class PromptLoader {

    public static String loadPrompt(String path, Map<String,Object> variables) {
        try {
            ClassPathResource resource = new ClassPathResource(path);

            String template = StreamUtils.copyToString(
                    resource.getInputStream(),
                    StandardCharsets.UTF_8
            );
            for (Map.Entry<String,Object> entry : variables.entrySet()) {
                String placeholder = "{" + entry.getKey() + "}";
                String value = String.valueOf(entry.getValue());
                template = template.replace(placeholder,value);

            }
            return template;
        }catch (Exception e) {
            throw new RuntimeException("加载prompt模板失败"+path,e);
        }
    }
}
