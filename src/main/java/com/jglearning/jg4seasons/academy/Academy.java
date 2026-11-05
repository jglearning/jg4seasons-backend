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

    /** 'active' · 'suspended'. 정지된 학원은 입장·세션 갱신이 안 된다 */
    @Column(nullable = false, columnDefinition = "enum('active','suspended')")
    private String status;

    /** JSON 문자열 또는 null. 지금 읽는 키는 timezone(없으면 Asia/Seoul) */
    @Column(columnDefinition = "json")
    private String settings;

    protected Academy() {}

    String getSettings() {
        return settings;
    }

    public boolean isActive() {
        return "active".equals(status);
    }

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
