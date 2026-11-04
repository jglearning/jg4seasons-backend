package com.jglearning.jg4seasons.auth;

import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import com.jglearning.jg4seasons.clazz.api.ClassDirectory;
import com.jglearning.jg4seasons.clazz.api.ClassEntry;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 가입 코드로 입장할 수 있는 반: 활성 반이고 학원이 정지되지 않았다. 가입 코드 확인(1.1)과 학생 입장(1.2)이 같은 판정을 쓴다 — 진입 경로가 늘어도
 * 정지 학원 필터를 빼먹지 않게.
 */
@Component
class EnterableClasses {

    private final ClassDirectory classes;
    private final AcademyDirectory academies;

    EnterableClasses(ClassDirectory classes, AcademyDirectory academies) {
        this.classes = classes;
        this.academies = academies;
    }

    /** @param rawCode 요청 그대로(형식은 DTO가 검사함). 앞뒤 공백을 없애고 대문자로 바꿔 찾는다 */
    Optional<ClassEntry> find(String rawCode) {
        return classes.findActiveByAccessCode(rawCode.strip().toUpperCase(Locale.ROOT))
                .filter(entry -> academies.isActive(entry.academyId()));
    }
}
