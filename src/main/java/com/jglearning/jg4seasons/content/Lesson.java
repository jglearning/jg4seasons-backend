package com.jglearning.jg4seasons.content;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** 회차(Reading Day, 기준 데이터, 읽기 전용). 반 진도표가 쓰는 칸만 매핑한다. */
@Entity
public class Lesson {

    @Id
    private Long id;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    /** 자연키 L2-B1-05 */
    @Column(nullable = false)
    private String code;

    /** 권 안 진도표 순서(블랙 1~28, 그 밖 1~24). 교재 번호는 code 끝(07, 보충 S1) */
    @Column(nullable = false, columnDefinition = "tinyint")
    private Integer number;

    protected Lesson() {}

    public Long getId() {
        return id;
    }

    public Long getBookId() {
        return bookId;
    }

    public String getCode() {
        return code;
    }

    public Integer getNumber() {
        return number;
    }
}
