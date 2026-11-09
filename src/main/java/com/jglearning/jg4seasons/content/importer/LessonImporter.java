package com.jglearning.jg4seasons.content.importer;

import com.jglearning.jg4seasons.content.api.LessonContent.TimeRange;
import com.jglearning.jg4seasons.content.persistence.ContentImportStore;
import com.jglearning.jg4seasons.content.persistence.ContentImportStore.LessonRow;
import com.jglearning.jg4seasons.content.persistence.ContentImportStore.PassageRow;
import com.jglearning.jg4seasons.content.persistence.ContentImportStore.SentenceRow;
import com.jglearning.jg4seasons.content.persistence.ContentImportStore.TimingRow;
import com.jglearning.jg4seasons.content.persistence.ContentImportStore.TrackRow;
import com.jglearning.jg4seasons.content.persistence.ContentImportStore.WordRow;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * manifest 하나로 회차 콘텐츠를 맞춘다(ADR 0027 결정 8). 회차 단위 트랜잭션.
 *
 * <p>자연키(지문 no, 문장 no, 트랙 kind+variant, 단어 sort_order)로 있는 행은 고치고 없는 행은 넣고 manifest에 없는 행은 지운다. 행 id는 바뀌지 않으므로 청취
 * 기록이 그대로 이어진다. 다만 청취 기록이 있는 트랙의 길이가 바뀌거나 그 트랙이 없어지면 집계(coverage)가 어긋나므로 그 회차는 아무것도 바꾸지 않고 거부한다. 검사(문장 시각,
 * 기준 데이터, 청취 기록)는 모두 쓰기 전에 한다.
 */
@Component
public class LessonImporter {

    static final String PASSAGE = "passage";
    static final String WORD = "word";
    static final String WORD_EXAMPLE = "word_example";

    private final ContentImportStore store;

    public LessonImporter(ContentImportStore store) {
        this.store = store;
    }

    @Transactional
    public ImportResult importLesson(LessonManifest manifest) {
        String code = manifest.code();

        Map<String, List<TimeRange>> timings = new HashMap<>();
        for (LessonManifest.Passage passage : manifest.passages()) {
            for (LessonManifest.Track track : passage.tracks()) {
                try {
                    timings.put(
                            passageVariant(passage.no(), track.voice()),
                            PassageTimings.of(track.sentenceMs(), track.durationMs()));
                } catch (IllegalArgumentException e) {
                    return ImportResult.rejected(
                            code, "지문 " + passage.no() + " " + track.voice() + ": " + e.getMessage());
                }
            }
        }

        Optional<LessonRow> found = store.lockLesson(code);
        if (found.isEmpty()) {
            return ImportResult.rejected(code, "기준 데이터에 없는 회차");
        }
        LessonRow lesson = found.get();

        Map<String, DesiredTrack> desiredTracks = desiredTracks(manifest);
        Map<String, TrackRow> existingTracks = new HashMap<>();
        for (TrackRow track : store.tracks(lesson.id())) {
            existingTracks.put(key(track.kind(), track.variant()), track);
        }

        String violation = listeningViolation(desiredTracks, existingTracks);
        if (violation != null) {
            return ImportResult.rejected(code, violation);
        }

        Changes changes = new Changes();
        deleteRemoved(manifest, lesson.id(), desiredTracks, existingTracks, changes);

        if (!lesson.title().equals(manifest.title())
                || lesson.passageCount() != manifest.passages().size()) {
            store.updateLesson(
                    lesson.id(), manifest.title(), manifest.passages().size());
            changes.add();
        }
        Map<Integer, Long> passageIds = upsertPassages(manifest, lesson.id(), changes);
        Map<Integer, List<Long>> sentenceIds = upsertSentences(manifest, lesson.id(), passageIds, changes);
        Map<String, Long> trackIds = upsertTracks(lesson.id(), desiredTracks, existingTracks, passageIds, changes);
        replaceTimings(manifest, lesson.id(), timings, sentenceIds, trackIds, changes);
        upsertWords(manifest, lesson.id(), trackIds, changes);

        return new ImportResult(
                code, changes.count == 0 ? ImportResult.Status.UNCHANGED : ImportResult.Status.IMPORTED, null);
    }

