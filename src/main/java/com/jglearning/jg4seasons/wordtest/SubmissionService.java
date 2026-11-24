package com.jglearning.jg4seasons.wordtest;

import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import com.jglearning.jg4seasons.content.api.WordTestItems;
import com.jglearning.jg4seasons.infra.storage.PhotoStorage;
import com.jglearning.jg4seasons.infra.storage.StoredPhoto;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import com.jglearning.jg4seasons.wordtest.grading.Section;
import com.jglearning.jg4seasons.wordtest.grading.SubmissionStatus;
import com.jglearning.jg4seasons.wordtest.persistence.SubmissionStore;
import com.jglearning.jg4seasons.wordtest.persistence.SubmissionStore.NewSubmission;
import com.jglearning.jg4seasons.wordtest.persistence.SubmissionStore.SubmissionRow;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * D-2 제출. 검사 순서: 기능 → 같은 clientSubmissionId(있으면 기존 제출) → 회차·열림 → 문항 정의 → imageKey 경로 → 올라간 사진의 크기·형식 → 하루 3회. 제출과 채점 작업은
 * 한 트랜잭션. 같은 요청이 동시에 오면 UNIQUE가 하나만 통과시키고 다른 하나는 잠금 읽기로 그 제출을 돌려준다.
 *
 * <p>격리 수준 READ_COMMITTED: 시도 번호가 겹쳐 다시 셀 때 상대가 방금 커밋한 번호가 보여야 한다(REPEATABLE READ면 트랜잭션 첫 읽기의 스냅숏을 다시 읽어
 * 같은 번호로 또 충돌한다). 범위를 FOR UPDATE로 잠그지 않는 것은, 빈 범위의 갭 잠금끼리는 서로 막지 않아 두 INSERT가 교착되기 때문
 */
@Service
public class SubmissionService {

    static final long MAX_BYTES = 5L * 1024 * 1024;
    static final int DAILY_LIMIT = 3;

    /** 새로 만들었으면 created = true(202), 같은 요청이면 false(200) */
    public record Result(long id, SubmissionStatus status, boolean created) {}

    private final WordTestAccess access;
    private final WordTestItems items;
    private final PhotoStorage storage;
    private final SubmissionStore store;
    private final AcademyDirectory academies;
    private final Clock clock;

    SubmissionService(
            WordTestAccess access,
            WordTestItems items,
            PhotoStorage storage,
            SubmissionStore store,
            AcademyDirectory academies,
            Clock clock) {
        this.access = access;
        this.items = items;
        this.storage = storage;
        this.store = store;
        this.academies = academies;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Result submit(
            long academyId, long studentId, String clientId, String lessonCode, Section section, String imageKey) {
        access.requireEnabled(academyId);
        Optional<Result> replay = store.findByClientId(clientId).map(row -> replay(row, studentId));
        if (replay.isPresent()) {
            return replay.get();
        }
        long lessonId =
                access.requireOpenLesson(academyId, studentId, lessonCode).lessonId();
        int total = items.count(lessonId, section.dbValue());
        if (total == 0) {
            throw new ApiException(CommonErrorCode.NOT_FOUND, "이 회차에는 이 영역 시험지가 없어요");
        }
        requireOwnPhoto(academyId, studentId, imageKey);

        Instant now = clock.instant();
        if (store.countSince(studentId, lessonId, section, startOfToday(academyId, now)) >= DAILY_LIMIT) {
            throw new ApiException(WordTestErrorCode.DAILY_LIMIT, "이 영역은 하루에 " + DAILY_LIMIT + "번까지 낼 수 있어요");
        }
        return insert(clientId, studentId, lessonId, section, imageKey, total, now);
    }

    /**
     * 시도 번호는 마지막 + 1(READ_COMMITTED라 커밋된 최신 값). 다른 요청이 같은 번호를 먼저 가져갔으면 한 번 다시 센다. 같은 client id가 먼저 커밋됐으면 그 제출
     */
    private Result insert(
            String clientId, long studentId, long lessonId, Section section, String imageKey, int total, Instant now) {
        for (int round = 0; ; round++) {
            int attempt = store.maxAttempt(studentId, lessonId, section) + 1;
            try {
                long id = store.insertWithJob(
                        new NewSubmission(clientId, studentId, lessonId, section, attempt, imageKey, total), now);
                return new Result(id, SubmissionStatus.PENDING, true);
            } catch (DuplicateKeyException e) {
                if (SubmissionStore.isClientIdConflict(e)) {
                    return store.lockByClientId(clientId)
                            .map(row -> replay(row, studentId))
                            .orElseThrow(() -> e);
                }
                if (round >= 1) {
                    throw e;
                }
            }
        }
    }

    /** 같은 client id의 제출: 그 학생 것이면 그대로(200), 남의 것이면 400 */
    private static Result replay(SubmissionRow row, long studentId) {
        if (row.studentId() != studentId) {
            throw ApiException.validation("clientSubmissionId", "이미 쓰인 제출 번호예요");
        }
        return new Result(row.id(), row.status(), false);
    }

    /** 내 경로의 키이고, 올라간 사진이 5MB 이하 JPEG·PNG. 키는 학생 id를 담으므로 오류 문구에 넣지 않는다 */
    private void requireOwnPhoto(long academyId, long studentId, String imageKey) {
        if (!ImageKeys.belongsTo(imageKey, academyId, studentId)) {
            throw new ApiException(WordTestErrorCode.INVALID_IMAGE, "사진을 다시 올려 주세요");
        }
        StoredPhoto photo = storage.head(imageKey)
                .orElseThrow(() -> new ApiException(WordTestErrorCode.INVALID_IMAGE, "사진이 올라가지 않았어요. 다시 올려 주세요"));
        if (photo.contentLength() > MAX_BYTES) {
            throw new ApiException(WordTestErrorCode.INVALID_IMAGE, "5MB 이하 사진만 낼 수 있어요");
        }
        if (photo.contentType() == null || !ImageKeys.EXTENSIONS.containsKey(photo.contentType())) {
            throw new ApiException(WordTestErrorCode.INVALID_IMAGE, "JPEG·PNG 사진만 낼 수 있어요");
        }
    }

    /** 학원 타임존 오늘 0시(UTC 시점) */
    private Instant startOfToday(long academyId, Instant now) {
        ZoneId zone = academies.zoneOf(academyId);
        return LocalDate.ofInstant(now, zone).atStartOfDay(zone).toInstant();
    }
}
