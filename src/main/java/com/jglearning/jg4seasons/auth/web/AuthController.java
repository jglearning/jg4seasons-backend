package com.jglearning.jg4seasons.auth.web;

import com.jglearning.jg4seasons.auth.CodeVerifyService;
import com.jglearning.jg4seasons.auth.StudentLoginService;
import com.jglearning.jg4seasons.auth.StudentLoginService.LoginResult;
import com.jglearning.jg4seasons.auth.ratelimit.ClientIpResolver;
import com.jglearning.jg4seasons.auth.token.AuthCookies;
import com.jglearning.jg4seasons.auth.web.dto.CodeVerifyRequest;
import com.jglearning.jg4seasons.auth.web.dto.CodeVerifyResponse;
import com.jglearning.jg4seasons.auth.web.dto.MeResponse;
import com.jglearning.jg4seasons.auth.web.dto.StudentLoginRequest;
import com.jglearning.jg4seasons.auth.web.dto.StudentLoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 학생 입장과 세션(03 API명세 §1). 토큰은 Set-Cookie로만 나간다(ADR-18). */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final CodeVerifyService codeVerifyService;
    private final StudentLoginService studentLoginService;
    private final ClientIpResolver clientIps;
    private final AuthCookies cookies;

    AuthController(
            CodeVerifyService codeVerifyService,
            StudentLoginService studentLoginService,
            ClientIpResolver clientIps,
            AuthCookies cookies) {
        this.codeVerifyService = codeVerifyService;
        this.studentLoginService = studentLoginService;
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
            description = "성공하면 Set-Cookie access·refresh·csrf. 틀리면 401 INVALID_CREDENTIALS(어느 쪽이 틀렸는지 구분하지 않음),"
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
            summary = "세션 갱신",
            description = "요청 바디 없음. refresh 쿠키로 새 access·refresh·csrf를 발급한다(refresh 회전). 쿠키가 없거나 무효면 401. CSRF 검사 없음")
    @PermitAll
    @PostMapping("/refresh")
    public MeResponse refresh() {
        throw new UnsupportedOperationException("Task 8");
    }

    @Operation(summary = "로그아웃", description = "refresh를 폐기하고 쿠키 3개를 만료시킨다. 헤더 X-CSRF 필요")
    @ApiResponse(responseCode = "204", description = "로그아웃됨")
    @PermitAll
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        throw new UnsupportedOperationException("Task 8");
    }

    private static ResponseEntity.BodyBuilder withCookies(
            ResponseEntity.BodyBuilder response, List<ResponseCookie> sent) {
        sent.forEach(cookie -> response.header(HttpHeaders.SET_COOKIE, cookie.toString()));
        return response;
    }
}
