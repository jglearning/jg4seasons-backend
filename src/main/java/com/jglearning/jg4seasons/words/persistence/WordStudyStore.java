package com.jglearning.jg4seasons.words.persistence;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** word_study 읽기·쓰기. academy_id 칸이 없어, 호출자가 학생의 학원 소속을 먼저 확인한다. 시각은 UTC LocalDateTime. */
@Component
public class WordStudyStore {

    /** 저장된 값(누적·최고 기록) */
    public record WordStudyRow(int cardsViewed, int memKnown, int recallBest, int spellBest, boolean allViewed) {}

    private final JdbcTemplate jdbc;

    public WordStudyStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Integer> memKnown(long studentId, long lessonId) {
        return jdbc
                .query(
                        "select mem_known from word_study where student_id = ? and lesson_id = ?",
                        (rs, i) -> rs.getInt("mem_known"),
                        studentId,
                        lessonId)
                .stream()
                .findFirst();
    }

    /**
     * 한 문장 upsert(2.5, ADR 0029). 칸마다 GREATEST(저장값, 새 값) — 안 보낸 칸은 0을 넘긴다(0은 저장값을 바꾸지 않는다). all_viewed_at은 비어 있고 새
     * cards_viewed가 회차 단어 수(wordTotal > 0)에 닿았을 때만 now. 같은 행을 동시에 처음 만들어도 중복 키가 갱신으로 바뀌어 둘 다 성공한다. 같은 트랜잭션 안에서
     * 저장 뒤 행을 잠금 읽기로 돌려준다.
     */
    public WordStudyRow save(
            long studentId,
            long lessonId,
            int cardsViewed,
            int memKnown,
            int recallCorrect,
            int spellCorrect,
            int wordTotal,
            Instant now) {
        LocalDateTime at = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        boolean allViewedNow = wordTotal > 0 && cardsViewed >= wordTotal;
        // all_viewed_at을 맨 앞에 둔다: 대입은 왼쪽부터라 뒤에 두면 갱신된 cards_viewed를 읽는다(지금 식은 GREATEST라 순서와 무관하지만 고정)
        jdbc.update(
                "insert into word_study (student_id, lesson_id, cards_viewed, mem_known, recall_best, spell_best,"
                        + " all_viewed_at, last_viewed_at) values (?, ?, ?, ?, ?, ?, ?, ?) as new"
                        + " on duplicate key update"
                        + " all_viewed_at = coalesce(word_study.all_viewed_at,"
                        + "   if(? > 0 and greatest(word_study.cards_viewed, new.cards_viewed) >= ?, new.last_viewed_at, null)),"
                        + " cards_viewed = greatest(word_study.cards_viewed, new.cards_viewed),"
                        + " mem_known = greatest(word_study.mem_known, new.mem_known),"
                        + " recall_best = greatest(word_study.recall_best, new.recall_best),"
                        + " spell_best = greatest(word_study.spell_best, new.spell_best),"
                        + " last_viewed_at = new.last_viewed_at",
                studentId,
                lessonId,
                cardsViewed,
                memKnown,
                recallCorrect,
                spellCorrect,
                allViewedNow ? at : null,
                at,
                wordTotal,
                wordTotal);
        return jdbc.queryForObject(
                "select cards_viewed, mem_known, recall_best, spell_best, all_viewed_at from word_study"
                        + " where student_id = ? and lesson_id = ? for update",
                (rs, i) -> new WordStudyRow(
                        rs.getInt("cards_viewed"),
                        rs.getInt("mem_known"),
                        rs.getInt("recall_best"),
                        rs.getInt("spell_best"),
                        rs.getObject("all_viewed_at") != null),
                studentId,
                lessonId);
    }
}
