package com.jglearning.jg4seasons.infra.security;

import com.jglearning.jg4seasons.infra.web.error.ErrorCode;
import com.jglearning.jg4seasons.infra.web.error.ErrorLogs;
import com.jglearning.jg4seasons.infra.web.error.Problems;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** MVC 밖(Security 필터)에서 Problem Details를 직접 쓴다. Boot의 JsonMapper를 써서 MVC 응답과 같은 모양(확장 필드 평탄화)이 된다. */
@Component
class ProblemResponseWriter {

    private final JsonMapper jsonMapper;

    ProblemResponseWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    void write(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode, Throwable cause)
            throws IOException {
        ErrorLogs.clientError(errorCode, request, cause);
        ProblemDetail problem = Problems.of(errorCode, null, request);
        response.setStatus(errorCode.status().value());
        // MVC 오류 응답과 같은 Content-Type(charset 없음). JSON은 UTF-8로 쓴다
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), problem);
    }
}
