package com.jglearning.jg4seasons.clazz;

import com.jglearning.jg4seasons.clazz.api.ClassStatus;
import com.jglearning.jg4seasons.infra.persistence.AcademyScoped;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/** 반(class 테이블). 권·개강일·요일은 만들 때만 정한다(바꾸면 진도표와 어긋남 — 새 반으로). */
@Entity
@Table(name = "`class`")
public class Clazz implements AcademyScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "academy_id", nullable = false)
    private Long academyId;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(nullable = false)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** 월~일 7자 비트 문자열(Weekdays) */
    @Column(nullable = false, columnDefinition = "varchar(7)")
    private String weekdays;

    @Column(name = "access_code", nullable = false, columnDefinition = "char(8)")
    private String accessCode;

    @Column(name = "code_issued_at", nullable = false)
    private Instant codeIssuedAt;

    @Column(name = "allow_future_lessons", nullable = false)
    private boolean allowFutureLessons;

    @Convert(converter = ClassStatusConverter.class)
    @Column(nullable = false, columnDefinition = "enum('active','finished','archived')")
    private ClassStatus status;

    protected Clazz() {}

    /** 새 활성 반. 이름·요일 검사와 코드 발급은 호출자(ClassManagementService)가 끝낸 값 */
    static Clazz create(
            long academyId,
            long bookId,
            String name,
            LocalDate startDate,
            Weekdays weekdays,
            String accessCode,
            Instant codeIssuedAt) {
        Clazz clazz = new Clazz();
        clazz.academyId = academyId;
        clazz.bookId = bookId;
        clazz.name = name;
        clazz.startDate = startDate;
        clazz.weekdays = weekdays.bits();
        clazz.accessCode = accessCode;
        clazz.codeIssuedAt = codeIssuedAt;
        clazz.allowFutureLessons = false;
        clazz.status = ClassStatus.ACTIVE;
        return clazz;
    }

    void rename(String name) {
        this.name = name;
    }

    void changeStatus(ClassStatus status) {
        this.status = status;
    }

    void allowFutureLessons(boolean allow) {
        this.allowFutureLessons = allow;
    }

    void reissueAccessCode(String accessCode, Instant issuedAt) {
        this.accessCode = accessCode;
        this.codeIssuedAt = issuedAt;
    }

    public Long getId() {
        return id;
    }

    @Override
    public Long getAcademyId() {
        return academyId;
    }

    public Long getBookId() {
        return bookId;
    }

    public String getName() {
        return name;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    Weekdays getWeekdays() {
        return Weekdays.fromBits(weekdays);
    }

    public String getAccessCode() {
        return accessCode;
    }

    public Instant getCodeIssuedAt() {
        return codeIssuedAt;
    }

    public boolean isAllowFutureLessons() {
        return allowFutureLessons;
    }

    public ClassStatus getStatus() {
        return status;
    }
}
