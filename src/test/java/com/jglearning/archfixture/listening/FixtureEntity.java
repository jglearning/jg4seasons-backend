package com.jglearning.archfixture.listening;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** 아키텍처 규칙 검증용 예제. 앱 기본 패키지 밖이라 엔티티 스캔에 잡히지 않는다. */
@Entity
public class FixtureEntity {
    @Id
    private Long id;
}
