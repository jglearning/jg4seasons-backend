package com.jglearning.jg4seasons.words.api;

/**
 * 학생 한 명의 회차 단어 학습 결과(word_study): 카드 본 수·암기 "알아요" 최고·리콜 최고·스펠 최고. 음수는 데이터 오류(IllegalStateException → 500). 회차 단어
 * 수와의 관계는 이 타입이 모른다(단어 수는 content).
 */
public record WordStudyScores(int cardsViewed, int memKnown, int recallBest, int spellBest) {
    public WordStudyScores {
        if (cardsViewed < 0 || memKnown < 0 || recallBest < 0 || spellBest < 0) {
            throw new IllegalStateException(
                    "단어 학습 점수는 0 이상이어야 한다: " + cardsViewed + ", " + memKnown + ", " + recallBest + ", " + spellBest);
        }
    }
}
