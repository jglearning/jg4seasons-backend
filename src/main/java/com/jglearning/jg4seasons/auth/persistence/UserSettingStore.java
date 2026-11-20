package com.jglearning.jg4seasons.auth.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 학생 설정(user_setting, ADR 0035) 읽기·쓰기. 학생 1명당 1행이고 설정마다 칸이 있다(지금은 목소리 하나).
 *
 * <p>이 표에는 academy_id가 없다. 읽기는 user를 조인해 학원도 조건에 넣는다 — 다른 학원의 토큰으로는 읽히지 않는다. 쓰기는 호출자가 그 학원의 활성 학생임을 확인한 같은
 * 트랜잭션에서 부른다.
 */
@Component
public class UserSettingStore {

    /** 저장된 설정 한 행. voice가 null이면 고른 적이 없거나 지운 것 */
    public record Row(String voice) {}

    private final JdbcTemplate jdbc;

    public UserSettingStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 그 학원의 그 사용자의 설정. 저장한 적이 없으면 빈 값 — 읽기는 행을 만들지 않는다 */
    public Optional<Row> find(long academyId, long userId) {
        List<Row> rows = jdbc.query(
                "select s.voice from user_setting s join `user` u on u.id = s.user_id"
                        + " where u.academy_id = ? and u.id = ?",
                (rs, i) -> new Row(rs.getString("voice")),
                academyId,
                userId);
        return rows.stream().findFirst();
    }

    /**
     * 목소리 칸만 쓴다(null이면 비운다). 행이 없으면 만든다 — 한 문장의 upsert라 처음 저장이 동시에 와도 행은 하나이고 나중에 쓴 값이 남는다. "조회해서 없으면 insert"는
     * 동시에 둘 다 없다고 보고 한쪽이 중복 키 오류가 난다. 네 목소리 밖의 값은 DB 제약이 거부한다.
     */
    public void saveVoice(long userId, String voice) {
        jdbc.update(
                "insert into user_setting (user_id, voice) values (?, ?) on duplicate key update voice = ?",
                userId,
                voice,
                voice);
    }
}
