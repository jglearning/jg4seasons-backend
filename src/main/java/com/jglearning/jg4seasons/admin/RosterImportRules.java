package com.jglearning.jg4seasons.admin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * 명단 가져오기에서 요청 안만 보고 정하는 규칙(ADR 0036). DB를 보지 않는 순수 함수 — 권·교사·반·사용권은 서비스가 따로 본다.
 *
 * <p>오류 순서: 전체 학생 수(맨 앞) → 반 항목 순서대로 반 고르기·같은 학생. 같은 학생은 처음 나온 줄을 기준으로 뒤에 나온 줄마다 오류.
 */
final class RosterImportRules {

    /** 한 번에 가져올 수 있는 전체 학생 수 */
    static final int MAX_STUDENTS = 500;

    private RosterImportRules() {}

    /**
     * 반 항목 하나. label은 문구에 쓰는 반 이름(새 반 = 받은 이름 앞뒤 공백 제거, 기존 반 = 그 반 이름 — 서비스가 채운다). exactlyOneChoice = 기존 반
     * id와 새 반 중 정확히 하나를 받았는가
     */
    record Draft(String label, boolean exactlyOneChoice, List<Student> students) {}

    record Student(
            String name, @Nullable String school, @Nullable String grade) {}

    static List<RosterIssue> inspect(List<Draft> drafts) {
        List<RosterIssue> issues = new ArrayList<>();
        int total = drafts.stream().mapToInt(d -> d.students().size()).sum();
        if (total > MAX_STUDENTS) {
            issues.add(validation("classes", "학생은 모두 " + MAX_STUDENTS + "명까지예요"));
        }
        Map<String, Integer> firstClassOf = new HashMap<>();
        for (int c = 0; c < drafts.size(); c++) {
            Draft draft = drafts.get(c);
            if (!draft.exactlyOneChoice()) {
                issues.add(validation("classes[" + c + "]", "기존 반과 새 반 중 하나만 골라 주세요"));
            }
            for (int i = 0; i < draft.students().size(); i++) {
                Student student = draft.students().get(i);
                Integer first = firstClassOf.putIfAbsent(studentKey(student), c);
                if (first == null) {
                    continue;
                }
                String who = display(student);
                String name = clean(student.name());
                String field = "classes[" + c + "].students[" + i + "].name";
                if (first == c) {
                    issues.add(validation(field, who + subject(name) + " " + draft.label() + "에 두 번 있어요. 한 줄만 남겨 주세요"));
                } else {
                    String firstLabel = drafts.get(first).label();
                    issues.add(validation(
                            field,
                            who + subject(name) + " " + firstLabel + with(firstLabel) + " " + draft.label()
                                    + "에 모두 있어요. 한 반만 남겨 주세요"));
                }
            }
        }
        return issues;
    }

    /** 같은 학생 판정 키: 이름·학교·학년 앞뒤 공백 제거, null과 ""는 같음 */
    static String studentKey(Student student) {
        return clean(student.name()) + "\u0000" + clean(student.school()) + "\u0000" + clean(student.grade());
    }

    /** 문구용: `김민준(A중·중1)`, 학교·학년이 없으면 이름만 */
    private static String display(Student student) {
        List<String> parts = new ArrayList<>();
        if (!clean(student.school()).isEmpty()) {
            parts.add(clean(student.school()));
        }
        if (!clean(student.grade()).isEmpty()) {
            parts.add(clean(student.grade()));
        }
        String name = clean(student.name());
        return parts.isEmpty() ? name : name + "(" + String.join("·", parts) + ")";
    }

    private static String clean(@Nullable String value) {
        return value == null ? "" : value.strip();
    }

    private static RosterIssue validation(String field, String message) {
        return new RosterIssue(field, RosterIssue.VALIDATION, message);
    }

    /** 주격 조사 이/가 */
    private static String subject(String word) {
        Boolean batchim = endsWithBatchim(word);
        return batchim == null ? "이(가)" : batchim ? "이" : "가";
    }

    /** 접속 조사 과/와 */
    private static String with(String word) {
        Boolean batchim = endsWithBatchim(word);
        return batchim == null ? "과(와)" : batchim ? "과" : "와";
    }

    /**
     * 끝 글자에 받침이 있는가. 괄호가 있으면 첫 `(` 앞 낱말로 본다(`그린(월수)` → 그린 — 조사는 괄호 앞 낱말에 맞춘다). 한글·숫자·영문 일부만 판단하고, 모르면
     * null
     */
    private static @Nullable Boolean endsWithBatchim(String word) {
        int paren = word.indexOf('(');
        String head = (paren >= 0 ? word.substring(0, paren) : word).strip();
        if (head.isEmpty()) {
            return null;
        }
        char last = head.charAt(head.length() - 1);
        if (last >= '가' && last <= '힣') {
            return (last - '가') % 28 != 0;
        }
        if (last >= '0' && last <= '9') {
            return "013678".indexOf(last) >= 0; // 영·일·삼·육·칠·팔
        }
        char upper = Character.toUpperCase(last);
        if (upper >= 'A' && upper <= 'Z') {
            return "LMNR".indexOf(upper) >= 0; // 엘·엠·엔·알
        }
        return null;
    }
}
