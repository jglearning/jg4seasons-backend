package com.jglearning.jg4seasons.clazz;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.LocalDate;

/**
 * 반 소속. academy_id 칸이 없어 AcademyScoped가 아니다. 반(Clazz)을 학원 범위로 먼저 확인한 뒤에만 조회한다
 * (ClassDirectoryService.isCurrentMember).
 */
@Entity
public class Enrollment {

    @Id
    private Long id;

    @Column(name = "class_id", nullable = false)
    private Long classId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "joined_at", nullable = false)
    private LocalDate joinedAt;

    /** null = 현재 소속 */
    @Column(name = "left_at")
    private LocalDate leftAt;

    protected Enrollment() {}

    public Long getId() {
        return id;
    }

    public Long getClassId() {
        return classId;
    }

    public Long getStudentId() {
        return studentId;
    }

    public LocalDate getJoinedAt() {
        return joinedAt;
    }

    public LocalDate getLeftAt() {
        return leftAt;
    }
}
