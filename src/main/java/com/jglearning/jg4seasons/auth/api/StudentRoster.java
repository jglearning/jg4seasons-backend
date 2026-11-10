package com.jglearning.jg4seasons.auth.api;

import java.util.List;

/**
 * 그 학원 학생으로 확인된 명단: 계정(이름 순, 같으면 id 순)과 같은 학생들의 범위 묶음. 화면은 accounts를, 집계 조회는 students를 쓴다 — 둘은 같은 학생이다.
 */
public record StudentRoster(List<StudentAccount> accounts, AcademyStudents students) {
    public StudentRoster {
        accounts = List.copyOf(accounts);
        List<Long> accountIds = accounts.stream().map(StudentAccount::id).toList();
        if (!accountIds.equals(students.ids())) {
            throw new IllegalArgumentException("명단 계정과 범위 묶음의 학생이 다르다");
        }
    }
}
