package com.jglearning.jg4seasons.auth.devseed;

import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import com.jglearning.jg4seasons.auth.AuthIdentity;
import com.jglearning.jg4seasons.auth.AuthIdentityRepository;
import com.jglearning.jg4seasons.auth.AuthProvider;
import com.jglearning.jg4seasons.auth.PersonalCodeHasher;
import com.jglearning.jg4seasons.auth.User;
import com.jglearning.jg4seasons.auth.UserRepository;
import com.jglearning.jg4seasons.auth.UserRole;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * 로컬 확인용 개인 코드: 데모 학원(slug demo)의 학생을 id 순으로 1001, 1002, …(학생01 = 1001). DB에는 HMAC만 넣는다 — 로그인 검증과 같은
 * PersonalCodeHasher를 쓴다.
 *
 * <p>공개 저장소의 로컬 전용 값이라 평문을 여기 둔다(운영에는 이 시드가 없다). 이미 개인 코드가 있는 학생은 건너뛴다.
 */
public class AuthDevSeed {

    static final String DEMO_SLUG = "demo";
    static final int FIRST_CODE = 1001;

    private final AcademyDirectory academies;
    private final UserRepository users;
    private final AuthIdentityRepository identities;
    private final PersonalCodeHasher hasher;
    private final Clock clock;

    public AuthDevSeed(
            AcademyDirectory academies,
            UserRepository users,
            AuthIdentityRepository identities,
            PersonalCodeHasher hasher,
            Clock clock) {
        this.academies = academies;
        this.users = users;
        this.identities = identities;
        this.hasher = hasher;
        this.clock = clock;
    }

    /** @return 새로 넣은 개인 코드 수 */
    public int seed() {
        Optional<Long> demo = academies.findIdBySlug(DEMO_SLUG);
        if (demo.isEmpty()) {
            return 0;
        }
        long academyId = demo.get();
        List<User> students = users.findByAcademyIdAndRoleOrderByIdAsc(academyId, UserRole.STUDENT);
        int inserted = 0;
        for (int i = 0; i < students.size(); i++) {
            User student = students.get(i);
            if (identities.existsByUserIdAndProvider(student.getId(), AuthProvider.PERSONAL_CODE)) {
                continue;
            }
            String code = Integer.toString(FIRST_CODE + i);
            identities.save(AuthIdentity.personalCode(student.getId(), hasher.hash(academyId, code), clock.instant()));
            inserted++;
        }
        return inserted;
    }
}
