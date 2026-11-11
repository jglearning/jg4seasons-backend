package com.jglearning.jg4seasons.academy;

import com.jglearning.jg4seasons.infra.persistence.AcademyScoped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** 학원·권 사용권(ADR 0031). 학원·권마다 한 줄 — 운영자가 만들고 고친다(7.4, 줄이기 허용·삭제 없음). */
@Entity
@Table(name = "book_license")
class BookLicense implements AcademyScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "academy_id", nullable = false)
    private Long academyId;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    /** 학원 타임존 날짜, 포함 */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** 학원 타임존 날짜, 포함 */
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** 학생 수 한도 */
    @Column(nullable = false)
    private int seats;

    protected BookLicense() {}

    long getBookId() {
        return bookId;
    }

    @Override
    public Long getAcademyId() {
        return academyId;
    }

    LocalDate getStartDate() {
        return startDate;
    }

    LocalDate getEndDate() {
        return endDate;
    }

    int getSeats() {
        return seats;
    }
}
