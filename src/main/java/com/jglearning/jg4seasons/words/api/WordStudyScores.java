package com.jglearning.jg4seasons.words.api;

/** 학생 한 명의 회차 단어 학습 결과(word_study): 카드 본 수·암기 "알아요" 최고·리콜 최고·스펠 최고. */
public record WordStudyScores(int cardsViewed, int memKnown, int recallBest, int spellBest) {}
