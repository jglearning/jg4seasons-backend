package com.jglearning.jg4seasons.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.api.AccountStatus;
import com.jglearning.jg4seasons.auth.api.NewStaff;
import com.jglearning.jg4seasons.auth.api.StaffAccount;
import com.jglearning.jg4seasons.auth.api.StaffAccounts;
import com.jglearning.jg4seasons.auth.api.StaffChange;
import com.jglearning.jg4seasons.auth.api.StaffRole;
import com.jglearning.jg4seasons.auth.token.RefreshOutcome;
import com.jglearning.jg4seasons.auth.token.RefreshTokenService;
import com.jglearning.jg4seasons.auth.token.SessionTtl;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/** 교사·관리자 계정 공개 api: 학원 안에서 만들고·바꾸고·비활성화하고·비밀번호를 재설정한다. */
@IntegrationTest
@Transactional
class StaffAccountsTest {

    static final String PASSWORD = "teacher-pass-1";

    @Autowired
    StaffAccounts accounts;

    @Autowired
    RefreshTokenService refreshTokens;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    JdbcTemplate jdbc;

    TestData data;
    long academyId;
    long adminId;

    @BeforeEach
    void setUp() {
        data = new TestData(jdbc);
        academyId = data.academy("테스트 학원");
        adminId = data.staff(academyId, "관리자", "admin");
    }

    private StaffAccount create(String loginId) {
        return accounts.create(academyId, new NewStaff("김교사", loginId, PASSWORD, StaffRole.TEACHER));
    }

