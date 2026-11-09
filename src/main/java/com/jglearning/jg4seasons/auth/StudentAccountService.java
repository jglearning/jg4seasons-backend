package com.jglearning.jg4seasons.auth;

import com.jglearning.jg4seasons.auth.api.AccountStatus;
import com.jglearning.jg4seasons.auth.api.IssuedCode;
import com.jglearning.jg4seasons.auth.api.NewStudent;
import com.jglearning.jg4seasons.auth.api.StudentAccount;
import com.jglearning.jg4seasons.auth.api.StudentAccounts;
import com.jglearning.jg4seasons.auth.api.StudentChange;
import com.jglearning.jg4seasons.auth.api.StudentChangeResult;
import com.jglearning.jg4seasons.auth.token.RefreshTokenService;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 학생 계정과 개인 코드(ADR-22). 평문 코드는 IssuedCode로 돌려줄 뿐 필드·로그에 남기지 않는다.
 *
 * <p>코드 고르기: 한 묶음에서 이미 낸 코드와 묶음 학생들의 옛 코드, 그 학원에서 지금 쓰는 코드(HMAC으로 확인)를 피한다 — 옛 코드가 같은 묶음의 다른 학생에게
 * 넘어가면 새어 나간 옛 코드로 그 학생 계정에 들어갈 수 있다. refresh 끊기(killAllOf)는 영속성 컨텍스트를 비우므로 엔티티 변경을 다 끝낸 뒤 마지막에 부른다.
 */
@Service
@Transactional
class StudentAccountService implements StudentAccounts {

    static final int NAME_MAX = 30;
    static final int SCHOOL_MAX = 50;
    static final int GRADE_MAX = 10;

    private final UserRepository users;
    private final AuthIdentityRepository identities;
    private final PersonalCodeHasher hasher;
    private final RefreshTokenService refreshTokens;
    private final PersonalCodeGenerator generator;
    private final Clock clock;

    StudentAccountService(
            UserRepository users,
            AuthIdentityRepository identities,
            PersonalCodeHasher hasher,
            RefreshTokenService refreshTokens,
            PersonalCodeGenerator generator,
            Clock clock) {
        this.users = users;
        this.identities = identities;
        this.hasher = hasher;
        this.refreshTokens = refreshTokens;
        this.generator = generator;
        this.clock = clock;
    }

    @Override
    public List<IssuedCode> create(long academyId, List<NewStudent> students) {
        // 하나라도 틀리면 아무도 만들지 않게 먼저 모두 정리한다
        List<NewStudent> cleaned = students.stream()
                .map(s -> new NewStudent(
                        name(s.name()),
                        optional(s.school(), "school", SCHOOL_MAX),
                        optional(s.grade(), "grade", GRADE_MAX)))
                .toList();
        Instant now = issuedAt();
        CodePicker picker = new CodePicker(academyId, Set.of());
        List<IssuedCode> issued = new ArrayList<>(cleaned.size());
        for (NewStudent student : cleaned) {
            User user = users.save(User.student(academyId, student.name(), student.school(), student.grade()));
            String code = picker.next();
            identities.save(AuthIdentity.personalCode(user.getId(), hasher.hash(academyId, code), now));
            issued.add(new IssuedCode(user.getId(), user.getName(), code));
        }
        return issued;
    }

    @Override
    public List<IssuedCode> regenerate(long academyId, List<Long> requested) {
        // 같은 id를 두 번 발급하면 첫 평문은 이미 무효다 — 첫 순서만 남긴다
        List<Long> studentIds = List.copyOf(new LinkedHashSet<>(requested));
        Map<Long, User> found = studentsById(academyId, studentIds);
        if (found.size() != studentIds.size()) {
            throw notFound();
        }
        Map<Long, AuthIdentity> codes =
                identities.findByUserIdInAndProvider(found.keySet(), AuthProvider.PERSONAL_CODE).stream()
                        .collect(Collectors.toMap(AuthIdentity::getUserId, Function.identity()));
        Set<String> oldHashes =
                codes.values().stream().map(AuthIdentity::identifier).collect(Collectors.toSet());
        CodePicker picker = new CodePicker(academyId, oldHashes);
        Instant now = issuedAt();
        List<IssuedCode> issued = new ArrayList<>(studentIds.size());
        for (Long id : studentIds) {
            String code = picker.next();
            String hash = hasher.hash(academyId, code);
            AuthIdentity identity = codes.get(id);
            if (identity == null) {
                identities.save(AuthIdentity.personalCode(id, hash, now));
            } else {
                identity.reissuePersonalCode(hash, now);
            }
            issued.add(new IssuedCode(id, found.get(id).getName(), code));
        }
        // 마지막에: killAllOf는 flush 뒤 영속성 컨텍스트를 비운다
        studentIds.forEach(refreshTokens::killAllOf);
        return issued;
    }

