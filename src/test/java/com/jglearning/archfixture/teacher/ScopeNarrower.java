package com.jglearning.archfixture.teacher;

import com.jglearning.jg4seasons.auth.api.AcademyStudents;

/** 정상: auth에서 받은 묶음을 한 명으로 좁힌다(만들지 않음). */
public class ScopeNarrower {
    public AcademyStudents one(AcademyStudents roster, long studentId) {
        return roster.only(studentId);
    }
}
