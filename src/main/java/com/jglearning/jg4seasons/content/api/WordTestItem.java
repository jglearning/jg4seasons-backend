package com.jglearning.jg4seasons.content.api;

import java.util.List;

/**
 * 시험지 문항 하나(공용 콘텐츠). 채점은 answer·accepted·hint로 한다 — 셋을 함께 넘겨 허용 답을 빠뜨리지 않게.
 *
 * @param hint 인쇄된 첫 글자(a, p... t...), 없으면 null
 * @param accepted 허용 답, 없으면 빈 목록(불변)
 */
public record WordTestItem(long id, int no, String prompt, String answer, String hint, List<String> accepted) {

    public WordTestItem {
        accepted = List.copyOf(accepted);
    }
}