    @Override
    @Transactional(readOnly = true)
    public StudentAccount find(long academyId, long studentId) {
        User user = users.findByAcademyIdAndId(academyId, studentId)
                .filter(StudentAccountService::isStudent)
                .orElseThrow(StudentAccountService::notFound);
        return toAccount(user, identities.findByUserIdAndProvider(user.getId(), AuthProvider.PERSONAL_CODE));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentAccount> findAll(long academyId, Collection<Long> studentIds) {
        if (studentIds.isEmpty()) {
            return List.of();
        }
        return toAccounts(
                users.findByAcademyIdAndRoleAndIdInOrderByNameAscIdAsc(academyId, UserRole.STUDENT, studentIds));
    }

    @Override
    public StudentChangeResult change(long academyId, long studentId, StudentChange change) {
        User user = users.findByAcademyIdAndIdForUpdate(academyId, studentId)
                .filter(StudentAccountService::isStudent)
                .orElseThrow(StudentAccountService::notFound);
        change.name().map(StudentAccountService::name).ifPresent(user::rename);
        // optional()은 빈 문자열을 null로 — 지우기
        change.school().ifPresent(s -> user.changeSchool(optional(s, "school", SCHOOL_MAX)));
        change.grade().ifPresent(g -> user.changeGrade(optional(g, "grade", GRADE_MAX)));
        boolean wasActive = user.isActive();
        change.status()
                .map(status -> status == AccountStatus.ACTIVE ? UserStatus.ACTIVE : UserStatus.INACTIVE)
                .ifPresent(user::changeStatus);
        boolean deactivated = wasActive && !user.isActive();
        StudentAccount account =
                toAccount(user, identities.findByUserIdAndProvider(user.getId(), AuthProvider.PERSONAL_CODE));
        if (deactivated) {
            refreshTokens.killAllOf(user.getId()); // 마지막에(영속성 컨텍스트를 비운다)
        }
        return new StudentChangeResult(account, deactivated);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentAccount> searchByName(long academyId, String namePart) {
        String part = namePart == null ? "" : namePart.strip();
        if (part.isEmpty()) {
            throw ApiException.validation("name", "이름을 입력해 주세요");
        }
        return toAccounts(users.findTop50ByAcademyIdAndRoleAndNameContainingOrderByNameAscIdAsc(
                academyId, UserRole.STUDENT, part));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> studentIds(long academyId) {
        return users.findIdsByAcademyIdAndRole(academyId, UserRole.STUDENT);
    }

    /** 한 요청 안에서 코드를 고른다: 이미 낸 코드·피할 저장값·학원에서 쓰는 코드를 피한다 */
    private final class CodePicker {
        private final long academyId;
        private final Set<String> avoidHashes;

        CodePicker(long academyId, Set<String> avoidHashes) {
            this.academyId = academyId;
            this.avoidHashes = new HashSet<>(avoidHashes);
        }

        String next() {
            String code = generator.next(candidate -> {
                String hash = hasher.hash(academyId, candidate);
                return avoidHashes.contains(hash)
                        || identities.existsByProviderAndIdentifier(AuthProvider.PERSONAL_CODE, hash);
            });
            avoidHashes.add(hasher.hash(academyId, code));
            return code;
        }
    }

    private Map<Long, User> studentsById(long academyId, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return users
                .findByAcademyIdAndRoleAndIdInOrderByNameAscIdAsc(academyId, UserRole.STUDENT, new LinkedHashSet<>(ids))
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private List<StudentAccount> toAccounts(List<User> found) {
        if (found.isEmpty()) {
            return List.of();
        }
        Map<Long, AuthIdentity> codes = identities
                .findByUserIdInAndProvider(found.stream().map(User::getId).toList(), AuthProvider.PERSONAL_CODE)
                .stream()
                .collect(Collectors.toMap(AuthIdentity::getUserId, Function.identity()));
        return found.stream()
                .map(user -> toAccount(user, Optional.ofNullable(codes.get(user.getId()))))
                .toList();
    }

    private static StudentAccount toAccount(User user, Optional<AuthIdentity> code) {
        return new StudentAccount(
                user.getId(),
                user.getName(),
                user.getSchool(),
                user.getGrade(),
                user.isActive() ? AccountStatus.ACTIVE : AccountStatus.INACTIVE,
                code.map(AuthIdentity::getIssuedAt).orElse(null),
                user.getLastLoginAt());
    }

    private static boolean isStudent(User user) {
        return user.getRole() == UserRole.STUDENT;
    }

    /** DATETIME은 초 단위 반올림 — 내림해 저장해 실제보다 늦은 시각이 되지 않게 */
    private Instant issuedAt() {
        return clock.instant().truncatedTo(ChronoUnit.SECONDS);
    }

    private static String name(String raw) {
        String name = raw == null ? "" : raw.strip();
        if (name.isEmpty() || name.length() > NAME_MAX) {
            throw ApiException.validation("name", "이름은 1~" + NAME_MAX + "자예요");
        }
        return name;
    }

    /** 앞뒤 공백 제거, 비면 null */
    private static String optional(String raw, String field, int max) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.strip();
        if (value.length() > max) {
            throw ApiException.validation(field, max + "자 이하예요");
        }
        return value;
    }

    private static ApiException notFound() {
        // 다른 학원 학생·교사 id·없는 id — 존재를 드러내지 않게 404(API 명세 §0.3)
        return new ApiException(CommonErrorCode.NOT_FOUND, "학생을 찾을 수 없어요");
    }
}
