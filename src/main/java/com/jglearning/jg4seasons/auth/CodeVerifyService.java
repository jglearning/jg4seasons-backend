package com.jglearning.jg4seasons.auth;

import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import com.jglearning.jg4seasons.auth.ratelimit.CodeGuessLimiter;
import com.jglearning.jg4seasons.auth.web.dto.CodeVerifyResponse;
import com.jglearning.jg4seasons.auth.web.dto.CodeVerifyResponse.BookInfo;
import com.jglearning.jg4seasons.auth.web.dto.CodeVerifyResponse.ClassInfo;
import com.jglearning.jg4seasons.auth.web.dto.CodeVerifyResponse.LevelInfo;
import com.jglearning.jg4seasons.clazz.api.ClassEntry;
import com.jglearning.jg4seasons.content.api.BookDirectory;
import com.jglearning.jg4seasons.content.api.BookSummary;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 1.1 가입 코드로 반 확인. 학생 명단은 내려주지 않는다(ADR-22). */
@Service
@Transactional(readOnly = true)
public class CodeVerifyService {

    private final CodeGuessLimiter codeGuessLimiter;
    private final EnterableClasses enterableClasses;
    private final AcademyDirectory academies;
    private final BookDirectory books;

    CodeVerifyService(
            CodeGuessLimiter codeGuessLimiter,
            EnterableClasses enterableClasses,
            AcademyDirectory academies,
            BookDirectory books) {
        this.codeGuessLimiter = codeGuessLimiter;
        this.enterableClasses = enterableClasses;
        this.academies = academies;
        this.books = books;
    }

    /** 반이 없거나 비활성이거나 학원이 정지면 404 INVALID_CODE. IP당 1분 10회를 넘으면 429 RATE_LIMITED */
    public CodeVerifyResponse verify(String rawCode, String clientIp) {
        codeGuessLimiter.acquire(clientIp);
        ClassEntry clazz = enterableClasses
                .find(rawCode)
                .orElseThrow(() -> new ApiException(AuthErrorCode.INVALID_CODE, "가입 코드를 다시 확인해 주세요"));
        BookSummary book = books.summaryOf(clazz.bookId());
        return new CodeVerifyResponse(new ClassInfo(
                clazz.id(),
                clazz.name(),
                academies.nameOf(clazz.academyId()),
                new LevelInfo(book.levelCode(), book.levelNameKo(), book.levelColorHex()),
                new BookInfo(book.bookCode(), book.season())));
    }
}
