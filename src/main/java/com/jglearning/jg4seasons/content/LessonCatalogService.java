package com.jglearning.jg4seasons.content;

import com.jglearning.jg4seasons.content.api.LessonCatalog;
import com.jglearning.jg4seasons.content.api.LessonContent;
import com.jglearning.jg4seasons.content.api.LessonContent.Passage;
import com.jglearning.jg4seasons.content.api.LessonContent.Sentence;
import com.jglearning.jg4seasons.content.api.LessonContent.Speed;
import com.jglearning.jg4seasons.content.api.LessonContent.TimeRange;
import com.jglearning.jg4seasons.content.persistence.LessonContentStore;
import com.jglearning.jg4seasons.content.persistence.LessonContentStore.LessonRow;
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

    /** 지문 트랙 variant: p{지문 no}:{속도} */
    private static final Pattern PASSAGE_VARIANT = Pattern.compile("^p(\\d+):(\\d\\.\\d)$");

    private final LessonContentStore store;

    LessonCatalogService(LessonContentStore store) {
        this.store = store;
    }

    @Override
    public Optional<LessonContent> findByCode(String lessonCode) {
        return store.findLesson(lessonCode).map(this::assemble);
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
            List<Speed> speeds = tracksByPassage.getOrDefault(passage.id(), List.of()).stream()
                    .map(track -> speed(
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
                    speeds));
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
    private static Speed speed(
            String lessonCode,
            int passageNo,
            TrackRow track,
            List<SentenceRow> sentences,
            Map<Long, TimingRow> timingsBySentence) {
        Matcher variant = PASSAGE_VARIANT.matcher(track.variant());
        if (!variant.matches() || Integer.parseInt(variant.group(1)) != passageNo) {
            throw new IllegalStateException(
                    "지문 트랙 variant가 p" + passageNo + ":{속도}가 아니다: " + lessonCode + " track " + track.id());
        }
        if (timingsBySentence.isEmpty()) {
            return new Speed(track.id(), variant.group(2), track.fileKey(), track.durationMs(), List.of(), false);
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
        return new Speed(track.id(), variant.group(2), track.fileKey(), track.durationMs(), timings, approximate);
    }
}
