#!/usr/bin/env bats
# make-trial-audio.sh의 경로·길이·입력 검사. ffmpeg는 가짜(받은 인자를 적고 출력 파일만 만든다).
# 실행: docker run --rm -v "$PWD:/repo" -w /repo bats/bats:1.11.0 infra/audio/test

bats_require_minimum_version 1.5.0

setup() {
	FAKE="$BATS_TEST_TMPDIR/fake"
	OUT="$BATS_TEST_TMPDIR/out"
	mkdir -p "$FAKE/bin"
	cat >"$FAKE/bin/ffmpeg" <<'EOF'
#!/usr/bin/env bash
echo "$*" >>"$FAKE/calls"
touch "${*: -1}"
EOF
	chmod +x "$FAKE/bin/ffmpeg"
	export FAKE PATH="$FAKE/bin:$PATH"
	SCRIPT="$BATS_TEST_DIRNAME/../make-trial-audio.sh"
}

@test "회차마다 지문 6개 × 속도 3개를 개발 시드의 file_key 경로로 만든다" {
	run bash "$SCRIPT" "$OUT" L2-B1-05 L2-B1-06
	[ "$status" -eq 0 ]
	[ "$(find "$OUT" -name '*.mp3' | wc -l)" -eq 36 ]
	[ -f "$OUT/audio/L2-B1-05/L2-B1-05_p1_0.8.mp3" ]
	[ -f "$OUT/audio/L2-B1-05/L2-B1-05_p6_1.2.mp3" ]
	[ -f "$OUT/audio/L2-B1-06/L2-B1-06_p3_1.0.mp3" ]
}

# 개발 시드(ContentDevSeed)의 duration_ms와 같다: 1.0 = 10000ms, 다른 속도 = round(10000 / 속도)
@test "길이는 시드의 duration_ms와 같다" {
	run bash "$SCRIPT" "$OUT" L2-B1-05
	[ "$status" -eq 0 ]
	grep -q "duration=12.500.*L2-B1-05_p1_0.8.mp3$" "$FAKE/calls"
	grep -q "duration=10.000.*L2-B1-05_p1_1.0.mp3$" "$FAKE/calls"
	grep -q "duration=8.333.*L2-B1-05_p1_1.2.mp3$" "$FAKE/calls"
}

@test "지문마다 음 높이가 달라 이어 들을 때 넘어간 것을 안다" {
	run bash "$SCRIPT" "$OUT" L2-B1-05
	[ "$status" -eq 0 ]
	[ "$(grep -c 'L2-B1-05_p1_1.0.mp3$' "$FAKE/calls")" -eq 1 ]
	p1="$(grep 'p1_1.0.mp3$' "$FAKE/calls" | grep -o 'frequency=[0-9]*')"
	p2="$(grep 'p2_1.0.mp3$' "$FAKE/calls" | grep -o 'frequency=[0-9]*')"
	[ "$p1" != "$p2" ]
}

@test "속도는 SPEEDS로 바꾼다(L1은 0.8 1.0)" {
	SPEEDS="0.8 1.0" run bash "$SCRIPT" "$OUT" L1-B1-01
	[ "$status" -eq 0 ]
	[ "$(find "$OUT" -name '*.mp3' | wc -l)" -eq 12 ]
}

@test "회차 코드 형식이 틀리면 아무것도 만들지 않는다" {
	run bash "$SCRIPT" "$OUT" L2-B1-05 "../etc"
	[ "$status" -ne 0 ]
	[[ "$output" == *"회차 코드"* ]]
	[ ! -f "$FAKE/calls" ]
}

@test "속도 형식이 틀리면 아무것도 만들지 않는다" {
	SPEEDS="1.0 fast" run bash "$SCRIPT" "$OUT" L2-B1-05
	[ "$status" -ne 0 ]
	[ ! -f "$FAKE/calls" ]
}

@test "인자가 모자라면 사용법을 알린다" {
	run bash "$SCRIPT" "$OUT"
	[ "$status" -ne 0 ]
	[[ "$output" == *"사용법"* ]]
}
