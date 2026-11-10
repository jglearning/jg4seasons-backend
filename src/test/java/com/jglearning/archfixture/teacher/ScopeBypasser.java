package com.jglearning.archfixture.teacher;

import com.jglearning.jg4seasons.auth.api.AcademyStudents;
import java.util.List;
import java.util.function.BiFunction;

/** 위반: auth 밖에서 학원 학생 묶음을 만든다(경로의 학생 id를 확인 없이 범위로 쓰는 모양). 호출과 메서드 참조 둘 다. */
public class ScopeBypasser {
    public AcademyStudents call(long academyId, long studentId) {
        return AcademyStudents.of(academyId, List.of(studentId));
    }

    public BiFunction<Long, List<Long>, AcademyStudents> reference() {
        return AcademyStudents::of;
    }
}
