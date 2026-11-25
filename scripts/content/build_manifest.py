# -*- coding: utf-8 -*-
"""녹음된 교재 자료 폴더에서 회차별 manifest와 업로드용 음원 폴더를 만든다(ADR-27).

사용법:
    python -I -X utf8 scripts/content/build_manifest.py <자료 폴더> <출력 폴더> --book-words <새 어휘책 결과물 폴더>
        [--word-test <단어시험 문항 TSV> ...] [--only 코드,코드]

- 자료 폴더: 레벨 폴더(1_…~5_…) 아래 회차 폴더(`NN회 · 제목`, `보충N회 · 제목`)가 있는 결과물 폴더. 지문과 회차 어휘를 읽는다
- --book-words: 새 어휘책 판 단어 음원 결과물 폴더. 자료 폴더와 같은 {레벨}/{회차} 아래 `단어/단어 목록.txt`와 음원(이슈 #88)
- 출력 폴더: 저장소 밖에 둔다. 교재 원문(manifest)과 음원은 저장소에 넣지 않는다
  - manifest/{회차 코드}.json  앱의 import 실행기가 읽는다
  - audio/{회차 코드}/…        S3 키 이름 그대로(하드 링크, 안 되면 복사)
- 표준 라이브러리만 쓴다. mp3 길이는 프레임을 세서 구한다
- 오류는 회차별로 모아 마지막에 보이고 종료 코드 1
- --word-test: 단어시험 문항 TSV(열 lesson_code section no book_page prompt answer hint). 행이 있는 회차만 manifest에
  wordTests를 넣는다(없는 회차는 칸이 없어 앱이 그 회차 문항을 건드리지 않는다). book_page는 버린다
  - wordTests는 그 회차 모든 영역의 전체 목록이다. 영역별 파일이면 그 회차에 넣을 파일을 모두 준다(여러 번 줄 수 있음).
    하나만 주면 앱이 다른 영역 문항을 지운다
  - 따옴표를 특별히 다루지 않는다(칸 안의 "는 글자 그대로). 칸 수가 7이 아닌 행은 행 번호와 함께 실패
"""
import argparse
import csv
import json
import os
import re
import shutil
import statistics
import sys
from pathlib import Path

VOICES = {"us_w": "미국 여자", "us_m": "미국 남자", "uk_w": "영국 여자", "uk_m": "영국 남자"}
WORD_VOICE = "us_w"  # 단어·예문 음원은 미국 여자 하나(1차)
BOOK_WORD_AUDIO = " · 영어 · 미국 여자.mp3"  # 새 어휘책 판 단어 음원 파일 이름 끝
POS = {"n.": "n", "v.": "v", "adj.": "adj", "adv.": "adv", "prep.": "prep", "conj.": "conj", "구": "phr"}
# 새 어휘책 판 품사 한 글자. 숙어는 품사가 비어 있고 phr. `명 · 동`처럼 여럿이면 첫 품사
BOOK_POS = {"명": "n", "동": "v", "형": "adj", "부": "adv", "전": "prep", "접": "conj", "대": "pron", "감": "interj"}
NARRATOR = "내레이터"
LIMITS = {"passage title": 120, "sentence": 800, "english": 50, "korean": 100,
          "example": 300, "example ko": 300, "speaker": 20}
# 단어시험 문항 칸 한도(V12, LessonManifest.WordTestItem과 같음)
TEST_LIMITS = {"prompt": 200, "answer": 100, "hint": 50}
TEST_SECTIONS = ("A", "B", "C", "D")
LESSON_DIR = re.compile(r"^(?:\d{2}회|보충\d회) · (.+)$")
CORE_PASSAGE_DIR = "핵심 지문"
GAP_MIN_MS, GAP_MAX_MS = 0, 3000  # import 검증과 같은 범위. 분포는 요약으로 따로 본다

# --- mp3 길이 -------------------------------------------------------------------------------
_BITRATE = {1: [0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320],
            2: [0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160]}
