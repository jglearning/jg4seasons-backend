package com.jglearning.jg4seasons.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 학원 학생임이 확인된 id 묶음. 만드는 곳 제한은 ArchitectureTest가 본다. */
class AcademyStudentsTest {

    @Test
    void 중복은_첫_순서로_한_번() {
        AcademyStudents students = AcademyStudents.of(7, List.of(30L, 10L, 30L, 20L));

        assertThat(students.academyId()).isEqualTo(7);
        assertThat(students.ids()).containsExactly(30L, 10L, 20L);
        assertThat(students.contains(10)).isTrue();
        assertThat(students.contains(99)).isFalse();
        assertThat(students.isEmpty()).isFalse();
    }

    @Test
    void 만든_뒤_원본을_바꿔도_묶음은_그대로이고_목록은_바꿀_수_없다() {
        List<Long> source = new ArrayList<>(List.of(1L, 2L));
        AcademyStudents students = AcademyStudents.of(7, source);
        source.add(3L);

        assertThat(students.ids()).containsExactly(1L, 2L);
        assertThatThrownBy(() -> students.ids().add(3L)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void 한_명으로_좁히기는_묶음_안_학생만() {
        AcademyStudents students = AcademyStudents.of(7, List.of(1L, 2L));

        AcademyStudents one = students.only(2);

        assertThat(one.academyId()).isEqualTo(7);
        assertThat(one.ids()).containsExactly(2L);
        assertThatThrownBy(() -> students.only(3))
                .as("확인되지 않은 id로 좁히면 범위를 우회한다")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 빈_묶음() {
        assertThat(AcademyStudents.of(7, List.of()).isEmpty()).isTrue();
    }
}
