package com.jglearning.jg4seasons.teacher.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 4.6 진도표 한 행 수정. 보낼 칸만, 하나도 없으면 400. 회차 순서 바꾸기·보강(날짜 추가)은 1차에 없다(lessonCode 칸 없음).
 */
public record SessionUpdateRequest(
        @Schema(description = "새 수업일(학원 날짜). 지금 날짜와 같으면 날짜 변경 아님")
        LocalDate scheduledDate,

        @Schema(description = "메모 100자 이하. 빈 문자열이면 지움") @Size(max = 100)
        String note,

        @Schema(description = "true면 뒤 수업들을 새 날짜의 다음 수업일(수업 요일, 휴강 날짜 제외)부터 차례로 다시 놓는다. 날짜 변경이 없으면 무시. 기본 false")
        Boolean shiftFollowing) {

    /** 바꿀 값이 하나라도 있는가(shiftFollowing만으로는 바뀌는 것이 없다) */
    public boolean hasChange() {
        return scheduledDate != null || note != null;
    }
}