_RATE = {3: [44100, 48000, 32000], 2: [22050, 24000, 16000], 0: [11025, 12000, 8000]}


def mp3_ms(path):
    """MPEG Layer III 프레임 수 × 프레임당 샘플 / 샘플레이트. Xing·Info 프레임은 세지 않는다."""
    b = path.read_bytes()
    i = 0
    if b[:3] == b"ID3":
        i = 10 + ((b[6] << 21) | (b[7] << 14) | (b[8] << 7) | b[9])
    frames = samples = 0
    rate = None
    while i + 4 <= len(b):
        if b[i] != 0xFF or (b[i + 1] & 0xE0) != 0xE0:
            i += 1
            continue
        ver, layer = (b[i + 1] >> 3) & 3, (b[i + 1] >> 1) & 3
        bri, sri, pad = b[i + 2] >> 4, (b[i + 2] >> 2) & 3, (b[i + 2] >> 1) & 1
        if ver == 1 or layer != 1 or bri in (0, 15) or sri == 3:
            i += 1
            continue
        sr = _RATE[ver][sri]
        spf = 1152 if ver == 3 else 576
        flen = (spf // 8) * _BITRATE[1 if ver == 3 else 2][bri] * 1000 // sr + pad
        if frames == 0 and (b"Xing" in b[i:i + 60] or b"Info" in b[i:i + 60]):
            i += flen
            continue
        frames += 1
        samples += spf
        rate = sr
        i += flen
    if not rate:
        raise ValueError(f"mp3 프레임이 없다: {path.name}")
    return round(samples * 1000 / rate)


# --- 회차 하나 ------------------------------------------------------------------------------
class LessonError(Exception):
    pass


def db_code(source_code):
    """자료의 블랙 L5-…는 DB 코드 K-…(ADR-27)"""
    return "K-" + source_code[3:] if source_code.startswith("L5-") else source_code


def check_len(errors, what, value, where):
    if value is not None and len(value) > LIMITS[what]:
        errors.append(f"{where}: {what} {len(value)}자 > {LIMITS[what]}")


def one_file(folder, pattern, where):
    found = sorted(folder.glob(pattern))
    if len(found) != 1:
        raise LessonError(f"{where}: '{pattern}' 파일이 {len(found)}개")
    return found[0]


def read_script(lesson_dir):
    """(원고, DB 코드)"""
    script = json.loads((lesson_dir / "자료" / "음원 원고.json").read_text(encoding="utf-8"))
    return script, db_code(script["회차ID"])


def build_lesson(lesson_dir, script, code, book_dir, out, gaps, link, word_tests=None):
    m = LESSON_DIR.match(lesson_dir.name)
    if not m:
        raise LessonError(f"회차 폴더 이름 규칙이 아니다: {lesson_dir.name}")
    errors = []

    passage_dirs = {}
    for d in lesson_dir.iterdir():
        pm = re.match(r"^지문(\d+) · ", d.name)
        if d.is_dir() and pm:
            passage_dirs[int(pm.group(1))] = d
        elif d.is_dir() and d.name == CORE_PASSAGE_DIR:
            passage_dirs[1] = d  # 지문이 하나뿐인 레벨(네이비)은 번호 없는 폴더 하나
    passages = []
    files = []  # (원본, 키)
    for p in sorted(script["지문"], key=lambda x: int(x["지문ID"].rsplit("-P", 1)[1])):
        no = int(p["지문ID"].rsplit("-P", 1)[1])
        where = f"{code} 지문 {no}"
        pdir = passage_dirs.get(no)
        if pdir is None:
            raise LessonError(f"{where}: 지문 폴더 없음")
        check_len(errors, "passage title", p["제목"], where)
        sentences = []
        for s in sorted(p["문장"], key=lambda x: x["번호"]):
            speaker = None if s["화자"] in ("", NARRATOR) else s["화자"]
            check_len(errors, "sentence", s["영어"], f"{where} 문장 {s['번호']}")
            check_len(errors, "speaker", speaker, f"{where} 문장 {s['번호']}")
            sentences.append({"no": s["번호"], "text": s["영어"], "speaker": speaker})
        if [s["no"] for s in sentences] != list(range(1, len(sentences) + 1)):
            errors.append(f"{where}: 문장 번호가 1부터 이어지지 않는다")
        tracks = []
        for voice, name in VOICES.items():
            full = one_file(pdir / "1_지문 전체", f"* 전체 · 영어 · {name}.mp3", where)
            sentence_files = sorted((pdir / "2_문장별").glob(f"*번 문장*· 영어 · {name}.mp3"),
                                    key=lambda f: int(f.name.split("번", 1)[0]))
            # 개수만 보면 빠진 번호와 겹친 번호("3번 문장 수정")가 서로 가려진다 → 번호가 정확히 1..n인지
            numbers = [int(f.name.split("번", 1)[0]) for f in sentence_files]
            if numbers != list(range(1, len(sentences) + 1)):
                errors.append(f"{where} {voice}: 문장 파일 번호 {numbers} ≠ 1~{len(sentences)}")
                continue
            duration = mp3_ms(full)
            sentence_ms = [mp3_ms(f) for f in sentence_files]
            n = len(sentence_ms)
            if n == 0:
                errors.append(f"{where} {voice}: 문장이 없다")
                continue
            if n == 1:
                # import(PassageTimings)는 문장이 하나면 문장 길이 = 지문 길이를 요구한다
                if duration != sentence_ms[0]:
                    errors.append(f"{where} {voice}: 문장 하나인데 길이가 다르다({sentence_ms[0]} ≠ {duration}ms)")
            else:
                gap = (duration - sum(sentence_ms)) / (n - 1)
                gaps.append((gap, f"{where} {voice}"))
                if not GAP_MIN_MS <= gap <= GAP_MAX_MS:
                    errors.append(f"{where} {voice}: 문장 사이 간격 {gap:.0f}ms가 범위 밖")
            key = f"audio/{code}/{code}_p{no}_{voice}.mp3"
            files.append((full, key))
            tracks.append({"voice": voice, "fileKey": key, "durationMs": duration, "sentenceMs": sentence_ms})
        passages.append({
            "no": no,
            "title": p["제목"],
            "wordCount": sum(len(s["text"].split()) for s in sentences),
            "sentences": sentences,
            "tracks": tracks,
        })
    if [p["no"] for p in passages] != list(range(1, len(passages) + 1)):
        errors.append(f"{code}: 지문 번호가 1부터 이어지지 않는다")

    words = merge_words(read_book_words(book_dir, code, errors), build_words(lesson_dir, script, code, errors),
                        code, errors, files)
    tests = None if word_tests is None else build_word_tests(word_tests, code, errors)
    if errors:
        raise LessonError("\n  ".join(errors))

    # 음원을 다 둔 뒤 manifest를 쓴다: 중간에 실패하면 manifest가 없어 import되지 않는다
    size = 0
    for src, key in files:
        dst = out / key
        dst.parent.mkdir(parents=True, exist_ok=True)
        if dst.exists():
            dst.unlink()
        link(src, dst)
        size += src.stat().st_size
    # 회차 제목은 넣지 않는다: 폴더 이름의 제목은 문법 주제이고, 학생 화면 제목은 기준 데이터의 Reading Day n이다
    manifest = {"code": code, "passages": passages, "words": words}
    if tests is not None:
        manifest["wordTests"] = tests
    target = manifest_path(out, code)
    tmp = target.with_suffix(".json.tmp")
    tmp.write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")
    os.replace(tmp, target)
    return len(passages), len(files), size


def manifest_path(out, code):
    return out / "manifest" / f"{code}.json"


def build_words(lesson_dir, script, code, errors):
    """회차 어휘(옛 자료 폴더) → 단어 목록. 음원은 파일 경로만(키·번호는 merge_words가 매긴다)"""
    translations = {}
    for line in (lesson_dir / "단어" / "단어 목록.txt").read_text(encoding="utf-8-sig").splitlines():
        if re.match(r"^\d+ \| ", line):
            cols = line.split(" | ")
            translations[int(cols[0])] = cols[5].strip()
    word_dir = lesson_dir / "단어"
    name = VOICES[WORD_VOICE]
    words = []
    for index, w in enumerate(script["어휘"], start=1):
        no = int(w["어휘ID"].rsplit("-V", 1)[1])
        where = f"{code} 회차 어휘 {no}"
        if no != index:
            errors.append(f"{where}: 단어 번호가 1부터 이어지지 않는다")
        pos = POS.get(w["품사"])
        if pos is None:
            errors.append(f"{where}: 품사 '{w['품사']}'를 모른다")
        example_ko = translations.get(no)
        if not example_ko:
            errors.append(f"{where}: 단어 목록.txt에 예문 해석이 없다")
        for what, value in (("english", w["단어·표현"]), ("korean", w["뜻"]), ("example", w["교재 예문"]),
                            ("example ko", example_ko)):
            check_len(errors, what, value, where)
        try:
            word_file = one_file(word_dir, f"{no:02d} * · 단어 · 영어 · {name}.mp3", where)
            example_file = one_file(word_dir, f"{no:02d} * · 예문 · 영어 · {name}.mp3", where)
        except LessonError as e:
            errors.append(str(e))
            continue
        words.append({"english": w["단어·표현"], "pos": pos, "korean": w["뜻"], "exampleEn": w["교재 예문"],
                      "exampleKo": example_ko, "wordFile": word_file, "exampleFile": example_file})
    return words


BOOK_ITEM = re.compile(r"^(?:(\d{2})|(\d{2})-\d+|숙어 (\d{2}))$")
BOOK_HEADER = ["책 번호", "영어", "품사", "뜻", "책 쪽", "예문(책)", "관련어"]


def read_book_words(word_dir, code, errors):
    """새 어휘책 판 `단어 목록.txt`(책 번호 | 영어 | 품사 | 뜻 | 책 쪽 | 예문(책) | 관련어) → 책 단어 목록.
    순서: 단어(NN) → 숙어(숙어 NN). 하위 표현(NN-k)은 예문이 없어 넣지 않는다(이슈 #88).
    음원은 단어 음원 하나(미국 여자), 예문 음원·예문 해석 없음.
    |가 없는 줄(제목·빈 줄)만 건너뛴다. 머리 줄이 다르거나 없고, 번호 형식을 모르는 줄이 있거나, 책 단어가 0개면 오류"""
    if not (word_dir / "단어 목록.txt").is_file():
        raise LessonError(f"{code}: 새 어휘책 단어 목록이 없다: {word_dir / '단어 목록.txt'}")
    names = [f.name for f in word_dir.iterdir()]
    items = {"단어": [], "숙어": []}
    header = False
    for line in (word_dir / "단어 목록.txt").read_text(encoding="utf-8-sig").splitlines():
        if "|" not in line:
            continue  # 제목·빈 줄
        cols = [c.strip() for c in line.split("|")]
        if cols[0] == BOOK_HEADER[0]:
            if cols != BOOK_HEADER:
                errors.append(f"{code} 새 어휘책: 머리 줄이 {BOOK_HEADER}가 아니다: {cols}")
            header = True
            continue
        m = BOOK_ITEM.match(cols[0])
        if not m:
            errors.append(f"{code} 새 어휘책: 번호 칸 '{cols[0]}'를 모른다(NN, NN-k, 숙어 NN)")
            continue
        if m.group(2):
            continue  # 하위 표현
        if len(cols) != 7:  # 칸 안에 |가 있으면 칸이 밀린다 — 건너뛰지 않고 알린다
            errors.append(f"{code} 새 어휘책 {cols[0]}: 칸이 {len(cols)}개다(7개여야 한다)")
            continue
        kind = "단어" if m.group(1) else "숙어"
        items[kind].append((cols[0], int(m.group(1) or m.group(3)), cols))
    if not header:
        errors.append(f"{code} 새 어휘책: 머리 줄({' | '.join(BOOK_HEADER)})이 없다")
    if not items["단어"] and not items["숙어"]:
        errors.append(f"{code} 새 어휘책: 책 단어가 0개다")
    words = []
    for kind in ("단어", "숙어"):
        if [no for _, no, _ in items[kind]] != list(range(1, len(items[kind]) + 1)):
            errors.append(f"{code} 새 어휘책 {kind}: 번호가 1부터 이어지지 않는다")
        for label, _, (_, english, pos_text, korean, _, example, _) in items[kind]:
            where = f"{code} 새 어휘책 {label}" if kind == "숙어" else f"{code} 새 어휘책 단어 {label}"
            if kind == "숙어":
                pos = "phr"
            else:
                pos = BOOK_POS.get(pos_text.split("·")[0].strip())
                if pos is None:
                    errors.append(f"{where}: 품사 '{pos_text}'를 모른다")
            for what, value in (("english", english), ("korean", korean), ("example", example)):
                if not value:
                    errors.append(f"{where}: {what}이(가) 비었다")
                check_len(errors, what, value, where)
            found = [n for n in names if n.startswith(f"{label} ") and n.endswith(BOOK_WORD_AUDIO)]
            if len(found) != 1:
                errors.append(f"{where}: 음원 '{label} *{BOOK_WORD_AUDIO}' 파일이 {len(found)}개")
                continue
            words.append({"english": english, "source": "book", "pos": pos, "korean": korean, "exampleEn": example,
                          "exampleKo": None, "wordFile": word_dir / found[0], "exampleFile": None})
    return words


WORD_TEST_COLUMNS = ["lesson_code", "section", "no", "book_page", "prompt", "answer", "hint"]


def read_word_tests(paths):
    """단어시험 문항 TSV 여러 개 → {DB 코드: [행]}. 칸이 밀리거나 모자란 행은 파일·행 번호와 함께 ValueError.

    따옴표 규칙을 끈다(QUOTE_NONE): 기본값이면 "로 시작하는 칸의 따옴표가 사라지고, 닫히지 않은 따옴표는 여러 행을 한 칸으로 합친다.
    같은 (회차, 영역, 번호)가 두 파일에 있으면 build_word_tests의 번호 검사가 겹침으로 잡는다.
    """
    tests = {}
    for path in paths:
        with open(path, encoding="utf-8-sig", newline="") as f:
            reader = csv.reader(f, delimiter="\t", quoting=csv.QUOTE_NONE)
            header = next(reader, None)
            if header != WORD_TEST_COLUMNS:
                raise ValueError(f"{path}: 머리 행이 {WORD_TEST_COLUMNS}가 아니다: {header}")
            for line, cells in enumerate(reader, start=2):
                if not cells:
                    continue
                if len(cells) != len(WORD_TEST_COLUMNS):
                    raise ValueError(f"{path} {line}행: 칸이 {len(cells)}개다({len(WORD_TEST_COLUMNS)}개여야 한다)")
                r = dict(zip(WORD_TEST_COLUMNS, cells))
                tests.setdefault(r["lesson_code"].strip(), []).append(r)
    return tests


def build_word_tests(rows, code, errors):
    """TSV 행 → manifest wordTests(영역 순 A~D, 번호 순). 영역마다 번호가 1부터 이어지는지 본다"""
    items = []
    for r in rows:
        where = f"{code} 단어시험 {r['section']} {r['no']}"
        section = r["section"].strip()
        if section not in TEST_SECTIONS:
            errors.append(f"{where}: 모르는 영역")
            continue
        try:
            no = int(r["no"])
        except ValueError:
            errors.append(f"{where}: 번호가 숫자가 아니다")
            continue
        item = {"section": section, "no": no, "prompt": r["prompt"].strip(), "answer": r["answer"].strip(),
                "hint": r["hint"].strip() or None, "accepted": []}
        for what, limit in TEST_LIMITS.items():
            value = item[what]
            if what != "hint" and not value:
                errors.append(f"{where}: {what}이(가) 비었다")
            elif value is not None and len(value) > limit:
                errors.append(f"{where}: {what} {len(value)}자 > {limit}")
        items.append(item)
    items.sort(key=lambda i: (i["section"], i["no"]))
    for section in TEST_SECTIONS:
        nos = [i["no"] for i in items if i["section"] == section]
        if nos and nos != list(range(1, len(nos) + 1)):
            missing = sorted(set(range(1, max(nos) + 1)) - set(nos))
            twice = sorted({n for n in nos if nos.count(n) > 1})
            errors.append(f"{code} 단어시험 {section}: 번호가 1부터 이어지지 않는다(빠짐 {missing}, 겹침 {twice})")
    return items


def norm_term(s):
    return " ".join(s.lower().replace("~", "").split())


def merge_words(book_words, lesson_words, code, errors, files):
    """단어 = 새 어휘책 단어 → 숙어 + 새 판에 없는 회차 어휘(ADR 0027 결정 6, 이슈 #88).
    두 쪽에 다 있으면 새 판 내용만 쓴다(화면 = 책). no는 1부터, 음원 키 번호(w{nn})도 no와 같다
    (책 단어와 회차 어휘가 모두 음원을 가져 회차 어휘 번호로는 겹친다). files에 (원본, 키)를 더한다.
    source(ADR 0029): 새 어휘책 단어·숙어 book, 새 판에 없는 회차 어휘 extra"""
    in_book = {norm_term(w["english"]) for w in book_words}
    merged = list(book_words) + [{**w, "source": "extra"} for w in lesson_words
                                 if norm_term(w["english"]) not in in_book]
    if len(merged) > 127:
        errors.append(f"{code}: 단어 {len(merged)}개 > 127(word.sort_order TINYINT)")
    words = []
    for no, w in enumerate(merged, start=1):
        track = example_track = None
        if w["wordFile"] is not None:
            key = f"audio/{code}/{code}_w{no:02d}_{WORD_VOICE}.mp3"
            files.append((w["wordFile"], key))
            track = {"fileKey": key, "durationMs": mp3_ms(w["wordFile"])}
        if w["exampleFile"] is not None:
            key = f"audio/{code}/{code}_w{no:02d}_ex_{WORD_VOICE}.mp3"
            files.append((w["exampleFile"], key))
            example_track = {"fileKey": key, "durationMs": mp3_ms(w["exampleFile"])}
        # 칸 순서: no, english, source, 나머지(manifest를 눈으로 볼 때 출처가 단어 옆에)
        words.append({"no": no, "english": w["english"], "source": w["source"], "pos": w["pos"],
                      "korean": w["korean"], "exampleEn": w["exampleEn"], "exampleKo": w["exampleKo"],
                      "track": track, "exampleTrack": example_track})
    return words


def link_or_copy(src, dst):
    try:
        os.link(src, dst)
    except OSError:
        shutil.copy2(src, dst)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("source", type=Path)
    parser.add_argument("out", type=Path)
    parser.add_argument("--book-words", type=Path, required=True,
                        help="새 어휘책 판 단어 음원 결과물 폴더({레벨}/{회차}/단어/단어 목록.txt, 이슈 #88)")
    parser.add_argument("--word-test", type=Path, action="append", default=[],
                        help="단어시험 문항 TSV(여러 번 줄 수 있음). 행이 있는 회차만 wordTests를 넣고, 그 회차의 전체 목록이 된다")
    parser.add_argument("--only", default="", help="회차 코드(DB 코드) 쉼표 목록")
    args = parser.parse_args()
    only = {c.strip() for c in args.only.split(",") if c.strip()}
    (args.out / "manifest").mkdir(parents=True, exist_ok=True)

    # 레벨 폴더 아래 폴더는 모두 회차 폴더여야 한다. 이름 규칙이 다르면 조용히 건너뛰지 않고 실패로 보고한다
    lessons = sorted(d for level in args.source.iterdir() if level.is_dir() and level.name[:1].isdigit()
                     for d in level.iterdir() if d.is_dir())
    try:
        word_tests = read_word_tests(args.word_test)
    except ValueError as e:  # 파일이 깨졌으면 회차별로 모을 이유가 없다
        print(f"단어시험 TSV 오류: {e}", file=sys.stderr)
        sys.exit(1)
    gaps, failed, seen = [], [], set()
    done = passages = files = size = 0
    for lesson_dir in lessons:
        label = f"{lesson_dir.parent.name}/{lesson_dir.name}"
        code = None
        try:
            if not LESSON_DIR.match(lesson_dir.name):
                raise LessonError(f"회차 폴더 이름 규칙이 아니다: {lesson_dir.name}")
            script, code = read_script(lesson_dir)
            if only and code not in only:
                continue
            seen.add(code)
            book_dir = args.book_words / lesson_dir.parent.name / lesson_dir.name / "단어"
            p, f, s = build_lesson(lesson_dir, script, code, book_dir, args.out, gaps, link_or_copy,
                                   word_tests.get(code))
            done, passages, files, size = done + 1, passages + p, files + f, size + s
        except Exception as e:  # 회차 하나의 오류가 다른 회차를 막지 않는다
            if only and code is None:
                label += " (--only 판별 전)"
            failed.append(f"{label}\n  {type(e).__name__}: {e}")
            if code:
                # 지난 실행의 manifest가 남아 있으면 옛 내용이 import된다
                manifest_path(args.out, code).unlink(missing_ok=True)
    for code in sorted(only - seen):
        failed.append(f"--only {code}: 그런 회차가 없다")
    for code in sorted(c for c in word_tests if c not in seen and (not only or c in only)):
        failed.append(f"단어시험 TSV {code}: 자료에 그런 회차가 없다")
    if done == 0:
        failed.append("만든 회차가 0개다(자료 폴더·--only 확인)")
    # 단어시험 영역 구성: manifest의 wordTests는 회차 전체 목록이라, 빠진 영역이 있으면 import가 그 영역 문항을 지운다
    for code in sorted(c for c in word_tests if c in seen):
        if manifest_path(args.out, code).exists():
            counts = {}
            for r in word_tests[code]:
                counts[r["section"].strip()] = counts.get(r["section"].strip(), 0) + 1
            print(f"단어시험 {code}: " + " ".join(f"{s}{counts[s]}" for s in sorted(counts))
                  + " (이 영역 밖의 문항은 import 때 지워진다)")

    print(f"회차 {done}개 · 지문 {passages}개 · 음원 {files}개 · {size / 1024 / 1024:.1f}MB")
    values = sorted(g for g, _ in gaps)  # 문장이 2개 이상인 지문 × 목소리만(간격 0 포함)
    if values:
        median = statistics.median(values)
        print(f"문장 사이 간격(지문 × 목소리 {len(values)}개): 최소 {values[0]:.0f} · 중앙 "
              f"{median:.0f} · 최대 {values[-1]:.0f}ms")
        far = sorted(((g, w) for g, w in gaps if abs(g - median) > 100), key=lambda x: -abs(x[0] - median))
        print(f"중앙값에서 100ms 넘게 벗어난 것: {len(far)}개(먼 것부터)")
        for g, w in far[:20]:
            print(f"  {g:.0f}ms {w}")
        if len(far) > 20:
            print(f"  … {len(far) - 20}개 더")
    if failed:
        print(f"\n실패 {len(failed)}개", file=sys.stderr)
        for f in failed:
            print(f, file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
