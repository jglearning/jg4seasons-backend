package com.jglearning.jg4seasons.auth.api;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 그 학원 학생임을 auth가 확인한 학생 id 묶음. 진도·단어 집계 표(track_progress·lesson_progress·word_study)에는 학원 칸이 없어 학생 id 목록이 곧 학원
 * 범위다 — 그 표를 읽는 api는 이 타입만 받아, 확인하지 않은 id(경로 값 등)를 넘길 길을 막는다. 만드는 것(of)은 auth 패키지에서만
 * (ArchitectureTest.학원_학생_묶음은_auth만_만든다). 다른 모듈은 받은 묶음을 좁히기만(only) 한다.
 */
public final class AcademyStudents {

    private final long academyId;
    private final List<Long> ids;

    private AcademyStudents(long academyId, List<Long> ids) {
        this.academyId = academyId;
        this.ids = ids;
    }

    /** auth 패키지에서만 부른다(아키텍처 규칙). 같은 id는 첫 순서로 한 번 */
    public static AcademyStudents of(long academyId, Collection<Long> ids) {
        return new AcademyStudents(academyId, List.copyOf(new LinkedHashSet<>(ids)));
    }

    public long academyId() {
        return academyId;
    }

    /** 수정할 수 없는 목록, 만든 순서 */
    public List<Long> ids() {
        return ids;
    }

    public boolean contains(long studentId) {
        return ids.contains(studentId);
    }

    public boolean isEmpty() {
        return ids.isEmpty();
    }

    /** 묶음 안 학생 한 명만. 묶음 밖이면 IllegalArgumentException — 확인하지 않은 id로 좁혀 범위를 우회하지 못하게 */
    public AcademyStudents only(long studentId) {
        if (!contains(studentId)) {
            throw new IllegalArgumentException("학원 학생 묶음에 없는 학생: " + studentId);
        }
        return new AcademyStudents(academyId, List.of(studentId));
    }

    @Override
    public String toString() {
        return "AcademyStudents[academyId=" + academyId + ", size=" + ids.size() + "]";
    }
}