    private record DesiredTrack(
            Integer passageNo, String kind, String variant, String fileKey, int durationMs, int sortOrder) {}

    private static final class Changes {
        int count;

        void add() {
            count++;
        }

        void add(int n) {
            count += n;
        }
    }

    /** 지문 트랙 p{n}:{voice}(sort_order = no × 10 + 목소리 순서), 단어·예문 트랙 w{nn}:us_w(sort_order = 단어 no) */
    private static Map<String, DesiredTrack> desiredTracks(LessonManifest manifest) {
        Map<String, DesiredTrack> tracks = new LinkedHashMap<>();
        for (LessonManifest.Passage passage : manifest.passages()) {
            for (LessonManifest.Track track : passage.tracks()) {
                String variant = passageVariant(passage.no(), track.voice());
                int sortOrder = passage.no() * 10 + LessonManifest.VOICES.indexOf(track.voice());
                tracks.put(
                        key(PASSAGE, variant),
                        new DesiredTrack(
                                passage.no(), PASSAGE, variant, track.fileKey(), track.durationMs(), sortOrder));
            }
        }
        for (LessonManifest.Word word : manifest.words()) {
            String variant = wordVariant(word.no());
            tracks.put(
                    key(WORD, variant),
                    new DesiredTrack(
                            null,
                            WORD,
                            variant,
                            word.track().fileKey(),
                            word.track().durationMs(),
                            word.no()));
            tracks.put(
                    key(WORD_EXAMPLE, variant),
                    new DesiredTrack(
                            null,
                            WORD_EXAMPLE,
                            variant,
                            word.exampleTrack().fileKey(),
                            word.exampleTrack().durationMs(),
                            word.no()));
        }
        return tracks;
    }

    /** 청취 기록이 있는 트랙이 없어지거나 길이가 바뀌면 그 목록, 없으면 null */
    private String listeningViolation(Map<String, DesiredTrack> desired, Map<String, TrackRow> existing) {
        Set<Long> listened = store.tracksWithListening(
                existing.values().stream().map(TrackRow::id).toList());
        List<String> problems = new ArrayList<>();
        for (TrackRow track : existing.values()) {
            if (!listened.contains(track.id())) {
                continue;
            }
            DesiredTrack next = desired.get(key(track.kind(), track.variant()));
            if (next == null) {
                problems.add(track.variant() + " 없어짐");
            } else if (next.durationMs() != track.durationMs()) {
                problems.add(track.variant() + " 길이 " + track.durationMs() + " → " + next.durationMs());
            }
        }
        if (problems.isEmpty()) {
            return null;
        }
        problems.sort(null);
        return "청취 기록이 있는 트랙: " + String.join(", ", problems);
    }

    /** manifest에 없는 행을 외래 키 순서대로 지운다: 시각 → 단어 → 단어 연결 끊기 → 트랙 → 문장 → 지문 */
    private void deleteRemoved(
            LessonManifest manifest,
            long lessonId,
            Map<String, DesiredTrack> desiredTracks,
            Map<String, TrackRow> existingTracks,
            Changes changes) {
        Map<Integer, Integer> sentenceCounts = manifest.passages().stream()
                .collect(Collectors.toMap(
                        LessonManifest.Passage::no, p -> p.sentences().size()));
        Map<Long, Integer> passageNoById = new HashMap<>();
        List<Long> removedPassages = new ArrayList<>();
        for (PassageRow passage : store.passages(lessonId)) {
            passageNoById.put(passage.id(), passage.no());
            if (!sentenceCounts.containsKey(passage.no())) {
                removedPassages.add(passage.id());
            }
        }
        List<Long> removedSentences = new ArrayList<>();
        for (SentenceRow sentence : store.sentences(lessonId)) {
            Integer count = sentenceCounts.get(passageNoById.get(sentence.passageId()));
            if (count == null || sentence.no() > count) {
                removedSentences.add(sentence.id());
            }
        }
        List<Long> removedTracks = existingTracks.entrySet().stream()
                .filter(e -> !desiredTracks.containsKey(e.getKey()))
                .map(e -> e.getValue().id())
                .toList();
        List<Long> removedWords = store.words(lessonId).stream()
                .filter(w ->
                        w.sortOrder() < 1 || w.sortOrder() > manifest.words().size())
                .map(WordRow::id)
                .toList();

        store.deleteTimingsOfTracks(removedTracks);
        store.deleteTimingsOfSentences(removedSentences);
        store.deleteWords(removedWords);
        store.detachWords(removedPassages, removedTracks);
        store.deleteTracks(removedTracks);
        store.deleteSentences(removedSentences);
        store.deletePassages(removedPassages);
        changes.add(removedTracks.size() + removedSentences.size() + removedPassages.size() + removedWords.size());
        removedTracks.forEach(id -> existingTracks.values().removeIf(t -> t.id() == id));
    }

