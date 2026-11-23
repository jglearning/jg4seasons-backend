package com.jglearning.jg4seasons.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.clazz.ClassErrorCode;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import org.junit.jupiter.api.Test;

/**
 * 검사 오류 code는 반 모듈 code가 공개 api가 아니라 문자열로 든다(ADR 0036). 만들기는 같은 code를 반 모듈 enum으로 던지므로, enum 이름이 바뀌면 검사와
 * 만들기의 code가 어긋난다 — 이름이 같은지 고정한다(테스트 코드는 모듈 규칙 밖).
 */
class RosterIssueCodesTest {

    @Test
    void 공통_code_이름과_같다() {
        assertThat(CommonErrorCode.valueOf(RosterIssue.VALIDATION)).isEqualTo(CommonErrorCode.VALIDATION);
        assertThat(CommonErrorCode.valueOf(RosterIssue.NOT_FOUND)).isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void 반_모듈_code_이름과_같다() {
        assertThat(ClassErrorCode.valueOf(RosterIssue.CLASS_NOT_ACTIVE)).isEqualTo(ClassErrorCode.CLASS_NOT_ACTIVE);
        assertThat(ClassErrorCode.valueOf(RosterIssue.LICENSE_INACTIVE)).isEqualTo(ClassErrorCode.LICENSE_INACTIVE);
        assertThat(ClassErrorCode.valueOf(RosterIssue.LICENSE_SEATS_FULL)).isEqualTo(ClassErrorCode.LICENSE_SEATS_FULL);
    }
}
