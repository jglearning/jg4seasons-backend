package com.jglearning.jg4seasons.clazz.api;

import java.util.List;

/** 반 이동 결과. fromClassIds = 이번에 소속을 끝낸 반(id 순, 없으면 빈 목록 — 소속이 없던 학생). 감사 로그 detail에 쓴다. */
public record MoveResult(List<Long> fromClassIds, long toClassId) {

    public MoveResult {
        fromClassIds = List.copyOf(fromClassIds);
    }
}
