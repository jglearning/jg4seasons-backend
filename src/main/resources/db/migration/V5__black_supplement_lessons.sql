-- V5: 블랙 보충 회차 4개를 진도표 수업일로 넣는다(ADR-27).
-- 블랙 진도표 순서: 1~6 → 보충1 → 7~12 → 보충2 → 13~18 → 보충3 → 19~24 → 보충4(28회).
-- lesson.number = 권 안 진도표 순서. 교재 n회의 number = n + (n - 1) DIV 6, 보충 k의 number = 7k. 화면 표시는 회차 code 끝(07, S1).

ALTER TABLE lesson
    MODIFY type ENUM ('regular', 'module_review', 'integrated', 'supplement') NOT NULL;

UPDATE book SET lesson_count = 28 WHERE code = 'K-B1';

-- 1) 본 회차 번호 옮기기. uk_lesson_book_number 충돌을 피해 먼저 100을 더한 뒤 최종값으로
UPDATE lesson l JOIN book b ON b.id = l.book_id
SET l.number = l.number + 100
WHERE b.code = 'K-B1';

UPDATE lesson l JOIN book b ON b.id = l.book_id
SET l.number = (l.number - 100) + (l.number - 101) DIV 6
WHERE b.code = 'K-B1';

-- 2) 보충 회차
INSERT INTO lesson (book_id, code, number, module, type, title, passage_count)
SELECT b.id, CONCAT(b.code, '-S', s.k), s.k * 7, 1, 'supplement', CONCAT('보충 ', s.k), 6
FROM book b
JOIN (SELECT 1 AS k UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4) s
WHERE b.code = 'K-B1';

-- 3) 블랙 묶음(8회씩)과 종류: 블랙 본 회차는 모두 regular(V2가 8·16·24를 module_review로 넣었다)
UPDATE lesson l JOIN book b ON b.id = l.book_id
SET l.module = (l.number - 1) DIV 8 + 1,
    l.type   = IF(l.type = 'supplement', 'supplement', 'regular')
WHERE b.code = 'K-B1';

ALTER TABLE lesson MODIFY number TINYINT NOT NULL COMMENT '권 안 진도표 순서(블랙 1~28, 그 밖 1~24)';
