package com.jglearning.jg4seasons.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 학원 학생 명단: 계정과 범위 묶음이 같은 학생이고, 한 명으로 좁히면 둘 다 그 학생만. */
class StudentRosterTest {

    private static StudentAccount account(long id, String name) {
        return new StudentAccount(id, name, null, null, AccountStatus.ACTIVE, null, null);
    }

    private static final StudentRoster ROSTER =
            new StudentRoster(List.of(account(2, "김학생"), account(1, "박학생")), AcademyStudents.of(7, List.of(2L, 1L)));

    @Test
    void 한_명으로_좁히면_계정과_묶음이_그_학생만() {
        StudentRoster one = ROSTER.only(1).orElseThrow();

        assertThat(one.accounts()).extracting(StudentAccount::name).containsExactly("박학생");
        assertThat(one.students().ids()).containsExactly(1L);
        assertThat(one.students().academyId()).isEqualTo(7);
    }

    @Test
    void 명단에_없는_학생이면_빈_값() {
        assertThat(ROSTER.only(3)).isEmpty();
    }

    @Test
    void 계정과_묶음의_학생이_다르면_만들_수_없다() {
        assertThatThrownBy(() -> new StudentRoster(List.of(account(1, "박학생")), AcademyStudents.of(7, List.of(2L))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
