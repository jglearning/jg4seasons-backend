package com.jglearning.jg4seasons.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.api.AccountStatus;
import com.jglearning.jg4seasons.auth.api.IssuedCode;
import com.jglearning.jg4seasons.auth.api.NewStudent;
import com.jglearning.jg4seasons.auth.api.StudentAccount;
import com.jglearning.jg4seasons.auth.api.StudentAccounts;
import com.jglearning.jg4seasons.auth.api.StudentChange;
import com.jglearning.jg4seasons.auth.api.StudentChangeResult;
import com.jglearning.jg4seasons.auth.token.RefreshTokenService;
import com.jglearning.jg4seasons.auth.token.SessionTtl;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 학생 계정 공개 api: 학원 안에서 학생을 만들고 개인 코드를 발급·재발급하고, 변경·퇴원·검색한다(ADR-22, 이슈 #29). */
@IntegrationTest
@Transactional
class StudentAccountsTest {

    @Autowired
    StudentAccounts accounts;

    @Autowired
    UserRepository users;

    @Autowired
    AuthIdentityRepository identities;

    @Autowired
    PersonalCodeHasher hasher;

    @Autowired
    RefreshTokenService refreshTokens;

    @Autowired
    MutableClock clock;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager em;

    TestData data;
    long academyId;

    @BeforeEach
    void setUp() {
        data = new TestData(jdbc);
        academyId = data.academy("학생 계정 학원");
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private IssuedCode createOne(String name) {
        return accounts.create(academyId, List.of(new NewStudent(name, null, null)))
                .get(0);
    }

    private String identifierOf(long studentId) {
        return jdbc.queryForObject(
                "select identifier from auth_identity where user_id = ? and provider = 'personal_code'",
                String.class,
                studentId);
    }

    /** DATETIME은 UTC로 저장된다 — JDBC Timestamp는 JVM 시간대로 읽혀 LocalDateTime으로 읽는다 */
    private Instant issuedAtInDb(long studentId) {
        return jdbc.queryForObject(
                        "select issued_at from auth_identity where user_id = ?", LocalDateTime.class, studentId)
                .toInstant(ZoneOffset.UTC);
    }

    private boolean revoked(long userId) {
        Integer live = jdbc.queryForObject(
                "select count(*) from refresh_token where user_id = ? and revoked_at is null", Integer.class, userId);
        return live == 0;
    }

    static void assertCode(Runnable call, Object code) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(
                        ApiException.class, e -> assertThat(e.errorCode()).isEqualTo(code));
    }

    @Test
    void 등록하면_요청_순서대로_사용자와_개인_코드가_생긴다() {
        List<IssuedCode> issued = accounts.create(
                academyId, List.of(new NewStudent(" 김민준 ", " A중 ", "중1"), new NewStudent("이서연", null, null)));

        assertThat(issued).extracting(IssuedCode::name).containsExactly("김민준", "이서연");
        IssuedCode first = issued.get(0);
        assertThat(first.personalCode()).matches("^\\d{4}$");
        var row = jdbc.queryForMap(
                "select academy_id, role, name, school, grade, status from `user` where id = ?", first.studentId());
        assertThat(row)
                .containsEntry("academy_id", academyId)
                .containsEntry("role", "student")
                .containsEntry("name", "김민준")
                .containsEntry("school", "A중")
                .containsEntry("grade", "중1")
                .containsEntry("status", "active");
        assertThat(identifierOf(first.studentId())).isEqualTo(hasher.hash(academyId, first.personalCode()));
        assertThat(issuedAtInDb(first.studentId())).isEqualTo(clock.instant().truncatedTo(ChronoUnit.SECONDS));
        // 소속(enrollment)은 clazz 모듈 몫 — 여기서는 만들지 않는다
        assertThat(jdbc.queryForObject(
                        "select count(*) from enrollment where student_id = ?", Integer.class, first.studentId()))
                .isZero();
    }

    @Test
    void DB에는_평문_코드가_없다() {
        List<IssuedCode> issued = accounts.create(
                academyId,
                IntStream.range(0, 10)
                        .mapToObj(i -> new NewStudent("학생" + i, null, null))
                        .toList());

        for (IssuedCode code : issued) {
            var row = jdbc.queryForMap(
                    "select identifier, secret_hash from auth_identity where user_id = ?", code.studentId());
            assertThat((String) row.get("identifier")).hasSize(64).doesNotContain(code.personalCode());
            assertThat(row.get("secret_hash")).isNull();
        }
    }

    @Test
    void 발급_결과의_toString에_코드가_없다() {
        IssuedCode issued = createOne("김민준");

        assertThat(issued.toString())
                .doesNotContain(issued.personalCode())
                .contains(String.valueOf(issued.studentId()));
    }

    @Test
    void 한_묶음_안에서도_코드가_겹치지_않는다() {
        // 난수가 같은 값을 두 번 내도 아직 flush 전인 앞 학생의 코드와 겹치지 않아야 한다
        StudentAccountService service = new StudentAccountService(
                users,
                identities,
                hasher,
                refreshTokens,
                new PersonalCodeGenerator(PersonalCodeGeneratorTest.fixed(1111, 1111, 2222)),
                clock);

        List<IssuedCode> issued =
                service.create(academyId, List.of(new NewStudent("가", null, null), new NewStudent("나", null, null)));

        assertThat(issued).extracting(IssuedCode::personalCode).containsExactly("1111", "2222");
    }

    @Test
    void 같은_학원에_있는_코드는_다시_뽑는다() {
        StudentAccountService service = new StudentAccountService(
                users,
                identities,
                hasher,
                refreshTokens,
                new PersonalCodeGenerator(PersonalCodeGeneratorTest.fixed(3333, 3333, 4444)),
                clock);
        service.create(academyId, List.of(new NewStudent("가", null, null)));

        IssuedCode second = service.create(academyId, List.of(new NewStudent("나", null, null)))
                .get(0);

        assertThat(second.personalCode()).isEqualTo("4444");
    }

    @Test
    void 다른_학원에_같은_코드가_있어도_발급된다() {
        long other = data.academy("다른 학원");
        StudentAccountService service = new StudentAccountService(
                users,
                identities,
                hasher,
                refreshTokens,
                new PersonalCodeGenerator(PersonalCodeGeneratorTest.fixed(5555)),
                clock);
        service.create(other, List.of(new NewStudent("가", null, null)));

        IssuedCode issued = service.create(academyId, List.of(new NewStudent("나", null, null)))
                .get(0);

        assertThat(issued.personalCode()).isEqualTo("5555");
    }

    @Test
    void 이름이_공백만이거나_30자를_넘으면_400이고_아무도_만들지_않는다() {
        Integer before = jdbc.queryForObject("select count(*) from `user`", Integer.class);

        assertCode(
                () -> accounts.create(
                        academyId, List.of(new NewStudent("김민준", null, null), new NewStudent("   ", null, null))),
                CommonErrorCode.VALIDATION);
        assertCode(
                () -> accounts.create(academyId, List.of(new NewStudent("가".repeat(31), null, null))),
                CommonErrorCode.VALIDATION);
        assertThat(jdbc.queryForObject("select count(*) from `user`", Integer.class))
                .isEqualTo(before);
    }

    @Test
    void 재발급_뒤_새_코드와_발급_시각이_DB에_남는다() {
        IssuedCode before = createOne("김민준");
        String oldIdentifier = identifierOf(before.studentId());
        clock.advance(Duration.ofMinutes(5));

        IssuedCode after =
                accounts.regenerate(academyId, List.of(before.studentId())).get(0);

        assertThat(after.studentId()).isEqualTo(before.studentId());
        assertThat(after.name()).isEqualTo("김민준");
        assertThat(after.personalCode()).matches("^\\d{4}$").isNotEqualTo(before.personalCode());
        assertThat(identifierOf(before.studentId()))
                .isEqualTo(hasher.hash(academyId, after.personalCode()))
                .isNotEqualTo(oldIdentifier);
        assertThat(identities.findByProviderAndIdentifier(AuthProvider.PERSONAL_CODE, oldIdentifier))
                .isEmpty();
        em.flush();
        assertThat(issuedAtInDb(before.studentId())).isEqualTo(clock.instant().truncatedTo(ChronoUnit.SECONDS));
    }

    @Test
    void 재발급하면_그_학생의_refresh가_모두_끊기고_다른_학생은_그대로() {
        IssuedCode target = createOne("김민준");
        IssuedCode other = createOne("이서연");
        refreshTokens.create(target.studentId(), "phone", SessionTtl.STUDENT_REFRESH);
        refreshTokens.create(target.studentId(), "tablet", SessionTtl.STUDENT_REFRESH);
        refreshTokens.create(other.studentId(), "phone", SessionTtl.STUDENT_REFRESH);

        accounts.regenerate(academyId, List.of(target.studentId()));

        assertThat(revoked(target.studentId())).isTrue();
        assertThat(revoked(other.studentId())).isFalse();
    }

    @Test
    void 코드가_없던_학생도_재발급으로_코드가_생긴다() {
        long seeded = data.student(academyId, "코드 없는 학생");

        IssuedCode issued = accounts.regenerate(academyId, List.of(seeded)).get(0);

        assertThat(identifierOf(seeded)).isEqualTo(hasher.hash(academyId, issued.personalCode()));
    }

    @Test
    void 같은_id를_두_번_넘겨도_한_번만_재발급한다() {
        // 두 번 발급하면 첫 평문은 이미 무효인데 응답에 실리고, 코드가 없던 학생은 수단이 두 번 저장된다
        long seeded = data.student(academyId, "코드 없는 학생");
        IssuedCode other = createOne("이서연");

        List<IssuedCode> issued = accounts.regenerate(academyId, List.of(seeded, other.studentId(), seeded));

        assertThat(issued).extracting(IssuedCode::studentId).containsExactly(seeded, other.studentId());
        em.flush();
        assertThat(identifierOf(seeded))
                .isEqualTo(hasher.hash(academyId, issued.get(0).personalCode()));
    }

    @Test
    void 다른_학원_학생이나_교사가_섞이면_404이고_아무것도_바뀌지_않는다() {
        IssuedCode mine = createOne("김민준");
        String before = identifierOf(mine.studentId());
        long otherAcademy = data.academy("다른 학원");
        long otherStudent = data.student(otherAcademy, "남의 학생");
        long teacher = data.staff(academyId, "김교사", "teacher");

        assertCode(
                () -> accounts.regenerate(academyId, List.of(mine.studentId(), otherStudent)),
                CommonErrorCode.NOT_FOUND);
        assertCode(() -> accounts.regenerate(academyId, List.of(mine.studentId(), teacher)), CommonErrorCode.NOT_FOUND);
        assertThat(identifierOf(mine.studentId())).isEqualTo(before);
    }

    @Test
    void 찾기는_학원_학생만() {
        IssuedCode mine = createOne("김민준");
        long otherStudent = data.student(data.academy("다른 학원"), "남의 학생");

        StudentAccount found = accounts.find(academyId, mine.studentId());

        assertThat(found.name()).isEqualTo("김민준");
        assertThat(found.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(found.codeIssuedAt()).isEqualTo(clock.instant().truncatedTo(ChronoUnit.SECONDS));
        assertThat(found.lastLoginAt()).isNull();
        assertCode(() -> accounts.find(academyId, otherStudent), CommonErrorCode.NOT_FOUND);
        assertCode(() -> accounts.find(academyId, data.staff(academyId, "교사", "teacher")), CommonErrorCode.NOT_FOUND);
    }

    @Test
    void 여러_명_찾기는_이름_순이고_학원_밖_id는_빠진다() {
        long c = createOne("최").studentId();
        long a = createOne("가").studentId();
        long a2 = createOne("가").studentId();
        long other = data.student(data.academy("다른 학원"), "나");

        List<StudentAccount> found = accounts.findAll(academyId, List.of(c, a2, a, other));

        assertThat(found).extracting(StudentAccount::id).containsExactly(a, a2, c);
        assertThat(accounts.findAll(academyId, List.of())).isEmpty();
    }

    @Test
    void 변경은_빠진_칸을_그대로_두고_빈_문자열은_지운다() {
        long id = accounts.create(academyId, List.of(new NewStudent("김민준", "A중", "중1")))
                .get(0)
                .studentId();

        StudentChangeResult result = accounts.change(
                academyId,
                id,
                new StudentChange(Optional.of(" 김민수 "), Optional.of(""), Optional.empty(), Optional.empty()));

        assertThat(result.deactivated()).isFalse();
        assertThat(result.account().name()).isEqualTo("김민수");
        assertThat(result.account().school()).isNull();
        assertThat(result.account().grade()).isEqualTo("중1");
        em.flush();
        assertThat(jdbc.queryForMap("select name, school, grade from `user` where id = ?", id))
                .containsEntry("name", "김민수")
                .containsEntry("school", null)
                .containsEntry("grade", "중1");
    }

    @Test
    void 퇴원하면_refresh가_끊기고_deactivated가_참_다시_보내면_거짓() {
        long id = createOne("김민준").studentId();
        refreshTokens.create(id, "phone", SessionTtl.STUDENT_REFRESH);
        StudentChange inactive = new StudentChange(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(AccountStatus.INACTIVE));

        StudentChangeResult first = accounts.change(academyId, id, inactive);
        StudentChangeResult again = accounts.change(academyId, id, inactive);

        assertThat(first.deactivated()).isTrue();
        assertThat(first.account().status()).isEqualTo(AccountStatus.INACTIVE);
        assertThat(again.deactivated()).isFalse();
        assertThat(revoked(id)).isTrue();
        assertThat(jdbc.queryForObject("select status from `user` where id = ?", String.class, id))
                .isEqualTo("inactive");
        // 결정 B: 퇴원해도 코드는 그대로
        assertThat(jdbc.queryForObject("select count(*) from auth_identity where user_id = ?", Integer.class, id))
                .isEqualTo(1);
    }

    @Test
    void 퇴원생을_되살리면_active() {
        long id = createOne("김민준").studentId();
        accounts.change(
                academyId,
                id,
                new StudentChange(
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(AccountStatus.INACTIVE)));

        StudentChangeResult result = accounts.change(
                academyId,
                id,
                new StudentChange(
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(AccountStatus.ACTIVE)));

        assertThat(result.deactivated()).isFalse();
        assertThat(result.account().status()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void 다른_학원_학생_변경은_404() {
        long other = data.student(data.academy("다른 학원"), "남의 학생");

        assertCode(
                () -> accounts.change(
                        academyId,
                        other,
                        new StudentChange(Optional.of("x"), Optional.empty(), Optional.empty(), Optional.empty())),
                CommonErrorCode.NOT_FOUND);
    }

    @Test
    void 이름_검색은_학원_안_퇴원_포함_이름_순() {
        long b = createOne("김민준").studentId();
        long a = createOne("김가람").studentId();
        createOne("이서연");
        accounts.change(
                academyId,
                b,
                new StudentChange(
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(AccountStatus.INACTIVE)));
        data.student(data.academy("다른 학원"), "김다른");

        List<StudentAccount> found = accounts.searchByName(academyId, " 김 ");

        assertThat(found).extracting(StudentAccount::id).containsExactly(a, b);
    }

    @Test
    void 검색어의_퍼센트와_밑줄은_글자_그대로() {
        long percent = createOne("김%").studentId();
        createOne("김민");

        assertThat(accounts.searchByName(academyId, "%"))
                .extracting(StudentAccount::id)
                .containsExactly(percent);
        assertThat(accounts.searchByName(academyId, "김_")).isEmpty();
    }

    @Test
    void 검색은_최대_50명() {
        accounts.create(
                academyId,
                IntStream.range(0, 51)
                        .mapToObj(i -> new NewStudent("박" + i, null, null))
                        .toList());

        assertThat(accounts.searchByName(academyId, "박")).hasSize(StudentAccounts.SEARCH_LIMIT);
        assertThat(StudentAccounts.SEARCH_LIMIT).isEqualTo(50);
    }

    @Test
    void 학원_학생_id는_퇴원_포함_학생만() {
        long a = createOne("가").studentId();
        long b = data.student(academyId, "나");
        data.staff(academyId, "교사", "teacher");
        data.student(data.academy("다른 학원"), "다");

        assertThat(accounts.studentIds(academyId)).containsExactlyInAnyOrder(a, b);
    }
}
