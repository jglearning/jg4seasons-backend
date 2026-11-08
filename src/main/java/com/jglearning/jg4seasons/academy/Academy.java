package com.jglearning.jg4seasons.academy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** 학원(테넌트 자체). 학원 범위 데이터가 아니라 AcademyScoped가 아니다. 입장·내 정보에 필요한 칸만 매핑한다. */
@Entity
public class Academy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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

    /** 운영 학원(active). 첫 학원 부트스트랩(InitialAcademyInitializer)만 쓴다 — 학원 등록 화면은 1차 범위 밖 */
    static Academy create(String name, String slug, String settings) {
        Academy academy = new Academy();
        academy.name = name;
        academy.slug = slug;
        academy.status = "active";
        academy.settings = settings;
        return academy;
    }

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
