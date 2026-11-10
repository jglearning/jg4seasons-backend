package com.jglearning.jg4seasons.content.importer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 회차 하나의 import 입력(ADR 0027). 콘텐츠 스크립트가 원고와 음원 길이로 만든다.
 *
 * <p>생성자가 DB에 넣기 전에 알 수 있는 것을 모두 검사한다: 번호가 1부터 이어지는지, 지문마다 목소리 4개인지, 트랙의 문장 길이 수가 문장 수와 같은지, 파일 키가 S3 키
 * 규칙과 같은지, 칸 길이가 DB 한도 안인지. 문장 시각(간격)은 {@link PassageTimings}가 검사한다. 위반하면 IllegalArgumentException.
 */
public record LessonManifest(String code, List<Passage> passages, List<Word> words) {

    /** 레벨 1~4는 L1~L4, 블랙은 K. 회차는 두 자리 번호이거나 블랙 보충 S1~S4 */
    private static final Pattern CODE = Pattern.compile("^(?:L[1-4]|K)-B[1-4]-(?:\\d{2}|S[1-4])$");

    public static final List<String> VOICES = List.of("us_w", "us_m", "uk_w", "uk_m");
    /** 단어·예문 음원은 미국 여자 하나(ADR 0027) */
    public static final String WORD_VOICE = "us_w";

    /** 품사. 어휘책에 대명사·감탄사가 있다(ADR 0027 결정 6) */
    private static final Set<String> POS = Set.of("n", "v", "adj", "adv", "prep", "conj", "pron", "interj", "phr");

    /** 단어 음원 키의 음원 번호. 단어 순서(no)와 따로다 — 음원은 회차 어휘 번호로 만들었고 단어 순서는 어휘책(교재) 순서다 */
    private static final Pattern WORD_AUDIO_NO = Pattern.compile("_w(\\d{2,3})_us_w\\.mp3$");

    // DB 칸 길이(문자 수)
    static final int PASSAGE_TITLE_MAX = 120;
    static final int SENTENCE_MAX = 800;
    static final int SPEAKER_MAX = 20;
    static final int ENGLISH_MAX = 50;
    static final int KOREAN_MAX = 100;
    static final int EXAMPLE_MAX = 300;

    public LessonManifest {
        if (code == null || !CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("회차 코드 형식이 다르다: " + code);
        }
        String where = code;
        passages = list(where, "passages", passages);
        words = list(where, "words", words);
        if (passages.isEmpty()) {
            throw new IllegalArgumentException(where + ": 지문이 없다");
        }
        for (int i = 0; i < passages.size(); i++) {
            Passage passage = passages.get(i);
            if (passage.no() != i + 1) {
                throw new IllegalArgumentException(
                        where + ": 지문 번호가 1부터 이어지지 않는다(" + (i + 1) + "번째가 " + passage.no() + ")");
            }
            for (Track track : passage.tracks()) {
                expectKey(
                        where + " 지문 " + passage.no(), track.fileKey(), passageKey(code, passage.no(), track.voice()));
            }
        }
        Set<Integer> audioNos = new HashSet<>();
        for (int i = 0; i < words.size(); i++) {
            Word word = words.get(i);
            if (word.no() != i + 1) {
                throw new IllegalArgumentException(
                        where + ": 단어 번호가 1부터 이어지지 않는다(" + (i + 1) + "번째가 " + word.no() + ")");
            }
            Integer audioNo = word.audioNo();
            if (audioNo == null) {
                continue;
            }
            String at = where + " 단어 " + word.no();
            expectKey(at, word.track().fileKey(), wordKey(code, audioNo, false));
            expectKey(at, word.exampleTrack().fileKey(), wordKey(code, audioNo, true));
            if (!audioNos.add(audioNo)) {
                throw new IllegalArgumentException(at + ": 음원 번호 " + audioNo + "가 다른 단어와 겹친다");
            }
        }
    }

    /** {@code audio/{code}/{code}_p{n}_{voice}.mp3} */
    public static String passageKey(String code, int passageNo, String voice) {
        return "audio/" + code + "/" + code + "_p" + passageNo + "_" + voice + ".mp3";
    }

    /** {@code audio/{code}/{code}_w{nn}_us_w.mp3}, 예문은 {@code _w{nn}_ex_us_w.mp3}. nn은 음원 번호 */
    public static String wordKey(String code, int audioNo, boolean example) {
        return "audio/" + code + "/" + code + "_w" + "%02d".formatted(audioNo) + (example ? "_ex_" : "_") + WORD_VOICE
                + ".mp3";
    }

