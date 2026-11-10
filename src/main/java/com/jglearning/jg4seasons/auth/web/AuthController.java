package com.jglearning.jg4seasons.auth.web;

import com.jglearning.jg4seasons.auth.CodeVerifyService;
import com.jglearning.jg4seasons.auth.SessionService;
import com.jglearning.jg4seasons.auth.SessionService.RefreshResult;
import com.jglearning.jg4seasons.auth.StaffLoginService;
import com.jglearning.jg4seasons.auth.StudentLoginService;
import com.jglearning.jg4seasons.auth.StudentLoginService.LoginResult;
import com.jglearning.jg4seasons.auth.ratelimit.ClientIpResolver;
import com.jglearning.jg4seasons.auth.token.AuthCookies;
import com.jglearning.jg4seasons.auth.web.dto.CodeVerifyRequest;
import com.jglearning.jg4seasons.auth.web.dto.CodeVerifyResponse;
import com.jglearning.jg4seasons.auth.web.dto.MeResponse;
import com.jglearning.jg4seasons.auth.web.dto.StaffLoginRequest;
import com.jglearning.jg4seasons.auth.web.dto.StaffLoginResponse;
import com.jglearning.jg4seasons.auth.web.dto.StudentLoginRequest;
import com.jglearning.jg4seasons.auth.web.dto.StudentLoginResponse;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 학생 입장과 세션(03 API명세 §1). 토큰은 Set-Cookie로만 나간다(ADR-18). */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final CodeVerifyService codeVerifyService;
    private final StudentLoginService studentLoginService;
    private final SessionService sessionService;
    private final StaffLoginService staffLoginService;
    private final ClientIpResolver clientIps;
    private final AuthCookies cookies;

    AuthController(
            CodeVerifyService codeVerifyService,
            StudentLoginService studentLoginService,
            SessionService sessionService,
            StaffLoginService staffLoginService,
            ClientIpResolver clientIps,
            AuthCookies cookies) {
        this.codeVerifyService = codeVerifyService;
        this.studentLoginService = studentLoginService;
        this.sessionService = sessionService;
        this.staffLoginService = staffLoginService;
        this.clientIps = clientIps;
        this.cookies = cookies;
    }

    @Operation(
            summary = "가입 코드로 반 확인(S-01 입력 중 표시용)",
            description = "IP당 1분에 10회. 반이 없거나 비활성이거나 학원이 정지면 404 INVALID_CODE, 넘으면 429 RATE_LIMITED. CSRF 검사 없음")
    @PermitAll
    @PostMapping("/code/verify")
    public CodeVerifyResponse verify(@Valid @RequestBody CodeVerifyRequest request, HttpServletRequest http) {
        return codeVerifyService.verify(request.code(), clientIps.resolve(http));
    }

    @Operation(
            summary = "가입 코드 + 개인 코드로 학생 입장",
            description =
                    "성공하면 Set-Cookie access·refresh·csrf(운영은 음원용 CloudFront-Policy·Signature·Key-Pair-Id도 — Path=/audio/, 2시간)."
                            + " 틀리면 401 INVALID_CREDENTIALS(어느 쪽이 틀렸는지 구분하지 않음),"
                            + " (반, IP) 5회 실패면 10분 동안 423 LOCKED(확장 필드 until). CSRF 검사 없음")
    @PermitAll
    @PostMapping("/student/login")
    public ResponseEntity<StudentLoginResponse> studentLogin(
            @Valid @RequestBody StudentLoginRequest request, HttpServletRequest http) {
        LoginResult result = studentLoginService.login(
                request.code(),
                request.personalCode(),
                clientIps.resolve(http),
                http.getHeader(HttpHeaders.USER_AGENT));
        return withCookies(ResponseEntity.ok(), cookies.issue(result.tokens())).body(result.body());
    }

    @Operation(
            summary = "교사·관리자 로그인",
            description =
                    "성공하면 Set-Cookie access·refresh(14일)·csrf(운영은 음원용 CloudFront-* 3개도). 틀리면 401 INVALID_CREDENTIALS(없는 아이디와 틀린 비밀번호를"
                            + " 구분하지 않음), 같은 계정 5회 실패면 10분 동안 423 LOCKED(확장 필드 until). 시도는 IP당 1분 10회(429, 성공·실패 모두)."
                            + " 아이디는 대소문자 구분 없음. CSRF 검사 없음")
    @PermitAll
    @PostMapping("/staff/login")
    public ResponseEntity<StaffLoginResponse> staffLogin(
            @Valid @RequestBody StaffLoginRequest request, HttpServletRequest http) {
        StaffLoginService.LoginResult result = staffLoginService.login(
                request.loginId(), request.password(), clientIps.resolve(http), http.getHeader(HttpHeaders.USER_AGENT));
        return withCookies(ResponseEntity.ok(), cookies.issue(result.tokens())).body(result.body());
    }

    @Operation(
            summary = "세션 갱신",
            description =
                    "요청 바디 없음. refresh 쿠키로 새 access·refresh·csrf(운영은 음원용 CloudFront-* 3개도, 기한 2시간 새로)를 발급한다(refresh 회전). 쿠키가 없거나 무효면 401. CSRF 검사 없음."
                            + " 회전된 지 10초 안의 옛 refresh는 200(쿠키 그대로 — 다른 탭이 받은 쿠키를 같이 씀), 그 뒤에 오면 그 사용자 refresh를 모두 끊고 401")
    @PermitAll
    @PostMapping("/refresh")
    public ResponseEntity<MeResponse> refresh(
            @Parameter(hidden = true) @CookieValue(name = AuthCookies.REFRESH, required = false) String refresh,
            HttpServletResponse http) {
        RefreshResult result = sessionService.refresh(refresh);
        if (result instanceof RefreshResult.Rotated rotated) {
            return withCookies(ResponseEntity.ok(), cookies.issue(rotated.tokens()))
                    .body(rotated.body());
        }
        if (result instanceof RefreshResult.Reused reused) {
            return ResponseEntity.ok(reused.body());
        }
        // 쿠키 없음(MissingRequestCookieException이 400이 되지 않게 직접 확인)·무효 → 401 + 쿠키 3개 만료
        cookies.expireAll().forEach(cookie -> http.addHeader(HttpHeaders.SET_COOKIE, cookie.toString()));
        throw new ApiException(CommonErrorCode.UNAUTHORIZED, "다시 들어와 주세요");
    }

    @Operation(summary = "로그아웃", description = "refresh를 폐기하고 쿠키 3개(운영은 음원 쿠키 3개도)를 만료시킨다. 헤더 X-CSRF 필요")
    @ApiResponse(responseCode = "204", description = "로그아웃됨")
    @PermitAll
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Parameter(hidden = true) @CookieValue(name = AuthCookies.REFRESH, required = false) String refresh) {
        sessionService.logout(refresh);
        return withCookies(ResponseEntity.status(HttpStatus.NO_CONTENT), cookies.expireAll())
                .build();
    }

    private static ResponseEntity.BodyBuilder withCookies(
            ResponseEntity.BodyBuilder response, List<ResponseCookie> sent) {
        sent.forEach(cookie -> response.header(HttpHeaders.SET_COOKIE, cookie.toString()));
        return response;
    }
}
