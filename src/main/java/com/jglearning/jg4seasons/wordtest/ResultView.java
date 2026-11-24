package com.jglearning.jg4seasons.wordtest;

/** 응답의 문항 값. 결과는 DB 판정 그대로(correct·wrong·blank·unclear) */
final class ResultView {

    private ResultView() {}

    /** 읽은 글자, 없으면 "" */
    static String written(String recognized) {
        return recognized == null ? "" : recognized;
    }
}
