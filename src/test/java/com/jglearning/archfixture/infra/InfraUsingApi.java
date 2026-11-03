package com.jglearning.archfixture.infra;

import com.jglearning.archfixture.listening.api.PlayCountQuery;

/** 위반: 공용 infra는 다른 모듈을 참조하지 않는다(api라도). */
public class InfraUsingApi {
    private final PlayCountQuery playCountQuery;

    public InfraUsingApi(PlayCountQuery playCountQuery) {
        this.playCountQuery = playCountQuery;
    }

    public int count() {
        return playCountQuery.playCount(1L, 1L);
    }
}