    static void assertCode(Runnable call, Object code) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(
                        ApiException.class, e -> assertThat(e.errorCode()).isEqualTo(code));
    }

    @Test
    void 교사를_만들면_해시만_저장하고_아이디를_정리한다() {
        StaffAccount created = create(" Kim.Teacher_01 ");

        assertThat(created.loginId()).isEqualTo("kim.teacher_01");
        assertThat(created.role()).isEqualTo(StaffRole.TEACHER);
        assertThat(created.status()).isEqualTo(AccountStatus.ACTIVE);
        String hash = jdbc.queryForObject(
                "select secret_hash from auth_identity where user_id = ?", String.class, created.id());
        assertThat(hash).doesNotContain(PASSWORD);
        assertThat(encoder.matches(PASSWORD, hash)).isTrue();
    }

    @Test
    void 이미_있는_아이디는_대소문자가_달라도_409_LOGIN_ID_TAKEN() {
        create("dup-teacher");

        assertCode(() -> create("DUP-teacher"), AuthErrorCode.LOGIN_ID_TAKEN);
    }

    @Test
    void 다른_학원_아이디와도_겹치면_409() {
        long other = data.academy("다른 학원");
        accounts.create(other, new NewStaff("다른 교사", "shared-id", PASSWORD, StaffRole.TEACHER));

        assertCode(() -> create("shared-id"), AuthErrorCode.LOGIN_ID_TAKEN);
    }

    @Test
    void 형식이_틀린_아이디와_짧은_비밀번호는_400() {
        assertCode(() -> create("ab"), CommonErrorCode.VALIDATION);
        assertCode(
                () -> accounts.create(academyId, new NewStaff("김교사", "teacher-x", "1234567", StaffRole.TEACHER)),
                CommonErrorCode.VALIDATION);
        assertCode(
                () -> accounts.create(academyId, new NewStaff("김교사", "teacher-y", "가".repeat(25), StaffRole.TEACHER)),
                CommonErrorCode.VALIDATION); // 75바이트 — BCrypt는 72바이트 뒤를 버린다
    }

    @Test
    void 목록에는_그_학원_교사와_관리자만() {
        StaffAccount teacher = create("list-teacher");
        data.student(academyId, "학생");
        long other = data.academy("다른 학원");
        accounts.create(other, new NewStaff("다른 교사", "other-teacher", PASSWORD, StaffRole.TEACHER));

        assertThat(accounts.list(academyId)).extracting(StaffAccount::id).containsExactly(adminId, teacher.id());
    }

    @Test
    void 다른_학원_교사와_학생_id는_404() {
        long other = data.academy("다른 학원");
        StaffAccount othersTeacher =
                accounts.create(other, new NewStaff("다른 교사", "other-t2", PASSWORD, StaffRole.TEACHER));
        long studentId = data.student(academyId, "학생");

        assertCode(() -> accounts.find(academyId, othersTeacher.id()), CommonErrorCode.NOT_FOUND);
        assertCode(() -> accounts.find(academyId, studentId), CommonErrorCode.NOT_FOUND);
        assertCode(
                () -> accounts.change(academyId, adminId, othersTeacher.id(), StaffChange.rename("x")),
                CommonErrorCode.NOT_FOUND);
    }

    @Test
    void 이름을_바꾼다() {
        StaffAccount teacher = create("rename-me");

        assertThat(accounts.change(academyId, adminId, teacher.id(), StaffChange.rename("박교사"))
                        .name())
                .isEqualTo("박교사");
    }

    @Test
    void 자기_자신은_비활성화할_수_없다() {
        assertCode(
                () -> accounts.change(academyId, adminId, adminId, StaffChange.status(AccountStatus.INACTIVE)),
                AuthErrorCode.SELF_CHANGE_NOT_ALLOWED);
    }

    @Test
    void 비활성화하면_그_교사의_refresh가_끊긴다() {
        StaffAccount teacher = create("deactivate-me");
        String refresh = refreshTokens.create(teacher.id(), null, SessionTtl.STAFF_REFRESH);

        StaffAccount changed =
                accounts.change(academyId, adminId, teacher.id(), StaffChange.status(AccountStatus.INACTIVE));

        assertThat(changed.status()).isEqualTo(AccountStatus.INACTIVE);
        assertThat(refreshTokens.rotate(refresh)).isInstanceOf(RefreshOutcome.Rejected.class);
    }

    @Test
    void 비밀번호를_재설정하면_새_비밀번호로_바뀌고_잠금이_풀리고_refresh가_끊긴다() {
        StaffAccount teacher = create("reset-me");
        String refresh = refreshTokens.create(teacher.id(), null, SessionTtl.STAFF_REFRESH);
        jdbc.update(
                "update auth_identity set failed_attempts = 5, locked_until = '2099-01-01' where user_id = ?",
                teacher.id());

        accounts.change(academyId, adminId, teacher.id(), StaffChange.password("new-password-2"));

        String hash = jdbc.queryForObject(
                "select secret_hash from auth_identity where user_id = ?", String.class, teacher.id());
        assertThat(encoder.matches("new-password-2", hash)).isTrue();
        assertThat(jdbc.queryForObject(
                        "select locked_until is null from auth_identity where user_id = ?",
                        Boolean.class,
                        teacher.id()))
                .isTrue();
        assertThat(refreshTokens.rotate(refresh)).isInstanceOf(RefreshOutcome.Rejected.class);
    }

    @Test
    void 비밀번호_수단이_없는_계정의_재설정은_404() {
        assertCode(
                () -> accounts.change(academyId, adminId, adminId, StaffChange.password("new-password-1")),
                CommonErrorCode.NOT_FOUND);
    }

    @Test
    void 바꿀_것이_없으면_그대로_돌려준다() {
        StaffAccount teacher = create("no-change");

        assertThat(accounts.change(
                                academyId,
                                adminId,
                                teacher.id(),
                                new StaffChange(Optional.empty(), Optional.empty(), Optional.empty()))
                        .name())
                .isEqualTo("김교사");
    }

    @Test
    void 활성_교사와_관리자는_역할을_돌려준다() {
        long teacherId = data.staff(academyId, "교사", "teacher");

        assertThat(accounts.requireActiveStaff(academyId, teacherId)).isEqualTo(StaffRole.TEACHER);
        assertThat(accounts.requireActiveStaff(academyId, adminId)).isEqualTo(StaffRole.ADMIN);
    }

    @Test
    void 비활성_교사_학생_다른_학원_교사_정지된_학원은_401() {
        long inactive = data.staff(academyId, "비활성 교사", "teacher");
        jdbc.update("update `user` set status = 'inactive' where id = ?", inactive);
        long student = data.student(academyId, "학생");
        long otherAcademy = data.academy("다른 학원");
        long otherTeacher = data.staff(otherAcademy, "남의 교사", "teacher");
        long suspendedAcademy = data.academy("정지 학원");
        long suspendedTeacher = data.staff(suspendedAcademy, "정지 학원 교사", "teacher");
        jdbc.update("update academy set status = 'suspended' where id = ?", suspendedAcademy);

        assertCode(() -> accounts.requireActiveStaff(academyId, inactive), CommonErrorCode.UNAUTHORIZED);
        assertCode(() -> accounts.requireActiveStaff(academyId, student), CommonErrorCode.UNAUTHORIZED);
        assertCode(() -> accounts.requireActiveStaff(academyId, otherTeacher), CommonErrorCode.UNAUTHORIZED);
        assertCode(() -> accounts.requireActiveStaff(suspendedAcademy, suspendedTeacher), CommonErrorCode.UNAUTHORIZED);
    }
}
