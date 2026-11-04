package com.jglearning.jg4seasons.clazz;

import com.jglearning.jg4seasons.infra.persistence.AcademyScoped;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 반(class 테이블). 입장에 필요한 칸만 매핑한다. 진도·요일 등은 반 관리 이슈에서. */
@Entity
@Table(name = "`class`")
public class Clazz implements AcademyScoped {

    @Id
    private Long id;

    @Column(name = "academy_id", nullable = false)
    private Long academyId;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(nullable = false)
    private String name;

    @Column(name = "access_code", nullable = false, columnDefinition = "char(8)")
    private String accessCode;

    @Convert(converter = ClassStatus.DbConverter.class)
    @Column(nullable = false, columnDefinition = "enum('active','finished','archived')")
    private ClassStatus status;

    protected Clazz() {}

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

    public String getAccessCode() {
        return accessCode;
    }

    public ClassStatus getStatus() {
        return status;
    }
}
