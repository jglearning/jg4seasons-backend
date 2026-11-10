package com.jglearning.archfixture.auth;

import com.jglearning.jg4seasons.auth.api.AcademyStudents;
import java.util.List;

/** 정상: auth 안에서 확인한 학생 id로 묶음을 만든다. 다른 모듈은 받은 묶음을 좁히기만 한다(teacher 예제). */
public class RosterMaker {
    public AcademyStudents confirmed(long academyId, List<Long> foundIds) {
        return AcademyStudents.of(academyId, foundIds);
    }
}
