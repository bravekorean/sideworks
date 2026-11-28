package com.example.sideworks;

import com.example.sideworks.support.MySqlTestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@Import(MySqlTestcontainersConfig.class)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.cors.allowed-origins=http://localhost:5173"
})
class SideworksApplicationTests {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void contextLoads() {
    }

    @Test
    void applicationConnectionsUseShortLockWaitTimeout() {
        assertThat(jdbc.queryForObject("select @@session.innodb_lock_wait_timeout", Integer.class)).isEqualTo(5);
    }
}
