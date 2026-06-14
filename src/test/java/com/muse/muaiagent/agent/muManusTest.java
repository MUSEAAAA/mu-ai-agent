package com.muse.muaiagent.agent;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class muManusTest {
    @Resource
    muManus muManus;
    @Test
    void run() {
        String userPrompt ="最简短的输出，回复我一下就好";
        muManus.run( userPrompt );
    }


}