package com.jglearning.jg4seasons.content;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** 레벨(기준 데이터, 읽기 전용). 표시에 필요한 칸만 매핑한다. */
@Entity
public class Level {

    @Id
    private Long id;

    @Column(nullable = false)
    private String code;

    @Column(name = "name_ko", nullable = false)
    private String nameKo;

    @Column(name = "color_hex", nullable = false, columnDefinition = "char(7)")
    private String colorHex;

    protected Level() {}

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getNameKo() {
        return nameKo;
    }

    public String getColorHex() {
        return colorHex;
    }
}
