package com.jglearning.jg4seasons.content.api;

/** 권의 회차 한 줄(회차 목록·홈). module은 8개씩 묶음 1~3(블랙 1~4), type은 regular·module_review·integrated·supplement(블랙 보충), passageCount는 지문 행 수(아직 없으면 0). */
public record LessonSummary(
        long lessonId, String code, int number, int module, String title, String type, int passageCount) {}
