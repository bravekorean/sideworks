package com.example.sideworks.organization.repository;

import com.example.sideworks.department.entity.DepartmentStatus;
import com.example.sideworks.department.entity.QDepartment;
import com.example.sideworks.organization.dto.OrganizationDepartmentResponse;
import com.example.sideworks.organization.dto.OrganizationMemberResponse;
import com.example.sideworks.position.entity.QPosition;
import com.example.sideworks.user.entity.QUser;
import com.example.sideworks.user.entity.UserStatus;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class OrganizationQueryRepository {

    private final JPAQueryFactory queryFactory;

    public List<OrganizationDepartmentResponse> findAllActiveDepartments() {
        QDepartment department = QDepartment.department;
        QDepartment parent = new QDepartment("parent");
        QUser manager = new QUser("manager");
        QUser member = new QUser("member");

        return queryFactory
                .select(Projections.constructor(
                        OrganizationDepartmentResponse.class,
                        department.departmentId,
                        parent.departmentId,
                        department.departmentName,
                        manager.userId,
                        manager.userName,
                        member.userId.count()
                ))
                .from(department)
                .leftJoin(department.parentDepartment, parent)
                .leftJoin(manager).on(
                        manager.userId.eq(department.managerUserId),
                        manager.status.eq(UserStatus.ACTIVE)
                )
                .leftJoin(member).on(
                        member.department.eq(department),
                        member.status.eq(UserStatus.ACTIVE)
                )
                .where(department.status.eq(DepartmentStatus.ACTIVE))
                .groupBy(
                        department.departmentId,
                        parent.departmentId,
                        department.departmentName,
                        manager.userId,
                        manager.userName
                )
                .orderBy(
                        department.departmentName.asc(),
                        department.departmentId.asc()
                )
                .fetch();
    }

    public Page<OrganizationMemberResponse> findActiveMembersByDepartmentId(Long departmentId, Pageable pageable) {
        QDepartment department = QDepartment.department;
        QUser member = QUser.user;
        QPosition position = QPosition.position;

        List<OrganizationMemberResponse> content = queryFactory
                .select(Projections.constructor(
                        OrganizationMemberResponse.class,
                        member.userId,
                        member.userName,
                        member.employeeNo,
                        position.positionId,
                        position.positionName,
                        new CaseBuilder()
                                .when(member.userId.eq(department.managerUserId))
                                .then(true)
                                .otherwise(false)
                ))
                .from(member)
                .join(member.department, department)
                .leftJoin(member.position, position)
                .where(
                        department.departmentId.eq(departmentId),
                        department.status.eq(DepartmentStatus.ACTIVE),
                        member.status.eq(UserStatus.ACTIVE)
                )
                .orderBy(
                        position.positionOrder.asc().nullsLast(),
                        member.userName.asc(),
                        member.userId.asc()
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(member.count())
                .from(member)
                .join(member.department, department)
                .where(
                        department.departmentId.eq(departmentId),
                        department.status.eq(DepartmentStatus.ACTIVE),
                        member.status.eq(UserStatus.ACTIVE)
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0L : total);
    }
}
