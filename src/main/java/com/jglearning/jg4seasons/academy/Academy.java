package com.jglearning.jg4seasons.academy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** 학원(테넌트 자체). 학원 범위 데이터가 아니라 AcademyScoped가 아니다. 입장·내 정보에 필요한 칸만 매핑한다. */
@Entity
public class Academy {

    @Id
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String slug;

    protected Academy() {}

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }
}
