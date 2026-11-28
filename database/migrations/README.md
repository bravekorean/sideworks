# DB 마이그레이션 적용 절차

애플리케이션은 `spring.jpa.hibernate.ddl-auto=validate`로 실행합니다. Hibernate는 테이블과 컬럼만 검증하고 인덱스는 만들지 않습니다. 따라서 이 폴더의 SQL은 배포 전에 사람이 직접 적용합니다.

## 적용 순서

1. 대상 DB를 백업합니다. 운영 DB라면 쓰기가 적은 시간대를 고릅니다.
2. 아직 적용하지 않은 파일을 **파일명의 날짜 순서대로** 실행합니다. 파일 머리의 주석에 선행 조건이 있으면 먼저 확인합니다.
3. 각 파일의 마지막 `SELECT`로 결과를 확인합니다.
4. 애플리케이션을 배포하고 기동 로그에서 스키마 검증 오류가 없는지 확인합니다.

적용 이력 테이블은 따로 없습니다. 어떤 파일까지 적용했는지는 배포 기록에 남깁니다.

## 인덱스 파일 주의사항

- 인덱스 이름과 컬럼 순서는 엔티티의 `@Index`와 같아야 합니다. 테스트 DB(`create-drop`)는 `@Index`로, 실제 DB는 이 SQL로 만들기 때문입니다.
- 일부 `ALTER TABLE`만 성공한 상태에서 파일 전체를 다시 실행하면 중복 인덱스 오류가 납니다. 다시 실행하기 전에 아래 쿼리로 이미 있는 인덱스를 확인하고, 없는 것만 실행합니다.
- `ALGORITHM = INPLACE, LOCK = NONE`은 쓰기를 막지 않습니다. 다만 시작과 끝에 메타데이터 잠금을 잠깐 잡기 때문에, 오래 실행 중인 트랜잭션이 있으면 그 트랜잭션이 끝날 때까지 기다립니다.

```sql
SELECT table_name, index_name,
       GROUP_CONCAT(CONCAT(column_name, IF(collation = 'D', ' DESC', '')) ORDER BY seq_in_index) AS columns_in_order
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name IN ('approval_linetbl', 'approval_historytbl')
GROUP BY table_name, index_name;
```
