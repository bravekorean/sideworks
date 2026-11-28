-- V2 권한 이관 전 읽기 전용 점검 스크립트다.
-- 기존 ADMIN을 일괄 변경하지 않고 실제 책임과 부서장 배정을 확인한 뒤 사용자별로 이관한다.

SELECT
    u.user_id,
    u.user_name,
    u.employee_no,
    u.department_id,
    u.status,
    CASE
        WHEN EXISTS (
            SELECT 1
            FROM departmenttbl d
            WHERE d.manager_user_id = u.user_id
              AND d.status = 'ACTIVE'
        ) THEN 'TEAM_LEADER_RELATION_EXISTS'
        ELSE 'NO_TEAM_LEADER_RELATION'
    END AS team_leader_relation
FROM usertbl u
WHERE u.user_role = 'ADMIN'
ORDER BY u.user_id;

select user_id, login_id, user_name, user_role from usertbl where user_role = 'ADMIN';

update usertbl set user_role = 'USER' where user_id in (2,4,5);
commit;

-- 점검 후 실제 담당 업무에 따라 명시적인 사용자 ID로 각각 실행한다.
-- 인사 담당자 예시:
-- UPDATE usertbl SET user_role = 'HR_MANAGER' WHERE user_id IN (검토한_사용자_ID);
-- 부서장 관계만 유지할 사용자 예시:
-- UPDATE usertbl SET user_role = 'USER' WHERE user_id IN (검토한_사용자_ID);
-- 시스템 운영자는 기존 SUPER_ADMIN 계정을 사용하며 API에서 SUPER_ADMIN 승격은 허용하지 않는다.
