package com.jglearning.jg4seasons.teacher;

import com.jglearning.jg4seasons.auth.api.StudentAccount;
import com.jglearning.jg4seasons.auth.api.StudentRoster;
import com.jglearning.jg4seasons.clazz.api.TeacherClass;
import com.jglearning.jg4seasons.content.api.LessonCatalog;
import com.jglearning.jg4seasons.content.api.LessonContent;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import com.jglearning.jg4seasons.teacher.web.dto.TeacherWordTestDetail;
import com.jglearning.jg4seasons.teacher.web.dto.TeacherWordTestItem;
import com.jglearning.jg4seasons.teacher.web.dto.TeacherWordTestList;
import com.jglearning.jg4seasons.teacher.web.dto.TeacherWordTestStudent;
import com.jglearning.jg4seasons.teacher.web.dto.TeacherWordTestStudentRef;
import com.jglearning.jg4seasons.teacher.web.dto.TeacherWordTestSummary;
import com.jglearning.jg4seasons.wordtest.api.WordTestResults;
import com.jglearning.jg4seasons.wordtest.api.WordTestResults.Detail;
import com.jglearning.jg4seasons.wordtest.api.WordTestResults.Summary;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 교사 단어시험 결과 조회(D-5·D-6). 범위는 TeacherScope(관리자 학원 전체, 교사 담당 반), 결과는 wordtest 공개 api에 학생 묶음으로. 회차 열림·사용권은 보지 않는다(이미
 * 낸 결과 조회)
 */
@Service
@Transactional(readOnly = true)
public class TeacherWordTestService {

    private final TeacherScope scope;
    private final LessonCatalog catalog;
    private final WordTestResults results;

    TeacherWordTestService(TeacherScope scope, LessonCatalog catalog, WordTestResults results) {
        this.scope = scope;
        this.catalog = catalog;
        this.results = results;
    }

    /** 순서: 범위(401·404, 교사는 진행 중 반만) → 기능 켜짐(404 FEATURE_DISABLED) → 그 반 진도표의 회차(404) → 시험지(404) → 명단·마지막 시도 */
    public TeacherWordTestList list(long academyId, long actorId, long classId, String lessonCode, String section) {
        TeacherScope.ScopedClass scoped = scope.activeClassOf(academyId, actorId, classId);
        results.requireEnabled(academyId);
        TeacherClass clazz = scoped.clazz();
        LessonContent lesson = catalog.findByCode(lessonCode)
                .filter(content -> content.bookId() == clazz.bookId())
                .filter(content -> clazz.lessons().stream().anyMatch(row -> row.lessonId() == content.lessonId()))
                .orElseThrow(() -> new ApiException(CommonErrorCode.NOT_FOUND, "회차를 찾을 수 없어요"));
        if (!results.hasItems(lesson.lessonId(), section)) {
            throw new ApiException(CommonErrorCode.NOT_FOUND, "이 영역의 단어시험이 없어요");
        }
        StudentRoster roster = scope.roster(scoped);
        Map<Long, Summary> latest = results.latestOf(roster.students(), lesson.lessonId(), section);
        return new TeacherWordTestList(
                lesson.code(),
                roster.accounts().stream()
                        .map(account -> new TeacherWordTestStudent(
                                account.id(), account.name(), summary(latest.get(account.id()))))
                        .toList());
    }

    /** 순서: 활성 교사·관리자(401) → 기능 켜짐(404 FEATURE_DISABLED) → 범위 안 학생의 제출(아니면 404) */
    public TeacherWordTestDetail detail(long academyId, long actorId, long submissionId) {
        StudentRoster roster = scope.studentsInScope(academyId, actorId);
        results.requireEnabled(academyId);
        Detail d = results.detailOf(roster.students(), submissionId)
                .orElseThrow(() -> new ApiException(CommonErrorCode.NOT_FOUND, "제출을 찾을 수 없어요"));
        StudentAccount student = roster.accounts().stream()
                .filter(account -> account.id() == d.studentId())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("범위 학생의 계정이 없다: " + d.studentId()));
        return new TeacherWordTestDetail(
                d.id(),
                d.lessonCode(),
                d.section(),
                d.attemptNo(),
                d.status(),
                d.score(),
                d.total(),
                d.submittedAt(),
                d.gradedAt(),
                d.items() == null
                        ? null
                        : d.items().stream()
                                .map(i -> new TeacherWordTestItem(
                                        i.no(), i.prompt(), i.written(), i.result(), i.answer()))
                                .toList(),
                new TeacherWordTestStudentRef(student.id(), student.name()),
                d.imageUrl());
    }

    private static TeacherWordTestSummary summary(Summary s) {
        return s == null
                ? null
                : new TeacherWordTestSummary(s.id(), s.status(), s.score(), s.total(), s.attemptNo(), s.submittedAt());
    }
}
