package com.jglearning.jg4seasons.wordtest;

import com.jglearning.jg4seasons.auth.api.AcademyStudents;
import com.jglearning.jg4seasons.content.api.LessonCatalog;
import com.jglearning.jg4seasons.content.api.WordTestItem;
import com.jglearning.jg4seasons.content.api.WordTestItems;
import com.jglearning.jg4seasons.infra.storage.PhotoStorage;
import com.jglearning.jg4seasons.wordtest.api.WordTestResults;
import com.jglearning.jg4seasons.wordtest.grading.Section;
import com.jglearning.jg4seasons.wordtest.grading.SubmissionStatus;
import com.jglearning.jg4seasons.wordtest.persistence.AnswerStore;
import com.jglearning.jg4seasons.wordtest.persistence.AnswerStore.AnswerRow;
import com.jglearning.jg4seasons.wordtest.persistence.SubmissionStore;
import com.jglearning.jg4seasons.wordtest.persistence.SubmissionStore.SubmissionRow;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 교사 조회용 결과(WordTestResults). 학생 조회(SubmissionQueryService)와 달리 unclear 문항에도 정답, 사진 서명 주소 포함 */
@Service
@Transactional(readOnly = true)
class WordTestResultsService implements WordTestResults {

    /** 사진 보기 주소 유효 시간(업로드 주소와 같음) */
    static final Duration IMAGE_URL_TTL = Duration.ofMinutes(5);

    private final WordTestAccess access;
    private final SubmissionStore submissions;
    private final AnswerStore answers;
    private final WordTestItems items;
    private final LessonCatalog catalog;
    private final PhotoStorage photos;

    WordTestResultsService(
            WordTestAccess access,
            SubmissionStore submissions,
            AnswerStore answers,
            WordTestItems items,
            LessonCatalog catalog,
            PhotoStorage photos) {
        this.access = access;
        this.submissions = submissions;
        this.answers = answers;
        this.items = items;
        this.catalog = catalog;
        this.photos = photos;
    }

    @Override
    public void requireEnabled(long academyId) {
        access.requireEnabled(academyId);
    }

    @Override
    public boolean hasItems(long lessonId, String section) {
        return items.count(lessonId, Section.fromDb(section).dbValue()) > 0;
    }

    @Override
    public Map<Long, Summary> latestOf(AcademyStudents students, long lessonId, String section) {
        Map<Long, Summary> latest = new LinkedHashMap<>();
        // 학생 순, 시도 번호 큰 것부터 — 학생마다 첫 행이 마지막 시도
        for (SubmissionRow row : submissions.latestAmong(students.ids(), lessonId, Section.fromDb(section))) {
            latest.putIfAbsent(row.studentId(), summary(row));
        }
        return latest;
    }

    @Override
    public Optional<Detail> detailOf(AcademyStudents students, long submissionId) {
        return submissions.findAmong(submissionId, students.ids()).map(this::detail);
    }

    private static Summary summary(SubmissionRow row) {
        boolean graded = row.status() == SubmissionStatus.GRADED;
        return new Summary(
                row.id(),
                row.status().dbValue(),
                graded ? row.score() : null,
                row.total(),
                row.attemptNo(),
                row.submittedAt());
    }

    private Detail detail(SubmissionRow row) {
        boolean graded = row.status() == SubmissionStatus.GRADED;
        String code = catalog.codeOf(row.lessonId())
                .orElseThrow(() -> new IllegalStateException("제출의 회차를 찾을 수 없다: " + row.lessonId()));
        String imageUrl = submissions
                .imageKeyOf(row.id())
                .flatMap(key -> photos.presignGet(key, IMAGE_URL_TTL))
                .map(URI::toString)
                .orElse(null);
        return new Detail(
                row.id(),
                row.studentId(),
                code,
                row.section().dbValue(),
                row.attemptNo(),
                row.status().dbValue(),
                graded ? row.score() : null,
                row.total(),
                row.submittedAt(),
                graded ? row.gradedAt() : null,
                graded ? itemsOf(row) : null,
                imageUrl);
    }

    /** 번호 순. 답안이 없는 문항은 뺀다(채점이 모든 문항에 답안을 만든다) */
    private List<Item> itemsOf(SubmissionRow row) {
        Map<Long, AnswerRow> byItem =
                answers.answers(row.id()).stream().collect(Collectors.toMap(AnswerRow::itemId, Function.identity()));
        List<Item> result = new ArrayList<>();
        for (WordTestItem item : items.of(row.lessonId(), row.section().dbValue())) {
            AnswerRow answer = byItem.get(item.id());
            if (answer == null) {
                continue;
            }
            result.add(new Item(
                    item.no(),
                    item.prompt(),
                    ResultView.written(answer.recognizedText()),
                    answer.result().dbValue(),
                    item.answer()));
        }
        return result;
    }
}
