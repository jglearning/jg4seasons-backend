package com.jglearning.jg4seasons.infra.storage;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

/**
 * 업로드 주소 발급 결과.
 *
 * @param headers PUT 요청에 그대로 넣을 헤더(불변)
 * @param expiresAt 주소 만료 시각(주입된 시계 기준)
 */
public record UploadTicket(URI uploadUrl, Map<String, String> headers, Instant expiresAt) {

    public UploadTicket {
        headers = Map.copyOf(headers);
    }
}
