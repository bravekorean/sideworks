package com.example.sideworks.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuerydslConfig {

    @Bean
    public JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
        // QueryDSL 쿼리 작성에 필요한 JPAQueryFactory를 Spring Bean으로 등록해 Repository 구현체에서 주입받는다.
        return new JPAQueryFactory(entityManager);
    }
}