    private Map<Integer, Long> upsertPassages(LessonManifest manifest, long lessonId, Changes changes) {
        Map<Integer, PassageRow> existing = new HashMap<>();
        for (PassageRow row : store.passages(lessonId)) {
            existing.put(row.no(), row);
        }
        Map<Integer, Long> ids = new HashMap<>();
        for (LessonManifest.Passage passage : manifest.passages()) {
            PassageRow row = existing.get(passage.no());
            if (row == null) {
                ids.put(
                        passage.no(),
                        store.insertPassage(lessonId, passage.no(), passage.title(), passage.wordCount()));
                changes.add();
                continue;
            }
            ids.put(passage.no(), row.id());
            if (!row.title().equals(passage.title()) || row.wordCount() != passage.wordCount()) {
                store.updatePassage(row.id(), passage.title(), passage.wordCount());
                changes.add();
            }
        }
        return ids;
    }

    /** @return 지문 no → 문장 id(문장 no 순) */
    private Map<Integer, List<Long>> upsertSentences(
            LessonManifest manifest, long lessonId, Map<Integer, Long> passageIds, Changes changes) {
        Map<Long, Map<Integer, SentenceRow>> existing = new HashMap<>();
        for (SentenceRow row : store.sentences(lessonId)) {
            existing.computeIfAbsent(row.passageId(), k -> new HashMap<>()).put(row.no(), row);
        }
        Map<Integer, List<Long>> ids = new HashMap<>();
        for (LessonManifest.Passage passage : manifest.passages()) {
            long passageId = passageIds.get(passage.no());
            Map<Integer, SentenceRow> rows = existing.getOrDefault(passageId, Map.of());
            List<Long> sentenceIds = new ArrayList<>();
            for (LessonManifest.Sentence sentence : passage.sentences()) {
                SentenceRow row = rows.get(sentence.no());
                if (row == null) {
                    sentenceIds.add(
                            store.insertSentence(passageId, sentence.no(), sentence.text(), sentence.speaker()));
                    changes.add();
                    continue;
                }
                sentenceIds.add(row.id());
                if (!row.text().equals(sentence.text()) || !Objects.equals(row.speaker(), sentence.speaker())) {
                    store.updateSentence(row.id(), sentence.text(), sentence.speaker());
                    changes.add();
                }
            }
            ids.put(passage.no(), sentenceIds);
        }
        return ids;
    }

    /** @return kind|variant → 트랙 id */
    private Map<String, Long> upsertTracks(
            long lessonId,
            Map<String, DesiredTrack> desired,
            Map<String, TrackRow> existing,
            Map<Integer, Long> passageIds,
            Changes changes) {
        Map<String, Long> ids = new HashMap<>();
        for (Map.Entry<String, DesiredTrack> entry : desired.entrySet()) {
            DesiredTrack track = entry.getValue();
            Long passageId = track.passageNo() == null ? null : passageIds.get(track.passageNo());
            TrackRow row = existing.get(entry.getKey());
            if (row == null) {
                ids.put(
                        entry.getKey(),
                        store.insertTrack(
                                lessonId,
                                passageId,
                                track.kind(),
                                track.variant(),
                                track.fileKey(),
                                track.durationMs(),
                                track.sortOrder()));
                changes.add();
                continue;
            }
            ids.put(entry.getKey(), row.id());
            if (!Objects.equals(row.passageId(), passageId)
                    || !row.fileKey().equals(track.fileKey())
                    || row.durationMs() != track.durationMs()
                    || row.sortOrder() != track.sortOrder()) {
                store.updateTrack(row.id(), passageId, track.fileKey(), track.durationMs(), track.sortOrder());
                changes.add();
            }
        }
        return ids;
    }

