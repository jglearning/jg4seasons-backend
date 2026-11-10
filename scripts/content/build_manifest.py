# -*- coding: utf-8 -*-
"""녹음된 교재 자료 폴더에서 회차별 manifest와 업로드용 음원 폴더를 만든다(ADR-27).

사용법:
    python -I -X utf8 scripts/content/build_manifest.py <자료 폴더> <출력 폴더> [--only 코드,코드]

- 자료 폴더: 레벨 폴더(1_…~5_…) 아래 회차 폴더(`NN회 · 제목`, `보충N회 · 제목`)가 있는 결과물 폴더
- 출력 폴더: 저장소 밖에 둔다. 교재 원문(manifest)과 음원은 저장소에 넣지 않는다
  - manifest/{회차 코드}.json  앱의 import 실행기가 읽는다
  - audio/{회차 코드}/…        S3 키 이름 그대로(하드 링크, 안 되면 복사)
- 표준 라이브러리만 쓴다. mp3 길이는 프레임을 세서 구한다
- 오류는 회차별로 모아 마지막에 보이고 종료 코드 1
"""
import argparse
import json
import os
import re
import shutil
import statistics
import sys
from pathlib import Path

VOICES = {"us_w": "미국 여자", "us_m": "미국 남자", "uk_w": "영국 여자", "uk_m": "영국 남자"}
WORD_VOICE = "us_w"  # 단어·예문 음원은 미국 여자 하나(1차)
POS = {"n.": "n", "v.": "v", "adj.": "adj", "adv.": "adv", "prep.": "prep", "conj.": "conj", "구": "phr"}
NARRATOR = "내레이터"
LIMITS = {"lesson title": 100, "passage title": 120, "sentence": 800, "english": 50, "korean": 100,
          "example": 300, "example ko": 300, "speaker": 20}
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


def build_lesson(lesson_dir, script, code, out, gaps, link):
    m = LESSON_DIR.match(lesson_dir.name)
    if not m:
        raise LessonError(f"회차 폴더 이름 규칙이 아니다: {lesson_dir.name}")
    errors = []
    check_len(errors, "lesson title", m.group(1), code)

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

    words = build_words(lesson_dir, script, code, errors, files)
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
    manifest = {"code": code, "title": m.group(1), "passages": passages, "words": words}
    target = manifest_path(out, code)
    tmp = target.with_suffix(".json.tmp")
    tmp.write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")
    os.replace(tmp, target)
    return len(passages), len(files), size


def manifest_path(out, code):
    return out / "manifest" / f"{code}.json"


def build_words(lesson_dir, script, code, errors, files):
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
        where = f"{code} 단어 {no}"
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
        word_key = f"audio/{code}/{code}_w{no:02d}_{WORD_VOICE}.mp3"
        example_key = f"audio/{code}/{code}_w{no:02d}_ex_{WORD_VOICE}.mp3"
        files += [(word_file, word_key), (example_file, example_key)]
        words.append({
            "no": no, "english": w["단어·표현"], "pos": pos, "korean": w["뜻"],
            "exampleEn": w["교재 예문"], "exampleKo": example_ko,
            "track": {"fileKey": word_key, "durationMs": mp3_ms(word_file)},
            "exampleTrack": {"fileKey": example_key, "durationMs": mp3_ms(example_file)},
        })
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
    parser.add_argument("--only", default="", help="회차 코드(DB 코드) 쉼표 목록")
    args = parser.parse_args()
    only = {c.strip() for c in args.only.split(",") if c.strip()}
    (args.out / "manifest").mkdir(parents=True, exist_ok=True)

    # 레벨 폴더 아래 폴더는 모두 회차 폴더여야 한다. 이름 규칙이 다르면 조용히 건너뛰지 않고 실패로 보고한다
    lessons = sorted(d for level in args.source.iterdir() if level.is_dir() and level.name[:1].isdigit()
                     for d in level.iterdir() if d.is_dir())
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
            p, f, s = build_lesson(lesson_dir, script, code, args.out, gaps, link_or_copy)
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
    if done == 0:
        failed.append("만든 회차가 0개다(자료 폴더·--only 확인)")

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
