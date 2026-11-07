#!/usr/bin/env bash
# 운영 시험용 음원(사인파 mp3). 교재 음원이 아니다 — 서명 없는 음원 버킷에 교재 음원을 올리지 않는다.
# 개발 시드(ContentDevSeed)의 지문 트랙과 같은 경로·길이로 만든다:
#   <출력 폴더>/audio/<회차>/<회차>_p<1~6>_<속도>.mp3
#   길이: 1.0 = 10000ms(문장 3000ms × 3 + 간격 500ms × 2), 다른 속도 = round(10000 / 속도)
# 지문마다 음 높이가 달라 이어 들을 때 넘어간 것을 귀로 안다.
#
# 사용법: make-trial-audio.sh <출력 폴더> <회차 코드>...
#   SPEEDS="0.8 1.0 1.2"(기본, L2·L3 레벨의 passage_speeds)
#   ffmpeg가 없으면 도커로: docker run --rm -v "$PWD:/w" -w /w --entrypoint bash linuxserver/ffmpeg infra/audio/make-trial-audio.sh out L2-B1-05
# 올리기: aws s3 sync <출력 폴더>/audio s3://<음원 버킷>/audio (만든 파일은 저장소에 넣지 않는다)
set -Eeuo pipefail

readonly PASSAGES=6
readonly ONE_SPEED_MS=10000
readonly SPEEDS="${SPEEDS:-0.8 1.0 1.2}"

usage() {
	echo "사용법: $0 <출력 폴더> <회차 코드>... (예: $0 out L2-B1-05)" >&2
	exit 2
}

(($# >= 2)) || usage
out="$1"
shift

# 입력을 모두 검사한 뒤에 만든다(반쯤 만들고 멈추지 않게)
for code in "$@"; do
	[[ $code =~ ^L[0-9]+-B[0-9]+-[0-9]{2}$ ]] || {
		echo "회차 코드 형식이 아니다(예: L2-B1-05): $code" >&2
		exit 2
	}
done
read -r -a speeds <<<"$SPEEDS"
for speed in "${speeds[@]}"; do
	[[ $speed =~ ^[0-9]\.[0-9]$ ]] || {
		echo "속도 형식이 아니다(예: 1.0): $speed" >&2
		exit 2
	}
done

count=0
for code in "$@"; do
	mkdir -p "$out/audio/$code"
	for ((p = 1; p <= PASSAGES; p++)); do
		frequency=$((220 + 110 * (p - 1)))
		for speed in "${speeds[@]}"; do
			seconds="$(awk -v ms="$ONE_SPEED_MS" -v s="$speed" 'BEGIN { printf "%.3f", int(ms / s + 0.5) / 1000 }')"
			ffmpeg -nostdin -hide_banner -loglevel error -y \
				-f lavfi -i "sine=frequency=${frequency}:sample_rate=44100:duration=${seconds}" \
				-ac 1 -b:a 64k "$out/audio/$code/${code}_p${p}_${speed}.mp3"
			count=$((count + 1))
		done
	done
done
echo "만든 파일: $count개 → $out/audio"
