package com.jglearning.jg4seasons.wordtest.web;

import com.jglearning.jg4seasons.infra.security.Principals;
import com.jglearning.jg4seasons.wordtest.UploadService;
import com.jglearning.jg4seasons.wordtest.web.dto.UploadRequest;
import com.jglearning.jg4seasons.wordtest.web.dto.UploadResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 단어시험 사진 채점 학생 API(S-, 작업 지시 §D). 학생만. 학원·학생은 access 쿠키(JWT)에서만. 그 학원에서 기능이 꺼져 있으면 모두 404 FEATURE_DISABLED. 쓰기는
 * X-CSRF 필요
 */
@RestController
@RolesAllowed("STUDENT")
public class WordTestController {

    private final UploadService uploads;

    WordTestController(UploadService uploads) {
        this.uploads = uploads;
    }

    @Operation(
            summary = "단어시험 사진 업로드 주소",
            description = "S3에 바로 PUT할 서명 주소(5분)와 제출에 쓸 imageKey. PUT에는 headers를 그대로 넣고 쿠키는 보내지 않는다."
                    + " JPEG·PNG만(그 밖 400 INVALID_IMAGE). 크기(5MB)는 제출 때 확인한다."
                    + " 기능 꺼짐 404 FEATURE_DISABLED, 없는 회차·진도표에 없는 회차 404 NOT_FOUND, 사용 기간 아님 403 LICENSE_INACTIVE,"
                    + " 수업일 전 423 LESSON_LOCKED")
    @PostMapping("/api/v1/word-tests/uploads")
    public UploadResponse issueWordTestUpload(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UploadRequest request) {
        return uploads.issue(
                Principals.academyId(jwt), Principals.userId(jwt), request.lessonCode(), request.contentType());
    }
}
