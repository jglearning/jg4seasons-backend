# -*- coding: utf-8 -*-
"""build_manifest.py 단어 부분 테스트(이슈 #88). 실행:
    python -B -I -X utf8 -m unittest discover -s scripts/content -p "test_*.py" -v
자료는 임시 폴더에 만든다. 교재 원문은 쓰지 않는다(지어낸 단어·문장)."""
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_manifest as bm  # noqa: E402

BOOK_HEADER = "그린 05회 · 시험 — ③ 어휘 단어장\n\n책 번호 | 영어 | 품사 | 뜻 | 책 쪽 | 예문(책) | 관련어\n"
LESSON_HEADER = "그린 05회 · 시험 — 회차 어휘\n\n번호 | 단어 | 품사 | 뜻 | 예문 | 예문 해석 | 어휘 ID\n"


def mp3(path, frames=10):
    """MPEG1 Layer III 128kbps 44.1kHz 프레임(417바이트)을 frames개. 길이 = frames × 1152 / 44100초"""
    path.parent.mkdir(parents=True, exist_ok=True)
    frame = bytes([0xFF, 0xFB, 0x90, 0x00]) + bytes(413)
    path.write_bytes(frame * frames)
    return path


class BookWordsTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.book_dir = self.root / "새" / "단어"
        self.errors = []

    def tearDown(self):
        self.tmp.cleanup()

    def book(self, rows, audio=True):
        """rows: (번호, 영어, 품사, 뜻, 예문). 음원은 번호마다 '{번호} {영어} · 영어 · 미국 여자.mp3'"""
        self.book_dir.mkdir(parents=True, exist_ok=True)
        lines = [f"{no} | {en} | {pos} | {ko} | 32 | {ex} | " for no, en, pos, ko, ex in rows]
        (self.book_dir / "단어 목록.txt").write_text(BOOK_HEADER + "\n".join(lines) + "\n", encoding="utf-8-sig")
        if audio:
            for no, en, *_ in rows:
                mp3(self.book_dir / f"{no} {en} · 영어 · 미국 여자.mp3")
                mp3(self.book_dir / f"{no} {en} · 영어 · 미국 남자.mp3")
                mp3(self.book_dir / f"{no} {en} · 뜻 · 한국어 여자.mp3")

    def read(self):
        return bm.read_book_words(self.book_dir, "X", self.errors)

    def test_책_단어_순서는_단어_다음_숙어(self):
        self.book([("숙어 01", "far from", "", "결코 ~이 아닌", "It is far from done."),
                   ("01", "aloud", "부", "소리 내어", "Read it aloud."),
                   ("02", "remain", "동", "남다", "Few leaves remain.")])
        words = self.read()
        self.assertEqual([], self.errors)
        self.assertEqual(["aloud", "remain", "far from"], [w["english"] for w in words])

    def test_하위_표현은_넣지_않는다(self):
        self.book([("01", "deal", "명", "거래", "It is a good deal."),
                   ("01-1", "a great deal", "", "많은 양", ""),
                   ("01-2", "deal with", "", "다루다", "")])
        words = self.read()
        self.assertEqual([], self.errors)
        self.assertEqual(["deal"], [w["english"] for w in words])

    def test_품사_여럿이면_첫_품사(self):
        self.book([("01", "post", "명 · 동", "우편 / 올리다", "It came by post."),
                   ("02", "they", "대", "그들", "They are here."),
                   ("숙어 01", "far from", "", "결코 ~이 아닌", "It is far from done.")])
        self.assertEqual(["n", "pron", "phr"], [w["pos"] for w in self.read()])
        self.assertEqual([], self.errors)

    def test_책_단어는_단어_음원만(self):
        self.book([("01", "aloud", "부", "소리 내어", "Read it aloud.")])
        w = self.read()[0]
        self.assertEqual("book", w["source"])
        self.assertEqual("소리 내어", w["korean"])
        self.assertEqual("Read it aloud.", w["exampleEn"])
        self.assertIsNone(w["exampleKo"])
        self.assertEqual("01 aloud · 영어 · 미국 여자.mp3", w["wordFile"].name)
        self.assertIsNone(w["exampleFile"])

    def test_책_음원이_없으면_오류(self):
        self.book([("01", "aloud", "부", "소리 내어", "Read it aloud."),
                   ("02", "remain", "동", "남다", "Few leaves remain.")])
        (self.book_dir / "02 remain · 영어 · 미국 여자.mp3").unlink()
        self.read()
        self.assertTrue(any("단어 02" in e for e in self.errors), self.errors)

    def test_책_번호가_이어지지_않으면_오류(self):
        self.book([("01", "aloud", "부", "소리 내어", "Read it aloud."),
                   ("03", "term", "명", "학기", "The term starts.")])
        self.read()
        self.assertTrue(any("이어지지" in e for e in self.errors), self.errors)

    def test_모르는_품사는_오류(self):
        self.book([("01", "the", "관", "그", "The cat sat.")])
        self.read()
        self.assertTrue(any("품사" in e for e in self.errors), self.errors)

    def test_칸이_밀린_항목은_건너뛰지_않고_오류(self):
        self.book([("01", "aloud", "부", "소리 | 내어", "Read it aloud.")])
        self.read()
        self.assertTrue(any("01: 칸이 8개" in e for e in self.errors), self.errors)

    def test_새_폴더에_회차가_없으면_실패(self):
        with self.assertRaisesRegex(bm.LessonError, "새 어휘책 단어 목록이 없다"):
            bm.read_book_words(None, "X", self.errors)


class MergeWordsTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.errors = []
        self.files = []

    def tearDown(self):
        self.tmp.cleanup()

    def book_word(self, en):
        return {"english": en, "source": "book", "pos": "n", "korean": "뜻", "exampleEn": f"{en} here.",
                "exampleKo": None, "wordFile": mp3(self.root / "새" / f"{en}.mp3", 10), "exampleFile": None}

    def lesson_word(self, en):
        return {"english": en, "pos": "n", "korean": "회차 뜻", "exampleEn": f"New {en}.", "exampleKo": "해석",
                "wordFile": mp3(self.root / "옛" / f"{en}.mp3", 12),
                "exampleFile": mp3(self.root / "옛" / f"{en} ex.mp3", 20)}

    def merge(self, book, lesson):
        return bm.merge_words([self.book_word(w) for w in book], [self.lesson_word(w) for w in lesson],
                              "X", self.errors, self.files)

    def test_겹치는_회차_어휘는_빼고_나머지는_뒤에(self):
        words = self.merge(["aloud", "remain", "far from"], ["remain", "glacier"])
        self.assertEqual([], self.errors)
        self.assertEqual(["aloud", "remain", "far from", "glacier"], [w["english"] for w in words])
        self.assertEqual([1, 2, 3, 4], [w["no"] for w in words])
        self.assertEqual(["book", "book", "book", "extra"], [w["source"] for w in words])
        # 겹친 단어는 새 판 내용만(뜻·예문·음원)
        self.assertEqual("뜻", words[1]["korean"])
        self.assertIsNone(words[1]["exampleTrack"])
        last = words[3]
        self.assertEqual("audio/X/X_w04_us_w.mp3", last["track"]["fileKey"])
        self.assertEqual("audio/X/X_w04_ex_us_w.mp3", last["exampleTrack"]["fileKey"])
        self.assertEqual("해석", last["exampleKo"])

    def test_음원_키_번호는_단어_순서(self):
        words = self.merge(["aloud", "remain"], ["glacier"])
        self.assertEqual(["audio/X/X_w01_us_w.mp3", "audio/X/X_w02_us_w.mp3", "audio/X/X_w03_us_w.mp3"],
                         [w["track"]["fileKey"] for w in words])
        self.assertEqual(round(10 * 1152 * 1000 / 44100), words[0]["track"]["durationMs"])
        self.assertEqual(round(20 * 1152 * 1000 / 44100), words[2]["exampleTrack"]["durationMs"])
        self.assertEqual(4, len(self.files))  # 책 2(단어) + 회차 어휘 1(단어·예문)
        self.assertEqual("audio/X/X_w03_ex_us_w.mp3", self.files[-1][1])

    def test_한쪽이_더_긴_표현은_다른_단어(self):
        words = self.merge(["keep away"], ["keep away from"])
        self.assertEqual(["keep away", "keep away from"], [w["english"] for w in words])

    def test_대소문자와_물결표는_같은_단어(self):
        words = self.merge(["Look forward to ~ing"], ["look forward to ing"])
        self.assertEqual(["Look forward to ~ing"], [w["english"] for w in words])

    def test_단어가_127개를_넘으면_오류(self):
        self.merge([f"w{i}" for i in range(100)], [f"x{i}" for i in range(28)])
        self.assertTrue(any("127" in e for e in self.errors), self.errors)

    def test_manifest_칸_순서(self):
        words = self.merge(["aloud"], [])
        self.assertEqual(["no", "english", "source", "pos", "korean", "exampleEn", "exampleKo", "track",
                          "exampleTrack"], list(words[0]))


class LessonWordsTest(unittest.TestCase):
    """옛 자료 폴더의 회차 어휘 읽기: 음원 파일 경로만 돌려주고 키는 merge가 만든다"""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.lesson = Path(self.tmp.name) / "05회 · 시험"
        self.errors = []

    def tearDown(self):
        self.tmp.cleanup()

    def test_회차_어휘는_단어_예문_음원_경로를_돌려준다(self):
        d = self.lesson / "단어"
        d.mkdir(parents=True)
        (d / "단어 목록.txt").write_text(
            LESSON_HEADER + "01 | glacier | n. | 빙하 | The glacier moved. | 빙하가 움직였다. | X-V01\n",
            encoding="utf-8-sig")
        mp3(d / "01 glacier · 단어 · 영어 · 미국 여자.mp3")
        mp3(d / "01 glacier · 예문 · 영어 · 미국 여자.mp3")
        script = {"어휘": [{"어휘ID": "X-V01", "단어·표현": "glacier", "품사": "n.", "뜻": "빙하",
                          "교재 예문": "The glacier moved."}]}
        words = bm.build_words(self.lesson, script, "X", self.errors)
        self.assertEqual([], self.errors)
        self.assertEqual("빙하가 움직였다.", words[0]["exampleKo"])
        self.assertEqual("01 glacier · 단어 · 영어 · 미국 여자.mp3", words[0]["wordFile"].name)
        self.assertEqual("01 glacier · 예문 · 영어 · 미국 여자.mp3", words[0]["exampleFile"].name)
        self.assertNotIn("track", words[0])


if __name__ == "__main__":
    unittest.main()
