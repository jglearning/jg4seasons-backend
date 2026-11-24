package com.jglearning.jg4seasons.infra.storage;

/**
 * 올라간 사진의 메타데이터.
 *
 * @param contentLength 바이트 수
 * @param contentType 저장된 형식(S3는 업로드 때 보낸 Content-Type), 모르면 null
 */
public record StoredPhoto(long contentLength, String contentType) {}
