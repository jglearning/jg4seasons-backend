package com.jglearning.jg4seasons.auth.api;

import java.util.List;
import java.util.Optional;

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

    /** 명단의 학생 한 명만(계정·묶음 모두). 명단에 없으면 빈 값 — 호출자는 404로. 확인과 좁히기가 한 번이라 확인을 빠뜨릴 길이 없다 */
    public Optional<StudentRoster> only(long studentId) {
        return accounts.stream()
                .filter(account -> account.id() == studentId)
                .findFirst()
                .map(account -> new StudentRoster(List.of(account), students.only(studentId)));
    }
}