    public record Passage(int no, String title, int wordCount, List<Sentence> sentences, List<Track> tracks) {
        public Passage {
            String where = "지문 " + no;
            text(where, "title", title, PASSAGE_TITLE_MAX);
            if (wordCount <= 0) {
                throw new IllegalArgumentException(where + ": 단어 수는 0보다 커야 한다: " + wordCount);
            }
            sentences = list(where, "sentences", sentences);
            tracks = list(where, "tracks", tracks);
            if (sentences.isEmpty()) {
                throw new IllegalArgumentException(where + ": 문장이 없다");
            }
            // 문장·트랙의 칸은 여기서 검사한다(오류 메시지에 지문 번호를 넣으려고)
            for (int i = 0; i < sentences.size(); i++) {
                Sentence sentence = sentences.get(i);
                if (sentence.no() != i + 1) {
                    throw new IllegalArgumentException(
                            where + ": 문장 번호가 1부터 이어지지 않는다(" + (i + 1) + "번째가 " + sentence.no() + ")");
                }
                String at = where + " 문장 " + sentence.no();
                text(at, "text", sentence.text(), SENTENCE_MAX);
                if (sentence.speaker() != null) {
                    text(at, "speaker", sentence.speaker(), SPEAKER_MAX);
                }
            }
            List<String> voices =
                    tracks.stream().map(t -> String.valueOf(t.voice())).sorted().toList();
            if (!voices.equals(VOICES.stream().sorted().toList())) {
                throw new IllegalArgumentException(where + ": 목소리는 " + VOICES + " 하나씩이어야 한다: " + voices);
            }
            for (Track track : tracks) {
                String at = where + " " + track.voice();
                text(at, "fileKey", track.fileKey(), Integer.MAX_VALUE);
                positive(at, track.durationMs());
                List<Integer> sentenceMs = list(at, "sentenceMs", track.sentenceMs());
                if (sentenceMs.size() != sentences.size()) {
                    throw new IllegalArgumentException(
                            at + ": 문장 길이 " + sentenceMs.size() + "개, 문장 " + sentences.size() + "개");
                }
            }
        }
    }

    /** speaker는 대화문의 화자, 내레이션이면 null. 칸 검사는 {@link Passage}가 한다 */
    public record Sentence(int no, String text, String speaker) {}

    /** 지문 트랙 하나(목소리 하나). sentenceMs는 문장 파일마다의 길이. 칸 검사는 {@link Passage}가 한다 */
    public record Track(String voice, String fileKey, int durationMs, List<Integer> sentenceMs) {
        public Track {
            sentenceMs = sentenceMs == null ? null : Collections.unmodifiableList(new ArrayList<>(sentenceMs));
        }
    }

    public record Word(
            int no,
            String english,
            String pos,
            String korean,
            String exampleEn,
            String exampleKo,
            Audio track,
            Audio exampleTrack) {
        public Word {
            String where = "단어 " + no;
            text(where, "english", english, ENGLISH_MAX);
            if (pos == null || !POS.contains(pos)) {
                throw new IllegalArgumentException(where + ": 모르는 품사: " + pos);
            }
            text(where, "korean", korean, KOREAN_MAX);
            text(where, "exampleEn", exampleEn, EXAMPLE_MAX);
            // 어휘책에만 있는 단어는 예문 해석·음원이 없다(ADR 0027 결정 6)
            if (exampleKo != null) {
                text(where, "exampleKo", exampleKo, EXAMPLE_MAX);
            }
            if ((track == null) != (exampleTrack == null)) {
                throw new IllegalArgumentException(where + ": 단어 음원과 예문 음원은 함께 있거나 함께 없어야 한다");
            }
            if (track != null) {
                positive(where, track.durationMs());
                positive(where, exampleTrack.durationMs());
                if (track.fileKey() == null
                        || !WORD_AUDIO_NO.matcher(track.fileKey()).find()) {
                    throw new IllegalArgumentException(where + ": 단어 음원 키 형식이 다르다: " + track.fileKey());
                }
            }
        }

        /** 음원 번호(키의 w{nn}). 음원이 없으면 null */
        public Integer audioNo() {
            if (track == null) {
                return null;
            }
            Matcher m = WORD_AUDIO_NO.matcher(track.fileKey());
            if (!m.find()) {
                throw new IllegalStateException("단어 음원 키 형식이 다르다: " + track.fileKey());
            }
            return Integer.parseInt(m.group(1));
        }
    }

    /** 단어·예문 음원 */
    public record Audio(String fileKey, int durationMs) {}

    private static void text(String where, String field, String value, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(where + ": " + field + "이(가) 비었다");
        }
        // MySQL VARCHAR는 문자(코드 포인트) 수로 센다
        int length = value.codePointCount(0, value.length());
        if (length > max) {
            throw new IllegalArgumentException(where + ": " + field + " " + length + "자, 한도 " + max + "자");
        }
    }

    private static void positive(String where, int durationMs) {
        if (durationMs <= 0) {
            throw new IllegalArgumentException(where + ": 음원 길이는 0보다 커야 한다: " + durationMs);
        }
    }

    private static <T> List<T> list(String where, String field, List<T> value) {
        if (value == null || value.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(where + ": " + field + "이(가) 없거나 빈 항목이 있다");
        }
        return List.copyOf(value);
    }

    private static void expectKey(String where, String actual, String expected) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException(where + ": 파일 키가 " + expected + "이어야 한다: " + actual);
        }
    }
}
