package com.jglearning.jg4seasons.infra.web.error;

import com.jglearning.jg4seasons.infra.web.requestid.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Locale;
import org.springframework.http.ProblemDetail;

/**
 * 오류 응답 본문 만들기. type은 상대 경로 `/problems/{code 소문자-하이픈}`(공개 명세에 도메인을 넣지 않는다), instance는 요청 경로, 확장
 * 필드 code·requestId.
 */
public final class Problems {

    public static final String CODE = "code";
    public static final String REQUEST_ID = "requestId";
    public static final String ERRORS = "errors";

    private Problems() {}

    /** detail이 null이면 title을 그대로 쓴다 */
    public static ProblemDetail of(ErrorCode errorCode, String detail, HttpServletRequest request) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(errorCode.status(), detail != null ? detail : errorCode.title());
        decorate(problem, errorCode, request);
        return problem;
    }

    /** 이미 만들어진 ProblemDetail(프레임워크 예외)에 type·title·instance·code·requestId를 채운다 */
    static void decorate(ProblemDetail problem, ErrorCode errorCode, HttpServletRequest request) {
        problem.setType(URI.create(
                "/problems/" + errorCode.code().toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setTitle(errorCode.title());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty(CODE, errorCode.code());
        problem.setProperty(REQUEST_ID, RequestIds.current(request));
    }
}
