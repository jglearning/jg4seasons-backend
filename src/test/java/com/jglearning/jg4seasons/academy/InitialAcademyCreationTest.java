package com.jglearning.jg4seasons.academy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.jglearning.jg4seasons.auth.api.AccountStatus;
import com.jglearning.jg4seasons.auth.api.NewStaff;
import com.jglearning.jg4seasons.auth.api.StaffAccount;
import com.jglearning.jg4seasons.auth.api.StaffAccounts;
import com.jglearning.jg4seasons.auth.api.StaffChange;
import com.jglearning.jg4seasons.auth.api.StaffRole;
import com.jglearning.jg4seasons.infra.persistence.devseed.DevSeedRunner;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.Order;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 학원이 하나도 없을 때(운영 첫 기동) 첫 학원·관리자를 만드는 경로. 통합 테스트 DB에는 다른 테스트의 학원이 있어 "학원 0개"를 만들 수 없으므로 가짜 저장소로
 * 본다(InitialAcademyInitializerTest는 건너뛰는 경로와 만든 계정의 로그인을 본다).
 */
class InitialAcademyCreationTest {

    static final String PASSWORD = "first-admin-password";

    private final FakeAcademies academies = new FakeAcademies();
    private final FakeStaffAccounts staff = new FakeStaffAccounts();

    private InitialAcademyInitializer initializer(InitialAcademyProperties properties) {
        return new InitialAcademyInitializer(academies, staff, properties);
    }

    @Test
    void 학원이_없고_설정이_있으면_학원과_관리자를_한_번_만든다() {
        initializer(new InitialAcademyProperties("첫 학원", "main", "owner", PASSWORD, null, true))
                .createIfNoAcademy();

        assertThat(academies.saved)
                .singleElement()
                .satisfies(academy -> assertThat(academy.getSlug()).isEqualTo("main"));
        assertThat(staff.created).singleElement().satisfies(call -> {
            assertThat(call.academyId()).isEqualTo(FakeAcademies.ID);
            assertThat(call.staff().role()).isEqualTo(StaffRole.ADMIN);
            assertThat(call.staff().loginId()).isEqualTo("owner");
        });
    }

    // 운영(required=true)에서 설정이 빠지면 아무도 로그인할 수 없는 서버가 정상처럼 뜨지 않게 기동을 멈춘다
    @Test
    void 학원이_없는데_운영_설정이_비었으면_서버_시작을_멈춘다() {
        assertThatIllegalStateException()
                .isThrownBy(() -> initializer(new InitialAcademyProperties(null, null, null, null, null, true))
                        .createIfNoAcademy());
        assertThat(academies.saved).isEmpty();
        assertThat(staff.created).isEmpty();
    }

    @Test
    void 학원이_있으면_만들지_않는다() {
        academies.count = 1;

        initializer(new InitialAcademyProperties("첫 학원", "main", "owner", PASSWORD, null, true))
                .createIfNoAcademy();

        assertThat(academies.saved).isEmpty();
        assertThat(staff.created).isEmpty();
    }

    // 시험 시드가 먼저 돌면 데모 학원 때문에 "학원 있음"이 되어 운영 관리자가 생기지 않는다
    @Test
    void 첫_학원은_시험_시드보다_먼저_만든다() {
        int initializer =
                InitialAcademyInitializer.class.getAnnotation(Order.class).value();
        int trialSeed = DevSeedRunner.class.getAnnotation(Order.class).value();

        assertThat(initializer).isLessThan(trialSeed);
    }

    static final class FakeAcademies implements AcademyRepository {

        static final long ID = 7L;

        long count;
        final List<Academy> saved = new ArrayList<>();

        @Override
        public long count() {
            return count;
        }

        @Override
        public Academy save(Academy academy) {
            ReflectionTestUtils.setField(academy, "id", ID);
            saved.add(academy);
            return academy;
        }

        @Override
        public Optional<Academy> findById(Long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Academy> findBySlug(String slug) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Long> lockById(Long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Academy saveAndFlush(Academy academy) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Academy> findAllByOrderByIdAsc() {
            throw new UnsupportedOperationException();
        }
    }

    record Created(long academyId, NewStaff staff) {}

    static final class FakeStaffAccounts implements StaffAccounts {

        final List<Created> created = new ArrayList<>();

        @Override
        public StaffAccount create(long academyId, NewStaff staff) {
            created.add(new Created(academyId, staff));
            return new StaffAccount(11L, staff.name(), staff.loginId(), staff.role(), AccountStatus.ACTIVE, null);
        }

        @Override
        public void requireActiveAdmin(long academyId, long actorId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StaffRole requireActiveStaff(long academyId, long actorId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<StaffAccount> list(long academyId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StaffAccount find(long academyId, long staffId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StaffAccount change(long academyId, long actorId, long staffId, StaffChange change) {
            throw new UnsupportedOperationException();
        }
    }
}
