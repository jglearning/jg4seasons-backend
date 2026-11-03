-- 기준 데이터: 레벨·권·회차·완료 규칙. 머지된 뒤에는 고치지 않는다. 바꿀 것은 새 버전으로.
-- 레벨 색: 디자인가이드 §2.1(화면용, 흰 글자 대비) · 속도·책 제목: 교재 설명 자료 v0.28 · 완료 규칙: 기능명세 §7.2
-- grammar_item·lesson_grammar_item은 회차 마스터 수령 후

-- 레벨 ------------------------------------------------------------------
INSERT INTO level (code, name_ko, name_en, color_hex, sort_order, passage_speeds) VALUES
    ('L1', '민트',   'Mint',  '#0F8A74', 1, JSON_ARRAY('0.8', '1.0')),
    ('L2', '그린',   'Green', '#1F7A4F', 2, JSON_ARRAY('0.8', '1.0', '1.2')),
    ('L3', '블루',   'Blue',  '#2563EB', 3, JSON_ARRAY('0.8', '1.0', '1.2')),
    ('L4', '네이비', 'Navy',  '#1E3A8A', 4, JSON_ARRAY('1.0', '1.2')),
    ('K',  '블랙',   'Black', '#111827', 5, JSON_ARRAY('1.0', '1.2'));

-- 권 (season 1 겨울 · 2 봄 · 3 여름 · 4 가을). Black은 B1 하나, 화면 묶음 라벨은 "블랙 n" ---------------
INSERT INTO book (level_id, season, code, title)
SELECT l.id, b.season, CONCAT(l.code, '-B', b.season), b.title
FROM level l
JOIN (
              SELECT 'L1' AS level_code, 1 AS season, '문장의 형식과 동사' AS title
    UNION ALL SELECT 'L1', 2, '말의 종류'
    UNION ALL SELECT 'L1', 3, '문장 넓히기'
    UNION ALL SELECT 'L1', 4, '중1 준비'
    UNION ALL SELECT 'L2', 1, '문장의 형식과 동사'
    UNION ALL SELECT 'L2', 2, '준동사'
    UNION ALL SELECT 'L2', 3, '기본 품사'
    UNION ALL SELECT 'L2', 4, '접속사와 관계사'
    UNION ALL SELECT 'L3', 1, '문장의 형식과 동사'
    UNION ALL SELECT 'L3', 2, '준동사'
    UNION ALL SELECT 'L3', 3, '품사와 연결'
    UNION ALL SELECT 'L3', 4, '절과 특수구문'
    UNION ALL SELECT 'L4', 1, '문장의 형식과 동사'
    UNION ALL SELECT 'L4', 2, '준동사'
    UNION ALL SELECT 'L4', 3, '품사와 연결'
    UNION ALL SELECT 'L4', 4, '절과 특수구문'
    UNION ALL SELECT 'K',  1, '고1 내신 실전'
) b ON b.level_code = l.code;

-- 회차: 권마다 1~24. 코드 {권}-{nn}, 묶음(module) 1~8·9~16·17~24, 8·16·24회는 묶음 정리 -------------
INSERT INTO lesson (book_id, code, number, module, type, title, passage_count)
WITH RECURSIVE n (num) AS (
    SELECT 1
    UNION ALL
    SELECT num + 1 FROM n WHERE num < 24
)
SELECT b.id,
       CONCAT(b.code, '-', LPAD(n.num, 2, '0')),
       n.num,
       (n.num - 1) DIV 8 + 1,
       IF(n.num % 8 = 0, 'module_review', 'regular'),
       CONCAT('Reading Day ', n.num),
       6
FROM book b
CROSS JOIN n;

-- 완료 규칙: 지문 6개 각각 필수 속도 하나 이상 청취율 0.9. 빈칸은 완료 조건 아님(O-10) ---------------
INSERT INTO completion_rule (level_id, coverage_threshold, required_tracks, cloze_required_ratio, cloze_hint)
SELECT l.id,
       0.900,
       JSON_ARRAY(JSON_OBJECT(
           'kind', 'passage',
           'eachPassage', TRUE,
           'anyOf', IF(l.code = 'L1', JSON_ARRAY('0.8', '1.0'), JSON_ARRAY('1.0')))),
       NULL,
       JSON_OBJECT('firstLetterAt', 3, 'answerAt', 4)
FROM level l;
