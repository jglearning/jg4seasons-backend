package com.jglearning.jg4seasons.words;

import com.jglearning.jg4seasons.content.api.LessonContent;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.words.persistence.WordStudyStore;
import com.jglearning.jg4seasons.words.persistence.WordStudyStore.WordStudyRow;
import com.jglearning.jg4seasons.words.web.dto.WordStudyRequest;
import com.jglearning.jg4seasons.words.web.dto.WordStudyResponse;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2.5 카드·학습 결과 저장. 열린 회차인지 확인한 뒤(OpenLessons) 값이 회차 단어 수 안인지 보고 단조 증가로 저장한다. 다 봤는지(allViewed)는 서버가 판정(ADR 0029).
 * 멱등 키 없음 — 같은 요청을 다시 보내도 GREATEST라 결과가 같다(자연 멱등).
 */
@Service
public class WordStudyService {

    private final OpenLessons lessons;
    private final WordStudyStore store;
    private final Clock clock;

    WordStudyService(OpenLessons lessons, WordStudyStore store, Clock clock) {
        this.lessons = lessons;
        this.store = store;
        this.clock = clock;
    }

    @Transactional
    public WordStudyResponse save(long academyId, long studentId, String lessonCode, WordStudyRequest request) {
        LessonContent lesson = lessons.require(academyId, studentId, lessonCode);
        int total = lesson.wordTotal();
        int cardsViewed = within("cardsViewed", request.cardsViewed(), total);
        int memKnown = within("memKnown", request.memKnown(), total);
        int recallCorrect = within("recallCorrect", request.recallCorrect(), total);
        int spellCorrect = within("spellCorrect", request.spellCorrect(), total);
        WordStudyRow row = store.save(
                studentId,
                lesson.lessonId(),
                cardsViewed,
                memKnown,
                recallCorrect,
                spellCorrect,
                total,
                clock.instant());
        return new WordStudyResponse(
                row.cardsViewed(), row.allViewed(), row.memKnown(), row.recallBest(), row.spellBest());
    }

    /** 안 보낸 칸은 0(저장값을 바꾸지 않음). 회차 단어 수보다 크면 400 — 프론트 버그를 드러낸다 */
    private static int within(String field, Integer value, int total) {
        if (value == null) {
            return 0;
        }
        if (value > total) {
            throw ApiException.validation(field, "회차 단어 수(" + total + "개)보다 클 수 없어요");
        }
        return value;
    }
}
