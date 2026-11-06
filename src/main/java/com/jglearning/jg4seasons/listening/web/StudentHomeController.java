package com.jglearning.jg4seasons.listening.web;

import com.jglearning.jg4seasons.infra.security.Principals;
import com.jglearning.jg4seasons.listening.StudentHomeService;
import com.jglearning.jg4seasons.listening.web.dto.ClassLessonsResponse;
import com.jglearning.jg4seasons.listening.web.dto.StudentHomeResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 2.1 학생 홈(S-03)·2.2 회차 목록(S-04). 학생만. 학원·학생은 access 쿠키(JWT)에서만. */
@RestController
@RolesAllowed("STUDENT")
public class StudentHomeController {

    private final StudentHomeService service;

    StudentHomeController(StudentHomeService service) {
        this.service = service;
    }

    @Operation(
            summary = "학생 홈",
            description =
                    "오늘 수업(수업일이 지난 회차 중 가장 늦은 것)·밀린 수업(ADR-24)·오늘 단어·다음 수업·통계." + " 오늘 = 학원 타임존 날짜. 현재 소속 반이 없으면 404")
    @GetMapping("/api/v1/student/home")
    public StudentHomeResponse home(@AuthenticationPrincipal Jwt jwt) {
        return service.home(Principals.academyId(jwt), Principals.userId(jwt));
    }

    @Operation(
            summary = "회차 목록",
            description = "묶음(8회차씩)별 회차와 상태(completed·today·overdue·open·locked). 내가 지금 소속된 active 반만,"
                    + " 다른 학원·남의 반·퇴원한 반은 404")
    @GetMapping("/api/v1/student/classes/{classId}/lessons")
    public ClassLessonsResponse lessons(@AuthenticationPrincipal Jwt jwt, @PathVariable long classId) {
        return service.lessons(Principals.academyId(jwt), Principals.userId(jwt), classId);
    }
}
