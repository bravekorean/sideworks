package com.example.sideworks;

import com.example.sideworks.support.MySqlTestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@Import(MySqlTestcontainersConfig.class)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.cors.allowed-origins=http://localhost:5173"
})
class SideworksApplicationTests {

    @Test
    void contextLoads() {
    }
}
