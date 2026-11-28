package com.example.sideworks;

import java.util.List;
import java.util.Locale;

/** 최근 활동 목록 조회가 탄 SQL 경로를 RecordingStatementInspector가 모은 문장으로 판별한다. */
enum RecentActivityListPath {
    /** 목록 SQL이 없거나 100건 이내 고정 탐색만 사용 */
    NONE,
    /** 최신 구간 SQL만 사용 */
    WINDOW,
    /** 최신 구간이 부족해 UNION 페이지 SQL로 다시 조회 */
    WINDOW_THEN_UNION,
    /** 구간을 건너뛰거나 고정 탐색이 부족해 UNION 페이지 SQL 사용 */
    UNION;

    static RecentActivityListPath of(List<String> statements) {
        boolean window = false;
        boolean union = false;
        for (String sql : statements) {
            String normalized = sql.toLowerCase(Locale.ROOT);
            if (normalized.contains("from latest w")) window = true;
            // 건수 SQL도 접근 문서 집합(with visible)을 쓰므로 count는 제외한다.
            if (normalized.contains("with visible") && !normalized.contains("count(")) union = true;
        }
        if (window) return union ? WINDOW_THEN_UNION : WINDOW;
        return union ? UNION : NONE;
    }
}
