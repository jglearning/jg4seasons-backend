package com.jglearning.jg4seasons.system;

import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import com.jglearning.jg4seasons.academy.api.AcademyRegistry;
import com.jglearning.jg4seasons.auth.api.Operators;
import com.jglearning.jg4seasons.auth.api.StudentAccounts;
import com.jglearning.jg4seasons.auth.api.StudentRoster;
import com.jglearning.jg4seasons.clazz.api.ClassRef;
import com.jglearning.jg4seasons.clazz.api.ClassRoster;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import com.jglearning.jg4seasons.system.web.dto.TalkStudentListResponse;
import com.jglearning.jg4seasons.system.web.dto.TalkStudentListResponse.TalkStudentItem;
import com.jglearning.jg4seasons.system.web.dto.TalkStudentsSetRequest;
import com.jglearning.jg4seasons.talk.api.TalkStudentAccess;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 7.5·7.6 운영자 학생별 프리토킹 허용(ADR 0040, O-04). 학생 계정은 auth, 지금 반은 clazz, 허용은 talk에서 받아 조립한다. 모든 메서드는 먼저 요청한 사람이 지금도
 * 활성 운영자인지 확인한다. 목록은 재원생만
 */
@Service
@Transactional
public class SystemTalkService {

    /** 반 이름 → 이름 → id. 반 없는 학생은 맨 뒤 */
    private static final Comparator<TalkStudentItem> ORDER = Comparator.comparing(
                    (TalkStudentItem item) ->
                            item.clazz() == null ? null : item.clazz().name(),
                    Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(TalkStudentItem::name)
            .thenComparingLong(TalkStudentItem::id);

    private final Operators operators;
    private final AcademyRegistry registry;
    private final AcademyDirectory academies;
    private final StudentAccounts students;
    private final ClassRoster classes;
    private final TalkStudentAccess access;

    SystemTalkService(
            Operators operators,
            AcademyRegistry registry,
            AcademyDirectory academies,
            StudentAccounts students,
            ClassRoster classes,
            TalkStudentAccess access) {
        this.operators = operators;
        this.registry = registry;
        this.academies = academies;
        this.students = students;
        this.classes = classes;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public TalkStudentListResponse list(long actorId, long academyId) {
        operators.requireActiveOperator(actorId);
        registry.find(academyId);
        StudentRoster active =
                students.roster(academyId, students.studentIds(academyId)).activeOnly();
        return new TalkStudentListResponse(items(academyId, active));
    }

    /**
     * 학원 관리 쓰기는 학원 행 잠금으로 줄 세운다(다른 관리 쓰기와 같은 규칙, 가장 먼저). 학원이 없으면 잠금은 아무것도 하지 않고 아래에서 404. 요청한 학생이 모두 그
     * 학원 재원생이어야 쓴다 — 하나라도 아니면 404, 아무것도 바꾸지 않음
     */
    public TalkStudentListResponse set(long actorId, long academyId, TalkStudentsSetRequest request) {
        academies.lockForAdminWrite(academyId);
        operators.requireActiveOperator(actorId);
        registry.find(academyId);
        Set<Long> requested = new LinkedHashSet<>(request.studentIds());
        StudentRoster active = students.roster(academyId, requested).activeOnly();
        if (active.accounts().size() != requested.size()) {
            throw new ApiException(CommonErrorCode.NOT_FOUND, "학생을 찾을 수 없어요");
        }
        access.set(active.students(), request.enabled(), actorId);
        return new TalkStudentListResponse(items(academyId, active));
    }

    private List<TalkStudentItem> items(long academyId, StudentRoster roster) {
        Map<Long, ClassRef> classOf =
                classes.currentClassOf(academyId, roster.students().ids());
        Set<Long> enabled = access.enabledAmong(roster.students());
        return roster.accounts().stream()
                .map(account -> {
                    ClassRef clazz = classOf.get(account.id());
                    return new TalkStudentItem(
                            account.id(),
                            account.name(),
                            clazz == null ? null : new TalkStudentListResponse.ClassRef(clazz.id(), clazz.name()),
                            enabled.contains(account.id()));
                })
                .sorted(ORDER)
                .toList();
    }
}
