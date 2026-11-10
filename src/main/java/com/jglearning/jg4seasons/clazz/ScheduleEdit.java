package com.jglearning.jg4seasons.clazz;

import com.jglearning.jg4seasons.clazz.api.SessionStatus;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 진도표 편집 규칙(ADR 0030, 4.6·4.7). 순수 함수: 지금 진도표 행(seq 순) → 바뀐 진도표 행. 결과 목록의 순서가 새 seq(1부터) — 날짜 순, 같은 날은 휴강 행 먼저,
 * 그다음 원래 순서. 수업 행 = 휴강(cancelled)이 아닌 행.
 */
final class ScheduleEdit {

    /** 진도표 행. id null = 새로 넣을 행. 날짜·상태 필수, 빈 메모는 null(4.6·4.7 모두 "" = 지움, "메모 없음"은 늘 null) */
    record Row(Long id, long lessonId, LocalDate date, SessionStatus status, String note) {
        Row {
            Objects.requireNonNull(date, "date");
            Objects.requireNonNull(status, "status");
            if (note != null && note.isEmpty()) {
                note = null;
            }
        }

        boolean held() {
            return status != SessionStatus.CANCELLED;
        }

        Row withDate(LocalDate newDate) {
            return new Row(id, lessonId, newDate, status, note);
        }

        Row withNote(String newNote) {
            return new Row(id, lessonId, date, status, newNote);
        }
    }

    private ScheduleEdit() {}

    /**
     * 휴강(4.7). 그 행은 날짜 그대로 휴강·메모(null이면 원래 메모). shiftFollowing이면 같은 회차의 새 수업 행 — 그 행 뒤 수업 행들의 날짜를 새 행과 뒤 회차들이 차례로
     * 받고, 마지막 회차는 다음 수업일. note "" = 지움. 이미 휴강한 행이면 rows 그대로(재시도에 안전, 메모도 바꾸지 않음 — 메모는 4.6으로). 오늘 이전 행이면 409
     * SESSION_PAST
     */
    static List<Row> cancel(
            List<Row> rows, int seq, String note, boolean shiftFollowing, Weekdays weekdays, LocalDate today) {
        int k = indexOf(rows, seq);
        Row target = rows.get(k);
        if (!target.held()) {
            return rows;
        }
        requireNotPast(target, today);
        List<Row> out = new ArrayList<>(rows);
        out.set(
                k,
                new Row(
                        target.id(),
                        target.lessonId(),
                        target.date(),
                        SessionStatus.CANCELLED,
                        note == null ? target.note() : note));
        if (shiftFollowing) {
            List<Integer> followers = heldAfter(out, k);
            List<LocalDate> dates = new ArrayList<>(
                    followers.stream().map(i -> out.get(i).date()).toList());
            LocalDate last = dates.isEmpty() ? target.date() : dates.get(dates.size() - 1);
            dates.add(nextClassDay(last, weekdays, cancelledDates(out)));
            Row moved = new Row(null, target.lessonId(), dates.get(0), SessionStatus.SCHEDULED, null);
            for (int j = 0; j < followers.size(); j++) {
                int i = followers.get(j);
                out.set(i, out.get(i).withDate(dates.get(j + 1)));
            }
            out.add(moved);
        }
        return renumber(out);
    }

    /**
     * 날짜·메모 변경(4.6). note: null 그대로, "" 지움. newDate: null이거나 지금 날짜와 같으면 날짜 변경 없음(밀기도 무시 — 수정 폼이 원래 날짜를 함께 보내도 지난
     * 행·휴강 행의 메모를 바꿀 수 있게). 날짜를 바꾸면 — 휴강 행 400, 오늘 이전 행 409, 새 날짜가 오늘 이전이거나 앞 수업 행 이하면 400, 밀지 않을 때 뒤 수업 행
     * 이상이면 400. shiftFollowing이면 뒤 수업 행들을 새 날짜의 다음 수업일부터 차례로(휴강 행은 그대로, 그 날짜는 건너뜀)
     */
    static List<Row> reschedule(
            List<Row> rows,
            int seq,
            LocalDate newDate,
            String note,
            boolean shiftFollowing,
            Weekdays weekdays,
            LocalDate today) {
        int k = indexOf(rows, seq);
        Row target = rows.get(k);
        Row updated = note == null ? target : target.withNote(note);
        List<Row> out = new ArrayList<>(rows);
        if (newDate == null || newDate.equals(target.date())) {
            out.set(k, updated);
            return renumber(out);
        }
        if (!target.held()) {
            throw ApiException.validation("scheduledDate", "휴강한 수업은 날짜를 바꿀 수 없어요");
        }
        requireNotPast(target, today);
        if (newDate.isBefore(today)) {
            throw ApiException.validation("scheduledDate", "오늘이나 그 뒤 날짜로 골라 주세요");
        }
        Row previous = heldBefore(out, k);
        if (previous != null && !newDate.isAfter(previous.date())) {
            throw ApiException.validation("scheduledDate", "앞 수업보다 뒤 날짜로 골라 주세요");
        }
        List<Integer> followers = heldAfter(out, k);
        if (!shiftFollowing
                && !followers.isEmpty()
                && !newDate.isBefore(out.get(followers.get(0)).date())) {
            throw ApiException.validation("scheduledDate", "뒤 수업보다 앞 날짜로 골라 주세요(뒤 수업도 옮기려면 뒤로 밀기)");
        }
        out.set(k, updated.withDate(newDate));
        if (shiftFollowing) {
            Set<LocalDate> skipped = cancelledDates(out);
            LocalDate previousDate = newDate;
            for (int i : followers) {
                previousDate = nextClassDay(previousDate, weekdays, skipped);
                out.set(i, out.get(i).withDate(previousDate));
            }
        }
        return renumber(out);
    }

    /** after 다음 날부터, 수업 요일이면서 skipped(휴강 날짜)가 아닌 가장 이른 날 */
    static LocalDate nextClassDay(LocalDate after, Weekdays weekdays, Set<LocalDate> skipped) {
        LocalDate day = after.plusDays(1);
        while (!weekdays.includes(day.getDayOfWeek()) || skipped.contains(day)) {
            day = day.plusDays(1);
        }
        return day;
    }

    private static int indexOf(List<Row> rows, int seq) {
        if (seq < 1 || seq > rows.size()) {
            throw new ApiException(CommonErrorCode.NOT_FOUND, "진도표 행을 찾을 수 없어요");
        }
        return seq - 1;
    }

    private static void requireNotPast(Row row, LocalDate today) {
        if (row.date().isBefore(today)) {
            throw new ApiException(ClassErrorCode.SESSION_PAST);
        }
    }

    private static List<Integer> heldAfter(List<Row> rows, int k) {
        List<Integer> indexes = new ArrayList<>();
        for (int i = k + 1; i < rows.size(); i++) {
            if (rows.get(i).held()) {
                indexes.add(i);
            }
        }
        return indexes;
    }

    private static Row heldBefore(List<Row> rows, int k) {
        for (int i = k - 1; i >= 0; i--) {
            if (rows.get(i).held()) {
                return rows.get(i);
            }
        }
        return null;
    }

    private static Set<LocalDate> cancelledDates(List<Row> rows) {
        return rows.stream().filter(row -> !row.held()).map(Row::date).collect(Collectors.toSet());
    }

    /** 날짜 순, 같은 날은 휴강 행 먼저, 그다음 지금 순서(안정 정렬) */
    private static List<Row> renumber(List<Row> rows) {
        return rows.stream()
                .sorted(Comparator.comparing(Row::date).thenComparing(Row::held))
                .toList();
    }
}
