package com.example.sideworks.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
// @CreatedDate, @LastModifiedDate가 동작하도록 JPA Auditing 기능을 활성화한다.
@EnableJpaAuditing
public class JpaConfig {
}
