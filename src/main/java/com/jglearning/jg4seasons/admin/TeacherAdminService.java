package com.jglearning.jg4seasons.admin;

import com.jglearning.jg4seasons.admin.web.dto.TeacherCreateRequest;
import com.jglearning.jg4seasons.admin.web.dto.TeacherListResponse;
import com.jglearning.jg4seasons.admin.web.dto.TeacherResponse;
import com.jglearning.jg4seasons.admin.web.dto.TeacherUpdateRequest;
import com.jglearning.jg4seasons.auth.api.AccountStatus;
import com.jglearning.jg4seasons.auth.api.NewStaff;
import com.jglearning.jg4seasons.auth.api.StaffAccount;
import com.jglearning.jg4seasons.auth.api.StaffAccounts;
import com.jglearning.jg4seasons.auth.api.StaffChange;
import com.jglearning.jg4seasons.auth.api.StaffRole;
import com.jglearning.jg4seasons.clazz.api.ClassTeachers;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 5.3 관리자 교사 API(ADM-04). 계정은 auth, 담당 반은 clazz — 한 트랜잭션에서 조립한다(반 배정이 404면 계정도 만들지 않음). academyId·actorId는
 * 컨트롤러가 인증 주체(JWT)에서 넘긴다. 모든 메서드는 먼저 요청한 사람이 지금도 활성 관리자인지 확인한다(JWT role만 믿지 않음).
 */
@Service
@Transactional
public class TeacherAdminService {

    private final StaffAccounts accounts;
    private final ClassTeachers classTeachers;

    TeacherAdminService(StaffAccounts accounts, ClassTeachers classTeachers) {
        this.accounts = accounts;
        this.classTeachers = classTeachers;
    }

    @Transactional(readOnly = true)
    public TeacherListResponse list(long academyId, long actorId) {
        accounts.requireActiveAdmin(academyId, actorId);
        List<TeacherResponse> items = accounts.list(academyId).stream()
                .map(account -> toResponse(academyId, account))
                .toList();
        return new TeacherListResponse(items, items.size());
    }

    public TeacherResponse create(long academyId, long actorId, TeacherCreateRequest request) {
        accounts.requireActiveAdmin(academyId, actorId);
        StaffAccount created = accounts.create(
                academyId,
                new NewStaff(
                        request.name(), request.loginId(), request.password(), StaffRole.fromValue(request.role())));
        classTeachers.replace(academyId, created.id(), new HashSet<>(request.classIds()));
        return toResponse(academyId, created);
    }

    public TeacherResponse update(long academyId, long actorId, long teacherId, TeacherUpdateRequest request) {
        accounts.requireActiveAdmin(academyId, actorId);
        StaffAccount changed = accounts.change(
                academyId,
                actorId,
                teacherId,
                new StaffChange(
                        Optional.ofNullable(request.name()),
                        Optional.ofNullable(request.status()).map(AccountStatus::fromValue),
                        Optional.ofNullable(request.password())));
        if (request.classIds() != null) {
            classTeachers.replace(academyId, teacherId, new HashSet<>(request.classIds()));
        }
        return toResponse(academyId, changed);
    }

    private TeacherResponse toResponse(long academyId, StaffAccount account) {
        return new TeacherResponse(
                account.id(),
                account.name(),
                account.loginId(),
                account.role().value(),
                account.status().value(),
                classTeachers.classIdsOf(academyId, account.id()),
                account.lastLoginAt());
    }
}
