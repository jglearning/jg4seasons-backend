package com.jglearning.jg4seasons.auth;

import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import com.jglearning.jg4seasons.academy.api.BookLicenses;
import com.jglearning.jg4seasons.clazz.api.StudentClass;
import com.jglearning.jg4seasons.clazz.api.StudentClasses;
import com.jglearning.jg4seasons.content.api.BookDirectory;
import com.jglearning.jg4seasons.infra.audio.AudioScope;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인·refresh 때 이 사용자의 음원 쿠키 범위를 정한다(ADR 0031 결정 6). 규칙은 {@link AudioScopeRule}, 여기서는 규칙에 넣을 값만 읽는다: 학원 사용권 적용 여부,
 * 현재 반들의 권, 그 권의 사용권 상태(학원 날짜 — 학습 차단과 같은 판정).
 */
@Component
@Transactional(readOnly = true)
public class AudioScopes {

    private final AcademyDirectory academies;
    private final StudentClasses studentClasses;
    private final BookLicenses licenses;
    private final BookDirectory books;
    private final Clock clock;

    AudioScopes(
            AcademyDirectory academies,
            StudentClasses studentClasses,
            BookLicenses licenses,
            BookDirectory books,
            Clock clock) {
        this.academies = academies;
        this.studentClasses = studentClasses;
        this.licenses = licenses;
        this.books = books;
        this.clock = clock;
    }

    /** academyId는 운영자면 null */
    public AudioScope of(long userId, UserRole role, Long academyId) {
        if (role != UserRole.STUDENT) {
            return AudioScopeRule.of(role, false, List.of());
        }
        boolean enforced = academies.licenseEnforced(academyId);
        if (!enforced) {
            return AudioScopeRule.of(role, false, List.of());
        }
        LocalDate today = LocalDate.now(clock.withZone(academies.zoneOf(academyId)));
        List<String> activeBooks = studentClasses.currentClasses(academyId, userId).stream()
                .map(StudentClass::bookId)
                .distinct()
                .filter(bookId -> licenses.statusOf(academyId, bookId, today).allowsLearning())
                .map(bookId -> books.summaryOf(bookId).bookCode())
                .toList();
        return AudioScopeRule.of(role, true, activeBooks);
    }
}
