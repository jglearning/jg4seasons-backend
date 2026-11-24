package com.jglearning.jg4seasons.wordtest;

import com.jglearning.jg4seasons.infra.storage.PhotoStorage;
import com.jglearning.jg4seasons.infra.storage.UploadTicket;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.wordtest.web.dto.UploadResponse;
import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * D-1 업로드 주소 발급. 검사는 기능 → 회차 → 열림 → 형식까지(문항·하루 횟수는 제출에서). 주소만 만들고 DB에는 아무것도 쓰지 않는다 — 올라간 사진은 제출 때
 * HeadObject로 확인한다(presigned PUT은 크기 상한을 서명에 걸 수 없음)
 */
@Service
@Transactional(readOnly = true)
public class UploadService {

    static final Duration UPLOAD_TTL = Duration.ofMinutes(5);

    private final WordTestAccess access;
    private final PhotoStorage storage;

    UploadService(WordTestAccess access, PhotoStorage storage) {
        this.access = access;
        this.storage = storage;
    }

    public UploadResponse issue(long academyId, long studentId, String lessonCode, String contentType) {
        access.requireOpenLesson(academyId, studentId, lessonCode);
        String key = ImageKeys.newKey(academyId, studentId, contentType)
                .orElseThrow(() -> new ApiException(WordTestErrorCode.INVALID_IMAGE, "JPEG·PNG 사진만 올릴 수 있어요"));
        UploadTicket ticket = storage.presignPut(key, contentType, UPLOAD_TTL);
        return new UploadResponse(ticket.uploadUrl().toString(), key, ticket.headers(), ticket.expiresAt());
    }
}
