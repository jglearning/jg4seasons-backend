package com.jglearning.jg4seasons.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.admin.RosterImportRules.Draft;
import com.jglearning.jg4seasons.admin.RosterImportRules.Student;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 명단 가져오기 요청 안에서만 보는 규칙(ADR 0036): 반 고르기 하나, 전체 500명, 같은 학생 두 번. */
class RosterImportRulesTest {

    private static Student s(String name, String school, String grade) {
        return new Student(name, school, grade);
    }

    private static Draft draft(String label, Student... students) {
        return new Draft(label, true, List.of(students));
    }

    @Test
    void 문제가_없으면_빈_목록() {
        List<RosterIssue> issues = RosterImportRules.inspect(List.of(
                draft("그린 월수반", s("김민준", "A중", "중1"), s("이서연", null, null)), draft("블루 화목반", s("박지호", "A중", "중1"))));

        assertThat(issues).isEmpty();
    }

    @Test
    void 반_항목은_기존_반과_새_반_중_하나만() {
        List<RosterIssue> issues = RosterImportRules.inspect(List.of(
                draft("그린 월수반", s("김민준", null, null)), new Draft("2번째 반", false, List.of(s("이서연", null, null)))));

        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.field()).isEqualTo("classes[1]");
            assertThat(issue.code()).isEqualTo("VALIDATION");
            assertThat(issue.message()).isEqualTo("기존 반과 새 반 중 하나만 골라 주세요");
        });
    }

    @Test
    void 전체_학생이_500명을_넘으면_맨_앞에_오류() {
        List<Draft> drafts = new ArrayList<>();
        for (int c = 0; c < 11; c++) {
            List<Student> students = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                students.add(s("학생" + c + "-" + i, null, null));
            }
            drafts.add(new Draft(c + "반", c != 3, students)); // 3번 반은 고르기 오류도 함께
        }

        List<RosterIssue> issues = RosterImportRules.inspect(drafts);

        assertThat(issues.getFirst().field()).isEqualTo("classes");
        assertThat(issues.getFirst().message()).isEqualTo("학생은 모두 500명까지예요");
        assertThat(issues).extracting(RosterIssue::field).containsExactly("classes", "classes[3]");
    }

    @Test
    void 정확히_500명은_된다() {
        List<Draft> drafts = new ArrayList<>();
        for (int c = 0; c < 10; c++) {
            List<Student> students = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                students.add(s("학생" + c + "-" + i, null, null));
            }
            drafts.add(draft(c + "반", students.toArray(Student[]::new)));
        }

        assertThat(RosterImportRules.inspect(drafts)).isEmpty();
    }

    @Test
    void 다른_반에_같은_학생이면_뒤쪽_줄에_오류() {
        List<RosterIssue> issues = RosterImportRules.inspect(List.of(
                draft("그린 월수반", s("이서연", null, null), s("김민준", "A중", "중1")),
                draft("블루 화목반", s("박지호", null, null), s("김민준", "A중", "중1"))));

        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.field()).isEqualTo("classes[1].students[1].name");
            assertThat(issue.code()).isEqualTo("VALIDATION");
            assertThat(issue.message()).isEqualTo("김민준(A중·중1)이 그린 월수반과 블루 화목반에 모두 있어요. 한 반만 남겨 주세요");
        });
    }

    @Test
    void 같은_반_안에서_두_번이면_오류() {
        List<RosterIssue> issues =
                RosterImportRules.inspect(List.of(draft("그린 월수반", s("김민준", "A중", "중1"), s("김민준", "A중", "중1"))));

        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.field()).isEqualTo("classes[0].students[1].name");
            assertThat(issue.message()).isEqualTo("김민준(A중·중1)이 그린 월수반에 두 번 있어요. 한 줄만 남겨 주세요");
        });
    }

    @Test
    void 세_번째부터도_첫_줄과_비교한다() {
        List<RosterIssue> issues = RosterImportRules.inspect(List.of(
                draft("A반", s("김민준", null, null)),
                draft("B반", s("김민준", null, null)),
                draft("C반", s("김민준", null, null))));

        assertThat(issues)
                .extracting(RosterIssue::field, RosterIssue::message)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "classes[1].students[0].name", "김민준이 A반과 B반에 모두 있어요. 한 반만 남겨 주세요"),
                        org.assertj.core.groups.Tuple.tuple(
                                "classes[2].students[0].name", "김민준이 A반과 C반에 모두 있어요. 한 반만 남겨 주세요"));
    }

    @Test
    void 앞뒤_공백과_null_빈_문자열은_같은_학생으로_본다() {
        List<RosterIssue> issues = RosterImportRules.inspect(
                List.of(draft("그린 월수반", s("김민준 ", null, " 중1")), draft("블루 화목반", s(" 김민준", "", "중1"))));

        assertThat(issues)
                .singleElement()
                .extracting(RosterIssue::message)
                .isEqualTo("김민준(중1)이 그린 월수반과 블루 화목반에 모두 있어요. 한 반만 남겨 주세요");
    }

    @Test
    void 학교나_학년이_다르면_다른_학생() {
        List<RosterIssue> issues = RosterImportRules.inspect(
                List.of(draft("그린 월수반", s("김민준", "A중", "중1"), s("김민준", "A중", "중2"), s("김민준", "B중", "중1"))));

        assertThat(issues).isEmpty();
    }

    @Test
    void 받침_없는_이름과_반에는_가_와를_붙인다() {
        List<RosterIssue> issues = RosterImportRules.inspect(
                List.of(draft("블루", s("이서아", null, null)), draft("그린", s("이서아", null, null))));

        assertThat(issues)
                .singleElement()
                .extracting(RosterIssue::message)
                .isEqualTo("이서아가 블루와 그린에 모두 있어요. 한 반만 남겨 주세요");
    }

    @Test
    void 조사는_괄호_앞_이름과_반_이름에_맞춘다() {
        // 괄호 안 끝 글자(중2의 2, 중1의 1)를 따르면 "김민준(A중·중2)가", "이서아(중1)이"가 된다
        List<RosterIssue> issues = RosterImportRules.inspect(List.of(
                draft("그린(월수)", s("김민준", "A중", "중2"), s("이서아", null, "중1")),
                draft("블루(화목)", s("김민준", "A중", "중2"), s("이서아", null, "중1"))));

        assertThat(issues)
                .extracting(RosterIssue::message)
                .containsExactly(
                        "김민준(A중·중2)이 그린(월수)과 블루(화목)에 모두 있어요. 한 반만 남겨 주세요",
                        "이서아(중1)가 그린(월수)과 블루(화목)에 모두 있어요. 한 반만 남겨 주세요");
    }

    @Test
    void 받침_없는_반_이름에는_와() {
        List<RosterIssue> issues = RosterImportRules.inspect(
                List.of(draft("블루(화목)", s("김민준", null, null)), draft("그린", s("김민준", null, null))));

        assertThat(issues)
                .singleElement()
                .extracting(RosterIssue::message)
                .isEqualTo("김민준이 블루(화목)와 그린에 모두 있어요. 한 반만 남겨 주세요");
    }

    @Test
    void 빈_요청은_검사하지_않는다() {
        // 반 1~30개·반마다 1~50명은 Bean Validation이 400으로 먼저 막는다
        assertThat(RosterImportRules.inspect(Collections.emptyList())).isEmpty();
    }
}
