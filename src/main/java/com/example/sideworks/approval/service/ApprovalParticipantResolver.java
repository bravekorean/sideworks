package com.example.sideworks.approval.service;

import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.repository.UserRepository;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ApprovalParticipantResolver {
    private ApprovalParticipantResolver() {}

    public static List<User> findInOrder(UserRepository users, List<Long> ids, ErrorCode missing) {
        if (ids.isEmpty()) return List.of();
        Map<Long, User> found = users.findAllByUserIdIn(ids).stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity()));
        if (found.size() != ids.size()) throw new BusinessException(missing);
        return ids.stream().map(found::get).toList();
    }
}
