package com.jglearning.jg4seasons.system.web;

import com.jglearning.jg4seasons.infra.security.Principals;
import com.jglearning.jg4seasons.system.SystemAcademyService;
import com.jglearning.jg4seasons.system.SystemLicenseService;
import com.jglearning.jg4seasons.system.SystemTalkService;
import com.jglearning.jg4seasons.system.web.dto.AcademyCreateRequest;
import com.jglearning.jg4seasons.system.web.dto.AcademyCreatedResponse;
import com.jglearning.jg4seasons.system.web.dto.AcademyDetailResponse;
import com.jglearning.jg4seasons.system.web.dto.AcademyListResponse;
import com.jglearning.jg4seasons.system.web.dto.LicenseItem;
import com.jglearning.jg4seasons.system.web.dto.LicenseSetRequest;
import com.jglearning.jg4seasons.system.web.dto.TalkStudentListResponse;
import com.jglearning.jg4seasons.system.web.dto.TalkStudentsSetRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 7.1~7.6 운영자 학원·교재 사용권(O-01~O-03, SYS-07)·학생별 프리토킹 허용(O-04, ADR 0040). 운영자만(ADR 0032 — 역할 계층 밖이라 관리자·교사는 403). 운영자는 학원이 없어 JWT에서 사용자만 읽는다.
 * 비활성 운영자의 남은 access는 401.
 */
@RestController
@RequestMapping("/api/v1/system/academies")
@RolesAllowed("SUPERADMIN")
public class SystemAcademyController {

    private final SystemAcademyService service;
    private final SystemLicenseService licenses;
    private final SystemTalkService talk;

    SystemAcademyController(SystemAcademyService service, SystemLicenseService licenses, SystemTalkService talk) {
        this.service = service;
        this.licenses = licenses;
        this.talk = talk;
    }

    @Operation(summary = "학원 목록", description = "id 순")
    @GetMapping
    public AcademyListResponse listAcademies(@AuthenticationPrincipal Jwt jwt) {
        return service.list(Principals.userId(jwt));
    }

    @Operation(
            summary = "학원 + 첫 관리자 만들기",
            description = "학원 주소(slug)가 있으면 409 ACADEMY_SLUG_TAKEN, 관리자 아이디가 있으면 409 LOGIN_ID_TAKEN — 둘 다 아무것도 남지 않는다."
                    + " licenseEnforced 필수, timezone은 IANA 이름(없으면 Asia/Seoul). X-CSRF 필요")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AcademyCreatedResponse createAcademy(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AcademyCreateRequest request) {
        return service.create(Principals.userId(jwt), request);
    }

    @Operation(
            summary = "학원 상세·사용권 현황",
            description = "사용권 줄이 있는 권만, 레벨·권 순. today·daysLeft는 그 학원 타임존 날짜 기준. 없는 학원 404")
    @GetMapping("/{id}")
    public AcademyDetailResponse getAcademy(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return licenses.detail(Principals.userId(jwt), id);
    }

    @Operation(
            summary = "교재 사용권 지정·변경",
            description = "학원·권마다 한 줄 — 없으면 만들고 있으면 고친다(줄이기 허용, 한도를 낮춰도 기존 학생은 그대로). 삭제는 없다(종료일을 앞당긴다)."
                    + " 종료일 < 시작일 400(endDate), 학원·권 없음 404. 감사 기록 LICENSE_SET. X-CSRF 필요")
    @PutMapping("/{id}/licenses/{bookCode}")
    public LicenseItem setLicense(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long id,
            @PathVariable String bookCode,
            @Valid @RequestBody LicenseSetRequest request) {
        return licenses.setLicense(Principals.userId(jwt), id, bookCode, request);
    }

    @Operation(
            summary = "학생별 프리토킹 허용 목록",
            description =
                    "재원생만, 반 이름 → 이름 순, 반 없는 학생(class null)은 맨 뒤. 페이지 없음. 학생이 쓰려면 학원 스위치(features.talk)도 켜져 있어야 한다."
                            + " 없는 학원 404")
    @GetMapping("/{id}/talk/students")
    public TalkStudentListResponse listTalkStudents(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return talk.list(Principals.userId(jwt), id);
    }

    @Operation(
            summary = "학생별 프리토킹 켜기·끄기",
            description = "한 명·여러 명 한 번에, 같은 값이면 그대로. 응답은 요청한 학생만(목록과 같은 순서·모양)."
                    + " 그 학원 재원생이 아닌 id가 하나라도 있으면 404이고 아무것도 바뀌지 않는다. 없는 학원 404."
                    + " 꺼진 학생의 프리토킹 API는 404 FEATURE_DISABLED. 감사 기록 TALK_ACCESS_SET. X-CSRF 필요")
    @PutMapping("/{id}/talk/students")
    public TalkStudentListResponse setTalkStudents(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long id,
            @Valid @RequestBody TalkStudentsSetRequest request) {
        return talk.set(Principals.userId(jwt), id, request);
    }
}
