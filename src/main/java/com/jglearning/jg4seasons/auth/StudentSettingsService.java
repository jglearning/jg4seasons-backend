package com.jglearning.jg4seasons.auth;

import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import com.jglearning.jg4seasons.auth.persistence.UserSettingStore;
import com.jglearning.jg4seasons.auth.web.dto.StudentSettingsPatch;
import com.jglearning.jg4seasons.auth.web.dto.StudentSettingsResponse;
import com.jglearning.jg4seasons.content.api.PassageVoices;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 1.8·1.9 학생 설정(ADR 0035). 사용자·학원은 access JWT(sub·aca)로만 정한다 — 요청 값으로 받지 않는다.
 *
 * <p>토큰만 믿지 않는다: 그 학원의 활성 학생이고 학원이 정지가 아닐 때만 읽고 쓴다(내 정보 1.7과 같은 기준, 아니면 401). 퇴원한 학생의 남은 access로는 쓰이지 않는다.
 */
@Service
public class StudentSettingsService {

    private final UserRepository users;
    private final AcademyDirectory academies;
    private final UserSettingStore settings;

    StudentSettingsService(UserRepository users, AcademyDirectory academies, UserSettingStore settings) {
        this.users = users;
        this.academies = academies;
        this.settings = settings;
    }

    /** 저장한 적이 없으면 모든 칸이 null이다. 조회는 행을 만들지 않는다 */
    @Transactional(readOnly = true)
    public StudentSettingsResponse get(long userId, Long academyId) {
        requireActiveStudent(userId, academyId);
        return current(academyId, userId);
    }

    /**
     * 보낸 칸만 바꾸고 설정 전체를 돌려준다. 빈 본문은 아무것도 바꾸지 않고 행도 만들지 않는다. 멱등 키 없음 — 같은 본문을 다시 보내도 결과가 같다. 두 기기가 동시에 바꾸면 나중에
     * 쓴 값이 남는다(충돌을 알리지 않는다).
     */
    @Transactional
    public StudentSettingsResponse patch(long userId, Long academyId, StudentSettingsPatch patch) {
        requireActiveStudent(userId, academyId);
        if (patch.voicePresent()) {
            String voice = patch.getVoice();
            if (voice != null && !PassageVoices.isValid(voice)) {
                throw ApiException.validation("voice", "목소리를 다시 골라 주세요");
            }
            settings.saveVoice(academyId, userId, voice);
        }
        return current(academyId, userId);
    }

    private StudentSettingsResponse current(long academyId, long userId) {
        return settings.find(academyId, userId)
                .map(row -> new StudentSettingsResponse(row.voice()))
                .orElseGet(() -> new StudentSettingsResponse(null));
    }

    private void requireActiveStudent(long userId, Long academyId) {
        if (academyId == null) { // 학원 없는 토큰(운영자)은 역할 검사에서 이미 403 — 여기까지 오면 토큰이 이상한 것
            throw new ApiException(CommonErrorCode.UNAUTHORIZED);
        }
        users.findByAcademyIdAndId(academyId, userId)
                .filter(User::isActive)
                .filter(found -> found.getRole() == UserRole.STUDENT)
                .filter(found -> academies.isActive(academyId))
                .orElseThrow(() -> new ApiException(CommonErrorCode.UNAUTHORIZED));
    }
}
