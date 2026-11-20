package com.jglearning.jg4seasons.auth.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 1.9 학생 설정 부분 수정(ADR 0035). 보낸 칸만 바꾼다.
 *
 * <p>레코드가 아니라 setter가 있는 클래스인 이유: "보내지 않음"({@code {}})과 "지움"({@code {"voice": null}})을 구분해야 한다. 레코드는 둘 다 null로
 * 받는다. Jackson은 본문에 있는 칸만 setter를 부르므로(null 포함) 불렸는지를 적어 둔다. 설정이 늘면 칸마다 같은 짝(값 + 보냄 여부)을 더한다.
 */
public final class StudentSettingsPatch {

    private String voice;
    private boolean voicePresent;

    @Schema(
            nullable = true,
            allowableValues = {"us_w", "us_m", "uk_w", "uk_m"},
            description = "고른 지문 목소리. 보내지 않으면 그대로 두고, null이면 비운다")
    public String getVoice() {
        return voice;
    }

    public void setVoice(String voice) {
        this.voice = voice;
        this.voicePresent = true;
    }

    /** 본문에 voice 칸이 있었는가(null 포함) */
    @JsonIgnore
    @Schema(hidden = true)
    public boolean voicePresent() {
        return voicePresent;
    }
}
