package com.jglearning.jg4seasons.auth.devseed;

import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import com.jglearning.jg4seasons.auth.AuthIdentity;
import com.jglearning.jg4seasons.auth.AuthIdentityRepository;
import com.jglearning.jg4seasons.auth.AuthProvider;
import com.jglearning.jg4seasons.auth.User;
import com.jglearning.jg4seasons.auth.UserRepository;
import com.jglearning.jg4seasons.auth.UserRole;
import com.jglearning.jg4seasons.auth.api.NewStaff;
import com.jglearning.jg4seasons.auth.api.StaffAccount;
import com.jglearning.jg4seasons.auth.api.StaffAccounts;
import com.jglearning.jg4seasons.auth.api.StaffRole;
import com.jglearning.jg4seasons.clazz.api.ClassDirectory;
import com.jglearning.jg4seasons.clazz.api.ClassTeachers;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 로컬 확인용 교사·관리자 로그인: 데모 학원의 기존 관리자에 `admin`, 새 교사 `teacher01`(데모 반 담당). DB에는 BCrypt 해시만 넣는다.
 *
 * <p>공개 저장소의 로컬 전용 값이라 평문을 여기 둔다(운영에는 이 시드가 없다). 이미 있으면 건너뛴다.
 */
public class StaffDevSeed {

    static final String DEMO_SLUG = "demo";
    static final String DEMO_ACCESS_CODE = "G7KM2XP4";
    static final String ADMIN_LOGIN_ID = "admin";
    static final String ADMIN_PASSWORD = "local-admin-1234";
    static final String TEACHER_LOGIN_ID = "teacher01";
    static final String TEACHER_PASSWORD = "local-teacher-1234";

    private final AcademyDirectory academies;
    private final UserRepository users;
    private final AuthIdentityRepository identities;
    private final PasswordEncoder passwordEncoder;
    private final StaffAccounts accounts;
    private final ClassDirectory classes;
    private final ClassTeachers classTeachers;
    private final Clock clock;

    public StaffDevSeed(
            AcademyDirectory academies,
            UserRepository users,
            AuthIdentityRepository identities,
            PasswordEncoder passwordEncoder,
            StaffAccounts accounts,
            ClassDirectory classes,
            ClassTeachers classTeachers,
            Clock clock) {
        this.academies = academies;
        this.users = users;
        this.identities = identities;
        this.passwordEncoder = passwordEncoder;
        this.accounts = accounts;
        this.classes = classes;
        this.classTeachers = classTeachers;
        this.clock = clock;
    }

    /** @return 새로 넣은 로그인 수단 수 */
    public int seed() {
        Optional<Long> demo = academies.findIdBySlug(DEMO_SLUG);
        if (demo.isEmpty()) {
            return 0;
        }
        long academyId = demo.get();
        return seedAdmin(academyId) + seedTeacher(academyId);
    }

    private int seedAdmin(long academyId) {
        List<User> admins = users.findByAcademyIdAndRoleOrderByIdAsc(academyId, UserRole.ADMIN);
        if (admins.isEmpty()
                || identities.existsByUserIdAndProvider(admins.get(0).getId(), AuthProvider.PASSWORD)
                || identities.existsByProviderAndIdentifier(AuthProvider.PASSWORD, ADMIN_LOGIN_ID)) {
            return 0;
        }
        identities.save(AuthIdentity.password(
                admins.get(0).getId(), ADMIN_LOGIN_ID, passwordEncoder.encode(ADMIN_PASSWORD), clock.instant()));
        return 1;
    }

    private int seedTeacher(long academyId) {
        if (identities.existsByProviderAndIdentifier(AuthProvider.PASSWORD, TEACHER_LOGIN_ID)) {
            return 0;
        }
        StaffAccount teacher =
                accounts.create(academyId, new NewStaff("교사01", TEACHER_LOGIN_ID, TEACHER_PASSWORD, StaffRole.TEACHER));
        classes.findActiveByAccessCode(DEMO_ACCESS_CODE)
                .ifPresent(demoClass -> classTeachers.replace(academyId, teacher.id(), Set.of(demoClass.id())));
        return 1;
    }
}
