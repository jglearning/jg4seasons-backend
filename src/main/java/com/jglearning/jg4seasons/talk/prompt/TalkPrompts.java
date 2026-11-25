package com.jglearning.jg4seasons.talk.prompt;

import com.jglearning.jg4seasons.talk.TalkLevel;
import com.jglearning.jg4seasons.talk.TalkTopic;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 프리토킹 지시문 템플릿(기획 §4-2). 클래스패스 {@code prompts/talk/v2/}(v2 = v1 + 말투 문단, #86)의 base·level-*·topic-* 파일을 기동 때 한 번 읽고, 조립은 순수 함수다. 파일이 없거나
 * 비면 기동 실패(빈 지시문으로 조용히 대화하지 않게). 브라우저는 주제·음성만 보내고 지시문은 서버만 만든다
 */
public final class TalkPrompts {

    /** talk_session.prompt_version에 남기는 값. 파일 내용을 바꾸면 새 버전 디렉터리를 만든다 */
    public static final String VERSION = "talk/v2";

    private static final String DEFAULT_DIR = "prompts/" + VERSION;

    private final String base;
    private final Map<TalkLevel, String> levels;
    private final Map<TalkTopic, String> topics;

    private final String closing;

    private TalkPrompts(String base, Map<TalkLevel, String> levels, Map<TalkTopic, String> topics, String closing) {
        this.base = base;
        this.levels = levels;
        this.topics = topics;
        this.closing = closing;
    }

    public static TalkPrompts load() {
        return load(DEFAULT_DIR);
    }

    static TalkPrompts load(String dir) {
        String base = read(dir, "base.txt");
        Map<TalkLevel, String> levels = new EnumMap<>(TalkLevel.class);
        for (TalkLevel level : TalkLevel.values()) {
            levels.put(level, read(dir, "level-" + level.slug() + ".txt"));
        }
        Map<TalkTopic, String> topics = new EnumMap<>(TalkTopic.class);
        for (TalkTopic topic : TalkTopic.values()) {
            topics.put(topic, read(dir, "topic-" + topic.code() + ".txt"));
        }
        return new TalkPrompts(base, levels, topics, read(dir, "closing.txt"));
    }

    /**
     * 시간이 다 됐을 때 브라우저가 연결된 세션에 넣는 마무리 신호 문장(closing.txt). base의 "Ending the conversation" 규칙이 이 신호를 받으면 인사하고 끝내게
     * 한다. 프론트가 문구를 지어내지 않도록 세션 생성 응답으로 내려 준다
     */
    public String closing() {
        return closing;
    }

    /**
     * base → level → topic → 회차 단어(있을 때) → 목표 문법(있을 때). 단어가 없는 lesson 주제(개강 전)는 day 주제 문단으로 대신한다
     *
     * @param words 회차 단어(교재 순서). 비면 단어 줄 없음
     * @param grammar 목표 문법. 비면 문법 줄 없음(1단계는 데이터가 없어 늘 빈 목록)
     */
    public String instructions(TalkLevel level, TalkTopic topic, List<String> words, List<String> grammar) {
        TalkTopic effective = topic == TalkTopic.LESSON && words.isEmpty() ? TalkTopic.DAY : topic;
        StringBuilder out = new StringBuilder()
                .append(base)
                .append("\n\n")
                .append(levels.get(level))
                .append("\n\n")
                .append(topics.get(effective));
        if (!words.isEmpty()) {
            out.append("\n\nLesson words: ").append(String.join(", ", words));
        }
        if (!grammar.isEmpty()) {
            out.append("\n\nTarget grammar: ").append(String.join(", ", grammar));
        }
        return out.toString();
    }

    private static String read(String dir, String name) {
        String path = dir + "/" + name;
        try (InputStream in = TalkPrompts.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("프리토킹 프롬프트 파일이 없다: " + path);
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
            if (text.isEmpty()) {
                throw new IllegalStateException("프리토킹 프롬프트 파일이 비었다: " + path);
            }
            return text;
        } catch (IOException e) {
            throw new UncheckedIOException("프리토킹 프롬프트 파일을 읽지 못했다: " + path, e);
        }
    }
}
