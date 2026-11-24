package com.jglearning.jg4seasons.wordtest;

import com.jglearning.jg4seasons.content.api.LessonCatalog;
import com.jglearning.jg4seasons.content.api.WordTestItem;
import com.jglearning.jg4seasons.content.api.WordTestItems;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import com.jglearning.jg4seasons.wordtest.grading.Section;
import com.jglearning.jg4seasons.wordtest.grading.SubmissionStatus;
import com.jglearning.jg4seasons.wordtest.persistence.AnswerStore;
import com.jglearning.jg4seasons.wordtest.persistence.AnswerStore.AnswerRow;
import com.jglearning.jg4seasons.wordtest.persistence.SubmissionStore;
import com.jglearning.jg4seasons.wordtest.persistence.SubmissionStore.SubmissionRow;
import com.jglearning.jg4seasons.wordtest.web.dto.SubmissionResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * D-3 제출 조회·D-4 회차 최근 제출(학생 본인 것만). 다른 학생·다른 학원의 제출은 없는 것과 같게 404. graded가 아니면 문항(정답)을 넣지 않는다
 */
@Service
@Transactional(readOnly = true)
public class SubmissionQueryService {

    private final WordTestAccess access;
    private final SubmissionStore submissions;
    private final AnswerStore answers;
    private final WordTestItems items;
    private final LessonCatalog catalog;

    SubmissionQueryService(
            WordTestAccess access,
            SubmissionStore submissions,
            AnswerStore answers,
            WordTestItems items,
            LessonCatalog catalog) {
        this.access = access;
        this.submissions = submissions;
        this.answers = answers;
        this.items = items;
        this.catalog = catalog;
    }

    public SubmissionResponse get(long academyId, long studentId, long submissionId) {
        access.requireEnabled(academyId);
        SubmissionRow row = submissions
                .findOwned(submissionId, studentId)
                .orElseThrow(() -> new ApiException(CommonErrorCode.NOT_FOUND, "제출을 찾을 수 없어요"));
        String code =
                catalog.codeOf(row.lessonId()).orElseThrow(() -> new IllegalStateException("제출의 회차가 없다: " + row.id()));
        return view(row, code);
    }

    /** 없으면 empty(204). 그 회차·영역의 시험지가 없으면 404 — 제출할 수 없는 곳에서 진입 버튼이 보이지 않게 */
    public Optional<SubmissionResponse> latest(long academyId, long studentId, String lessonCode, Section section) {
        WordTestAccess.LessonRef lesson = access.requireOpenLesson(academyId, studentId, lessonCode);
        if (items.count(lesson.lessonId(), section.dbValue()) == 0) {
            throw new ApiException(CommonErrorCode.NOT_FOUND, "이 회차에는 이 영역 시험지가 없어요");
        }
        return submissions.latest(studentId, lesson.lessonId(), section).map(row -> view(row, lesson.code()));
    }

    private SubmissionResponse view(SubmissionRow row, String lessonCode) {
        boolean graded = row.status() == SubmissionStatus.GRADED;
        return new SubmissionResponse(
                row.id(),
                lessonCode,
                row.section(),
                row.attemptNo(),
                row.status().dbValue(),
                graded ? row.score() : null,
                row.total(),
                row.submittedAt(),
                graded ? row.gradedAt() : null,
                graded ? itemsOf(row) : null);
    }

    /** 문항 번호 순. 답안이 없는 문항은 빼고(채점이 모든 문항에 답안을 만든다) */
    private List<SubmissionResponse.Item> itemsOf(SubmissionRow row) {
        Map<Long, AnswerRow> byItem =
                answers.answers(row.id()).stream().collect(Collectors.toMap(AnswerRow::itemId, Function.identity()));
        List<SubmissionResponse.Item> result = new ArrayList<>();
        for (WordTestItem item : items.of(row.lessonId(), row.section().dbValue())) {
            AnswerRow answer = byItem.get(item.id());
            if (answer == null) {
                continue;
            }
            result.add(new SubmissionResponse.Item(
                    item.no(),
                    item.prompt(),
                    ResultView.written(answer.recognizedText()),
                    answer.result().dbValue(),
                    item.answer()));
        }
        return result;
    }
}
