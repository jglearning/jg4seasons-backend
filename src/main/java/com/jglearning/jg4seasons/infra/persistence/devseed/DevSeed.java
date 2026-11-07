package com.jglearning.jg4seasons.infra.persistence.devseed;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/**
 * 로컬·운영 시험(trial) 확인용 데이터: 데모 학원 1 · 관리자 1(로그인 없음) · 반 1(L2-B1, 화·목) · 진도표 24 · 학생 14.
 *
 * <p>실제 학원명은 쓰지 않는다(공개 저장소). 가입 코드는 local이면 공개 값, trial이면 Parameter Store 값(SeedAccessCode). 기준일은 서울 날짜이고, 반은 기준일 14일 전에 개강해 지난 수업·오늘 수업·밀린 수업을 바로 볼 수
 * 있다. 개인 코드·비밀번호(auth_identity)는 인증 이슈에서 추가한다. 이미 데모 학원이 있으면 아무것도 하지 않는다.
 */
public class DevSeed {

    static final String ACADEMY_SLUG = "demo";
    static final String ACCESS_CODE = "G7KM2XP4";
    static final String BOOK_CODE = "L2-B1";
    /** 월~일 비트 문자열. 화·목 */
    static final String WEEKDAYS = "0101000";

    static final int STUDENT_COUNT = 14;
    static final int SESSION_COUNT = 24;
    static final int DAYS_SINCE_START = 14;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final JdbcTemplate jdbc;
    private final Clock clock;

    private final String accessCode;

    /** 공개 가입 코드(G7KM2XP4) — local·테스트 */
    public DevSeed(JdbcTemplate jdbc, Clock clock) {
        this(jdbc, clock, ACCESS_CODE);
    }

    /** @param accessCode 반 가입 코드. 운영 시험(trial)은 SeedAccessCode가 검사한 Parameter Store 값 */
    public DevSeed(JdbcTemplate jdbc, Clock clock, String accessCode) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.accessCode = accessCode;
    }

    /** @return 새로 넣었으면 true, 이미 있어서 건너뛰었으면 false */
    public boolean seed() {
        Integer existing =
                jdbc.queryForObject("select count(*) from academy where slug = ?", Integer.class, ACADEMY_SLUG);
        if (existing != null && existing > 0) {
            return false;
        }

        LocalDate today = LocalDate.now(clock.withZone(SEOUL));
        LocalDate startDate = today.minusDays(DAYS_SINCE_START);
        LocalDateTime nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);

        long academyId = insert(
                "insert into academy (name, slug, status, settings) values (?, ?, 'active', ?)",
                "데모 학원",
                ACADEMY_SLUG,
                "{\"timezone\":\"Asia/Seoul\"}");
        long adminId = insert(
                "insert into `user` (academy_id, role, name, status) values (?, 'admin', ?, 'active')",
                academyId,
                "관리자");
        long bookId = jdbc.queryForObject("select id from book where code = ?", Long.class, BOOK_CODE);
        long classId = insert(
                "insert into `class` (academy_id, book_id, name, start_date, weekdays, access_code,"
                        + " code_issued_at, allow_future_lessons, status)"
                        + " values (?, ?, ?, ?, ?, ?, ?, false, 'active')",
                academyId,
                bookId,
                "겨울 그린 A반",
                startDate,
                WEEKDAYS,
                accessCode,
                nowUtc);
        jdbc.update("insert into class_teacher (class_id, user_id) values (?, ?)", classId, adminId);

        for (int i = 1; i <= STUDENT_COUNT; i++) {
            long studentId = insert(
                    "insert into `user` (academy_id, role, name, school, grade, status)"
                            + " values (?, 'student', ?, ?, ?, 'active')",
                    academyId,
                    String.format("학생%02d", i),
                    "A중",
                    "중1");
            jdbc.update(
                    "insert into enrollment (class_id, student_id, joined_at) values (?, ?, ?)",
                    classId,
                    studentId,
                    startDate);
        }

        List<Long> lessonIds =
                jdbc.queryForList("select id from lesson where book_id = ? order by number", Long.class, bookId);
        List<LocalDate> dates = classDates(startDate, WEEKDAYS, SESSION_COUNT);
        for (int seq = 1; seq <= SESSION_COUNT; seq++) {
            jdbc.update(
                    "insert into class_session (class_id, seq, lesson_id, scheduled_date, status)"
                            + " values (?, ?, ?, ?, 'scheduled')",
                    classId,
                    seq,
                    lessonIds.get(seq - 1),
                    dates.get(seq - 1));
        }
        return true;
    }

    /** startDate(포함)부터 weekdays(월~일 비트 문자열)에 맞는 날짜 count개. 개발 시드 전용 — 진도표 생성 규칙은 반 생성 기능에서 따로 구현 */
    static List<LocalDate> classDates(LocalDate startDate, String weekdays, int count) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate date = startDate;
        while (dates.size() < count) {
            if (weekdays.charAt(date.getDayOfWeek().getValue() - 1) == '1') {
                dates.add(date);
            }
            date = date.plusDays(1);
        }
        return dates;
    }

    private long insert(String sql, Object... args) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(
                connection -> {
                    PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    for (int i = 0; i < args.length; i++) {
                        statement.setObject(i + 1, args[i]);
                    }
                    return statement;
                },
                keys);
        return Objects.requireNonNull(keys.getKey(), "생성된 키 없음").longValue();
    }
}