    /** 지문 트랙마다 문장 시각이 계산값과 다르면 그 트랙의 시각을 지우고 다시 넣는다(approximate = false) */
    private void replaceTimings(
            LessonManifest manifest,
            long lessonId,
            Map<String, List<TimeRange>> timings,
            Map<Integer, List<Long>> sentenceIds,
            Map<String, Long> trackIds,
            Changes changes) {
        Map<Long, Set<TimingRow>> existing = new HashMap<>();
        for (TimingRow row : store.timings(lessonId)) {
            existing.computeIfAbsent(row.trackId(), k -> new HashSet<>()).add(row);
        }
        for (LessonManifest.Passage passage : manifest.passages()) {
            List<Long> sentences = sentenceIds.get(passage.no());
            for (LessonManifest.Track track : passage.tracks()) {
                String variant = passageVariant(passage.no(), track.voice());
                long trackId = trackIds.get(key(PASSAGE, variant));
                List<TimeRange> ranges = timings.get(variant);
                List<TimingRow> rows = new ArrayList<>(ranges.size());
                for (int i = 0; i < ranges.size(); i++) {
                    rows.add(new TimingRow(
                            sentences.get(i),
                            trackId,
                            ranges.get(i).startMs(),
                            ranges.get(i).endMs(),
                            false));
                }
                if (!new HashSet<>(rows).equals(existing.getOrDefault(trackId, Set.of()))) {
                    store.deleteTimingsOfTracks(List.of(trackId));
                    store.insertTimings(rows);
                    changes.add();
                }
            }
        }
    }

    private void upsertWords(LessonManifest manifest, long lessonId, Map<String, Long> trackIds, Changes changes) {
        Map<Integer, WordRow> existing = new HashMap<>();
        for (WordRow row : store.words(lessonId)) {
            existing.put(row.sortOrder(), row);
        }
        for (LessonManifest.Word word : manifest.words()) {
            String variant = wordVariant(word.no());
            Long trackId = trackIds.get(key(WORD, variant));
            Long exampleTrackId = trackIds.get(key(WORD_EXAMPLE, variant));
            WordRow row = existing.get(word.no());
            long id;
            if (row == null) {
                id = store.insertWord(
                        lessonId,
                        word.no(),
                        word.english(),
                        word.korean(),
                        word.pos(),
                        word.exampleEn(),
                        word.exampleKo());
            } else if (row.passageId() == null
                    && row.moeLevel() == null
                    && row.english().equals(word.english())
                    && row.korean().equals(word.korean())
                    && row.pos().equals(word.pos())
                    && row.exampleEn().equals(word.exampleEn())
                    && row.exampleKo().equals(word.exampleKo())
                    && Objects.equals(row.trackId(), trackId)
                    && Objects.equals(row.exampleTrackId(), exampleTrackId)) {
                continue;
            } else {
                id = row.id();
            }
            store.updateWord(
                    id,
                    word.english(),
                    word.korean(),
                    word.pos(),
                    word.exampleEn(),
                    word.exampleKo(),
                    trackId,
                    exampleTrackId);
            changes.add();
        }
    }

    static String passageVariant(int passageNo, String voice) {
        return "p" + passageNo + ":" + voice;
    }

    static String wordVariant(int wordNo) {
        return "w%02d:%s".formatted(wordNo, LessonManifest.WORD_VOICE);
    }

    private static String key(String kind, String variant) {
        return kind + "|" + variant;
    }
}
