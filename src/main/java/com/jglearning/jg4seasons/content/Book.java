package com.jglearning.jg4seasons.content;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

/** 책(시즌, 기준 데이터, 읽기 전용). */
@Entity
public class Book {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "level_id", nullable = false)
    private Level level;

    /** 1 겨울 · 2 봄 · 3 여름 · 4 가을 */
    @Column(nullable = false, columnDefinition = "tinyint")
    private Integer season;

    @Column(nullable = false)
    private String code;

    /** 권의 회차 수 = 진도표 행 수(블랙 28, 그 밖 24 — ADR-27) */
    @Column(name = "lesson_count", nullable = false, columnDefinition = "tinyint")
    private Integer lessonCount;

    protected Book() {}

    public Long getId() {
        return id;
    }

    public Level getLevel() {
        return level;
    }

    public Integer getSeason() {
        return season;
    }

    public String getCode() {
        return code;
    }

    public Integer getLessonCount() {
        return lessonCount;
    }
}
