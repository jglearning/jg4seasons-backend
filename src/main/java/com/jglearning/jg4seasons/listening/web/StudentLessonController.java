package com.jglearning.jg4seasons.listening.web;

import com.jglearning.jg4seasons.infra.security.Principals;
import com.jglearning.jg4seasons.listening.StudentLessonService;
import com.jglearning.jg4seasons.listening.web.dto.LessonDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 2.3 회차 상세(S-05). 학생만. 학원·학생은 access 쿠키(JWT)에서만 — 내 반 진도표에 없는 회차는 404, 아직 안 열린 회차는 423. */
@RestController
@RolesAllowed("STUDENT")
public class StudentLessonController {

    private final StudentLessonService service;

    StudentLessonController(StudentLessonService service) {
        this.service = service;
    }

    @Operation(
            summary = "회차 상세",
            description = "지문·문장·속도별 음원·문장 시각·들은 구간·완료 상태. 내 반 진도표에 없는 회차(다른 권 포함)는 404,"
                    + " 수업일 전(학원 타임존)·휴강만 있는 회차는 423 LESSON_LOCKED")
    @GetMapping("/api/v1/student/lessons/{lessonCode}")
    public LessonDetailResponse detail(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "회차 코드", example = "L2-B1-05")
                    @PathVariable
                    @Pattern(regexp = "^[A-Za-z0-9-]{1,10}$", message = "회차 코드 형식이 아니에요")
                    String lessonCode) {
        return service.detail(Principals.academyId(jwt), Principals.userId(jwt), lessonCode);
    }
}
