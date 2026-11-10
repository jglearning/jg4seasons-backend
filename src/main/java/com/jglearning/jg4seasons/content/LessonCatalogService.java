package com.jglearning.jg4seasons.content;

import com.jglearning.jg4seasons.content.api.LessonCatalog;
import com.jglearning.jg4seasons.content.api.LessonContent;
import com.jglearning.jg4seasons.content.api.LessonContent.Passage;
import com.jglearning.jg4seasons.content.api.LessonContent.Sentence;
import com.jglearning.jg4seasons.content.api.LessonContent.TimeRange;
import com.jglearning.jg4seasons.content.api.LessonContent.Voice;
import com.jglearning.jg4seasons.content.api.LessonSummary;
import com.jglearning.jg4seasons.content.api.LessonWord;
import com.jglearning.jg4seasons.content.api.PassageTrack;
import com.jglearning.jg4seasons.content.persistence.LessonContentStore;
import com.jglearning.jg4seasons.content.persistence.LessonContentStore.LessonRow;
import com.jglearning.jg4seasons.content.persistence.LessonContentStore.LessonSummaryRow;
import com.jglearning.jg4seasons.content.persistence.LessonContentStore.PassageRow;
import com.jglearning.jg4seasons.content.persistence.LessonContentStore.SentenceRow;
import com.jglearning.jg4seasons.content.persistence.LessonContentStore.TimingRow;
import com.jglearning.jg4seasons.content.persistence.LessonContentStore.TrackRow;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class LessonCatalogService implements LessonCatalog {

    /** 단어 출처(ADR 0029). API 계약(allowableValues)과 같은 값만 내보낸다 */
    private static final Set<String> WORD_SOURCES = Set.of("book", "extra");

    /** 지문 트랙 variant: p{지문 no}:{목소리}(ADR-27 — us_w·us_m·uk_w·uk_m) */
    private static final Pattern PASSAGE_VARIANT = Pattern.compile("^p(\\d+):((?:us|uk)_[wm])$");

    private final LessonContentStore store;

    LessonCatalogService(LessonContentStore store) {
        this.store = store;
    }

    @Override
    public Optional<LessonContent> findByCode(String lessonCode) {
        return store.findLesson(lessonCode).map(this::assemble);
    }

    @Override
    public List<LessonWord> words(long lessonId) {
        return store.words(lessonId).stream()
                .map(row -> new LessonWord(
                        row.id(),
                        row.sortOrder(),
                        wordSource(row.source(), row.id()),
                        row.english(),
                        row.korean(),
                        row.pos(),
                        row.exampleEn(),
                        row.exampleKo(),
                        wordAudio(row.trackId(), row.trackKind(), "word", row.trackKey(), row.trackMs(), row.id()),
                        wordAudio(
                                row.exampleTrackId(),
                                row.exampleKind(),
                                "word_example",
                                row.exampleKey(),
                                row.exampleMs(),
                                row.id())))
                .toList();
    }

    /** book·extra가 아니면 데이터 오류(manifest를 거치지 않은 값이 계약 밖으로 나가지 않게) */
    private static String wordSource(String source, long wordId) {
        if (!WORD_SOURCES.contains(source)) {
            throw new IllegalStateException("단어 출처가 book·extra가 아니다: word " + wordId);
        }
        return source;
    }

    /** 단어에 연결된 트랙이 없으면 null. 종류가 다르거나 길이가 0 이하면 데이터 오류 */
    private static LessonWord.Audio wordAudio(
            Long trackId, String kind, String expectedKind, String fileKey, Integer durationMs, long wordId) {
        if (trackId == null) {
            return null;
        }
        if (!expectedKind.equals(kind) || durationMs == null || durationMs <= 0) {
            throw new IllegalStateException(
                    "단어 트랙이 " + expectedKind + " 트랙이 아니거나 길이가 0 이하다: word " + wordId + " track " + trackId);
        }
        return new LessonWord.Audio(trackId, fileKey, durationMs);
    }

    @Override
    public Optional<PassageTrack> findPassageTrack(long trackId) {
        return store.findPassageTrack(trackId).map(row -> {
            if (row.passageNo() == null || row.passageLessonId() == null || row.passageLessonId() != row.lessonId()) {
                throw new IllegalStateException("지문 트랙이 이 회차 지문을 가리키지 않는다: " + row.lessonCode() + " track " + trackId);
            }
            if (row.durationMs() <= 0) {
                throw new IllegalStateException("트랙 길이가 0 이하다: " + row.lessonCode() + " track " + trackId);
            }
            Matcher variant = PASSAGE_VARIANT.matcher(row.variant());
            if (!variant.matches() || Integer.parseInt(variant.group(1)) != row.passageNo()) {
                throw new IllegalStateException("지문 트랙 variant가 p" + row.passageNo() + ":{목소리}가 아니다: "
                        + row.lessonCode() + " track " + trackId);
            }
            return new PassageTrack(
                    trackId,
                    row.lessonId(),
                    row.lessonCode(),
                    row.bookId(),
                    row.passageNo(),
                    variant.group(2),
                    row.durationMs());
        });
    }

    @Override
    public List<LessonSummary> lessonsOfBook(long bookId) {
        return store.lessonsOfBook(bookId).stream()
                .map(LessonCatalogService::summary)
                .toList();
    }

    /**
     * 묶음은 진도표 순서로 8회차씩(회차 목록 묶음·"겨울 1" 라벨): 1~3, 블랙 28회차는 4까지(V2·V5와 같은 식). 맞지 않으면 기준 데이터 오류 — 조용히 엉뚱한
     * 묶음을 만들지 않는다
     */
    private static LessonSummary summary(LessonSummaryRow row) {
        if (row.number() < 1 || row.module() != (row.number() - 1) / 8 + 1) {
            throw new IllegalStateException(
                    "회차의 묶음·번호가 범위 밖이다: " + row.code() + " module " + row.module() + " number " + row.number());
        }
        return new LessonSummary(
                row.id(), row.code(), row.number(), row.module(), row.title(), row.type(), row.passageCount());
    }

    private LessonContent assemble(LessonRow lesson) {
        if (lesson.coverageThreshold() == null || lesson.requiredTracksJson() == null) {
            throw new IllegalStateException("레벨 완료 규칙이 없다: " + lesson.code());
        }
        List<PassageRow> passageRows = store.passages(lesson.id());
        List<TrackRow> trackRows = store.passageTracks(lesson.id());
        Set<Long> passageIds = passageRows.stream().map(PassageRow::id).collect(Collectors.toSet());
        for (TrackRow track : trackRows) {
            if (track.passageId() == null || !passageIds.contains(track.passageId())) {
                throw new IllegalStateException("지문 트랙이 이 회차 지문을 가리키지 않는다: " + lesson.code() + " track " + track.id());
            }
        }
        Map<Long, List<SentenceRow>> sentencesByPassage =
                store.sentences(lesson.id()).stream().collect(Collectors.groupingBy(SentenceRow::passageId));
        Map<Long, List<TrackRow>> tracksByPassage =
                trackRows.stream().collect(Collectors.groupingBy(TrackRow::passageId));
        Map<Long, Map<Long, TimingRow>> timingsByTrack = store.timings(lesson.id()).stream()
                .collect(Collectors.groupingBy(
                        TimingRow::trackId, Collectors.toMap(TimingRow::sentenceId, timing -> timing)));

        List<Passage> passages = new ArrayList<>();
        for (PassageRow passage : passageRows) {
            List<SentenceRow> sentences = sentencesByPassage.getOrDefault(passage.id(), List.of());
            List<Voice> voices = tracksByPassage.getOrDefault(passage.id(), List.of()).stream()
                    .map(track -> voice(
                            lesson.code(),
                            passage.no(),
                            track,
                            sentences,
                            timingsByTrack.getOrDefault(track.id(), Map.of())))
                    .toList();
            passages.add(new Passage(
                    passage.no(),
                    passage.title(),
                    passage.wordCount(),
                    sentences.stream()
                            .map(s -> new Sentence(s.no(), s.text(), s.speaker()))
                            .toList(),
                    voices));
        }
        return new LessonContent(
                lesson.id(),
                lesson.bookId(),
                lesson.code(),
                lesson.number(),
                lesson.title(),
                lesson.type(),
                new LessonContent.Level(lesson.levelCode(), lesson.levelNameKo(), lesson.levelColorHex()),
                passages,
                store.wordCount(lesson.id()),
                new LessonContent.Rule(lesson.coverageThreshold(), lesson.requiredTracksJson()));
    }

    /** 시각이 없으면 빈 목록. 있으면 그 지문 문장마다 정확히 하나여야 한다(결정 H — 데이터 오류를 숨기지 않는다) */
    private static Voice voice(
            String lessonCode,
            int passageNo,
            TrackRow track,
            List<SentenceRow> sentences,
            Map<Long, TimingRow> timingsBySentence) {
        Matcher variant = PASSAGE_VARIANT.matcher(track.variant());
        if (!variant.matches() || Integer.parseInt(variant.group(1)) != passageNo) {
            throw new IllegalStateException(
                    "지문 트랙 variant가 p" + passageNo + ":{목소리}가 아니다: " + lessonCode + " track " + track.id());
        }
        if (timingsBySentence.isEmpty()) {
            return new Voice(track.id(), variant.group(2), track.fileKey(), track.durationMs(), List.of(), false);
        }
        if (timingsBySentence.size() != sentences.size()) {
            throw new IllegalStateException("문장 시각 수가 문장 수와 다르다: " + lessonCode + " track " + track.id());
        }
        List<TimeRange> timings = new ArrayList<>();
        boolean approximate = false;
        for (SentenceRow sentence : sentences) {
            TimingRow timing = timingsBySentence.get(sentence.id());
            if (timing == null) {
                throw new IllegalStateException("문장 시각이 다른 지문 문장을 가리킨다: " + lessonCode + " track " + track.id());
            }
            timings.add(new TimeRange(timing.startMs(), timing.endMs()));
            approximate |= timing.approximate();
        }
        return new Voice(track.id(), variant.group(2), track.fileKey(), track.durationMs(), timings, approximate);
    }
}
