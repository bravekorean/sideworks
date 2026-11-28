package com.example.sideworks.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MySQLContainer;

/**
 * 로컬 설정 파일 없이 실제 MySQL 8에서 애플리케이션 컨텍스트를 띄우기 위한 공용 테스트 설정.
 * {@code @ServiceConnection}이 컨테이너 접속 정보를 DataSource에 주입한다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MySqlTestcontainersConfig {

    @Bean
    @ServiceConnection
    MySQLContainer<?> mysqlContainer() {
        return new MySQLContainer<>("mysql:8.0.44");
    }
}
