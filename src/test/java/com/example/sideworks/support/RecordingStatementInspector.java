package com.example.sideworks.support;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.ArrayList;
import java.util.List;

/**
 * 테스트 전용: 기록을 켠 스레드에서 Hibernate가 실행하는 SQL을 순서대로 모은다.
 * {@code spring.jpa.properties.hibernate.session_factory.statement_inspector}로 등록한다.
 */
public class RecordingStatementInspector implements StatementInspector {

    private static final ThreadLocal<List<String>> RECORDED = new ThreadLocal<>();

    public static void start() {
        RECORDED.set(new ArrayList<>());
    }

    public static List<String> stop() {
        List<String> statements = RECORDED.get();
        RECORDED.remove();
        return statements == null ? List.of() : List.copyOf(statements);
    }

    @Override
    public String inspect(String sql) {
        List<String> statements = RECORDED.get();
        if (statements != null) statements.add(sql);
        return sql;
    }
}
