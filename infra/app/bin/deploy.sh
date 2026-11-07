#!/usr/bin/env bash
# 앱을 blue/green으로 바꿔 끼운다. SSM 문서 jg4seasons-prod-deploy가 root로 부른다.
#   deploy.sh <이미지 태그 = 커밋 SHA 40자>
# 순서: 비밀값으로 env 파일 → 쉬는 슬롯 기동 → readiness 대기 → Nginx 전환 → 확인 → 이전 슬롯 종료.
# 지금 슬롯은 Nginx가 가리키는 곳(active.conf)이다. state/active-slot은 사람이 보라고 같이 적는다.
# 마지막 줄(종료 0은 deployed뿐):
#   "deployed <슬롯> <태그>"  새 슬롯으로 옮겼다
#   "kept <슬롯>: <이유>"     예상한 실패. 옮기지 않았거나 되돌렸고, 그 슬롯(첫 배포면 none)이 요청을 받는다
#   "failed <슬롯>: <이유>"   되돌리지 못했거나 예상하지 못한 오류. 지금 요청을 받는 슬롯을 적는다 — 서버를 직접 본다
# 비밀값을 출력하지 않는다(set -x 금지, env 파일 내용을 찍지 않는다). 새 슬롯의 앱 로그는 출력에 싣지 않고
# state/failed-app-<슬롯>.log에 남긴다(SSM 출력은 공개 Actions 로그에 찍히고, 앱 로그에는 접속 주소가 있다).
# 실패 시험: READY_TIMEOUT_SECONDS=1 deploy.sh <태그> → readiness를 못 기다려 kept로 끝난다.
# 시험(infra/app/test)은 JG4S_BASE·JG4S_LOCK_FILE로 경로를 바꾼다. 운영의 SSM 문서는 넘기지 않는다.
set -Eeuo pipefail

readonly BASE="${JG4S_BASE:-/opt/jg4seasons}"
readonly RUNTIME="$BASE/runtime"
readonly SLOTS_ENV="$RUNTIME/slots.env"
readonly APP_ENV="$RUNTIME/app.env"
readonly STATE="$BASE/state/active-slot"
readonly UPSTREAM="$BASE/nginx/upstream/active.conf"
readonly PARAM_PATH=/jg4seasons/prod/
readonly READY_TIMEOUT_SECONDS="${READY_TIMEOUT_SECONDS:-180}"
# 운영에 꼭 있어야 하는 값(없으면 앱이 뜨지 않는다 — 배포 전에 알린다).
# INITIAL_*(첫 학원·관리자)는 학원이 생긴 뒤에는 없어도 된다.
readonly REQUIRED_PARAMS=(DB_URL DB_USERNAME DB_PASSWORD JG4SEASONS_PERSONAL_CODE_PEPPER JG4SEASONS_JWT_SECRET)
# 운영 프로파일(prod)은 compose.yaml이 고정한다. 파라미터로 바꿔치기하지 못하게 거부한다.
# 시험 데이터(trial)는 SPRING_PROFILES_INCLUDE=trial 파라미터로만 켠다(다른 값은 거부 — write_app_env).
readonly FORBIDDEN_PARAMS=(SPRING_PROFILES_ACTIVE)
export AWS_REGION="${AWS_REGION:-ap-northeast-2}"

current=""
target=""
previous_tag=""
previous_upstream=""
switched=""

compose() {
	docker compose -p jg4seasons -f "$BASE/compose.yaml" --env-file "$SLOTS_ENV" "$@"
}

kept() {
	echo "kept ${current:-none}: $*"
	exit 1
}

failed() {
	echo "failed $1: $2"
	exit 1
}

# 지금 요청을 받는 슬롯: 옮긴 뒤면 새 슬롯, 아니면 이전 슬롯.
serving_slot() {
	if [[ -n $switched ]]; then echo "$target"; else echo "${current:-none}"; fi
}

# 예상하지 못한 실패(set -e로 멈추는 명령)도 마지막 줄 규약을 지킨다.
trap 'failed "$(serving_slot)" "예상하지 못한 오류(줄 $LINENO)"' ERR

port_of() {
	if [[ $1 == blue ]]; then echo 8081; else echo 8082; fi
}

tag_var_of() {
	if [[ $1 == blue ]]; then echo BLUE_TAG; else echo GREEN_TAG; fi
}

set_slot_tag() {
	local var
	var="$(tag_var_of "$1")"
	if grep -q "^${var}=" "$SLOTS_ENV"; then
		sed -i "s/^${var}=.*/${var}=$2/" "$SLOTS_ENV"
	else
		echo "${var}=$2" >>"$SLOTS_ENV"
	fi
}

# 새 슬롯을 세우다 실패했을 때: 로그를 서버 파일에 남기고, 새 슬롯을 내리고, 태그를 되돌린다.
abandon_target() {
	local log="$BASE/state/failed-app-$target.log"
	if { : >"$log" && chmod 600 "$log" && compose logs --no-color --tail 200 "app-$target" >>"$log" 2>&1; } 2>/dev/null; then
		echo "app-$target의 로그: $log"
	else
		echo "app-$target의 로그를 남기지 못했다($log). 서버에서 docker logs로 본다"
	fi
	# 멈추지 못하면 restart: unless-stopped로 다시 뜨며 메모리를 차지한다 — 사람이 봐야 한다.
	compose stop -t 30 "app-$target" || echo "app-$target를 멈추지 못했다(서버에서 직접 멈춘다)"
	set_slot_tag "$target" "$previous_tag"
	kept "$1"
}

nginx_running() {
	[[ -n "$(compose ps -q --status running nginx)" ]]
}

apply_upstream() {
	if nginx_running; then
		compose exec -T nginx nginx -t -q && compose exec -T nginx nginx -s reload
	else
		compose up -d nginx
	fi
}

# 첫 배포면 되돌릴 곳이 없다. 파일은 그대로 둔다(지우면 다음 Nginx 시작이 include에서 실패한다).
restore_upstream() {
	[[ -n $previous_upstream ]] || return 0
	printf '%s\n' "$previous_upstream" >"$UPSTREAM"
	apply_upstream
}

write_app_env() {
	local raw tmp name value missing=()
	# --recursive: 하위 경로에 잘못 넣은 값도 받아 아래 이름 검사에서 멈춘다(빼면 조용히 빠진다).
	raw="$(aws ssm get-parameters-by-path --path "$PARAM_PATH" --recursive --with-decryption \
		--query 'Parameters[].[Name,Value]' --output text)" || kept "Parameter Store를 읽지 못했다"
	tmp="$(mktemp "$RUNTIME/app.env.XXXXXX")"
	while IFS=$'\t' read -r name value; do
		[[ -z $name ]] && continue
		# 값에 줄바꿈이 있으면 다음 줄이 경로 없는 "이름"으로 읽힌다. 그 줄은 비밀값의 일부라 출력하지 않는다.
		if [[ $name != "$PARAM_PATH"* ]]; then
			rm -f "$tmp"
			kept "파라미터 값에 줄바꿈이 있다(어느 값인지·내용은 출력하지 않는다 — Parameter Store에서 값 끝의 줄바꿈을 본다)"
		fi
		# 경로 안의 이름은 비밀값이 아니라 출력해도 된다(하위 경로·소문자 등).
		if [[ ! ${name#"$PARAM_PATH"} =~ ^[A-Z][A-Z0-9_]*$ ]]; then
			rm -f "$tmp"
			kept "파라미터 이름 형식이 틀렸다(하위 경로는 쓰지 않는다): ${name:0:80}"
		fi
		name="${name#"$PARAM_PATH"}"
		# env 파일은 공백·따옴표·$·#·백틱·역슬래시를 다르게 읽는다. 그런 값은 조용히 바뀌지 않게 거부한다.
		if [[ $value =~ [[:space:]\$\"\'\`\\#] ]]; then
			rm -f "$tmp"
			kept "파라미터 ${name}의 값에 env 파일로 넣을 수 없는 글자가 있다"
		fi
		printf '%s=%s\n' "$name" "$value" >>"$tmp"
	done <<<"$raw"
	for name in "${REQUIRED_PARAMS[@]}"; do
		grep -q "^${name}=." "$tmp" || missing+=("$name")
	done
	if ((${#missing[@]} > 0)); then
		rm -f "$tmp"
		kept "파라미터가 없다: ${missing[*]}"
	fi
	for name in "${FORBIDDEN_PARAMS[@]}"; do
		if grep -q "^${name}=" "$tmp"; then
			rm -f "$tmp"
			kept "파라미터 ${name}는 쓰지 않는다(운영 프로파일은 compose.yaml이 정한다)"
		fi
	done
	# 더하는 프로파일은 trial 하나만. local 등을 더하면 공개 저장소의 비밀번호·가입 코드로 계정이 생긴다.
	if grep -q "^SPRING_PROFILES_INCLUDE=" "$tmp" && ! grep -qx "SPRING_PROFILES_INCLUDE=trial" "$tmp"; then
		rm -f "$tmp"
		kept "파라미터 SPRING_PROFILES_INCLUDE는 trial만 쓸 수 있다"
	fi
	chmod 600 "$tmp"
	mv -f "$tmp" "$APP_ENV"
}

wait_ready() {
	local url deadline
	url="http://127.0.0.1:$(port_of "$target")/actuator/health/readiness"
	deadline=$((SECONDS + READY_TIMEOUT_SECONDS))
	until curl -fsS -o /dev/null --max-time 2 "$url"; do
		((SECONDS >= deadline)) && abandon_target "readiness가 ${READY_TIMEOUT_SECONDS}초 안에 통과하지 않았다"
		sleep 2
	done
}

health_through_nginx() {
	local tries=10
	until curl -fsS -o /dev/null --max-time 3 http://127.0.0.1/api/health; do
		((--tries > 0)) || return 1
		sleep 1
	done
}

main() {
	local tag="${1:-}"
	[[ $tag =~ ^[0-9a-f]{40}$ ]] || kept "이미지 태그가 커밋 SHA(40자) 형식이 아니다"

	exec 9>"${JG4S_LOCK_FILE:-/run/lock/jg4seasons-deploy}"
	flock -n 9 || kept "다른 배포가 진행 중이다"

	if [[ ! -f $SLOTS_ENV ]] || ! grep -q '^ECR_REGISTRY=.' "$SLOTS_ENV"; then
		kept "slots.env에 ECR_REGISTRY가 없다"
	fi
	previous_upstream="$(cat "$UPSTREAM" 2>/dev/null || true)"
	case "$previous_upstream" in
	"server app-blue:8080;") current=blue target=green ;;
	"server app-green:8080;") current=green target=blue ;;
	"") current="" target=blue ;;
	*) kept "active.conf가 알 수 없는 모양이다" ;;
	esac
	previous_tag="$(grep "^$(tag_var_of "$target")=" "$SLOTS_ENV" | cut -d= -f2- || true)"

	write_app_env

	set_slot_tag "$target" "$tag"
	compose pull --quiet "app-$target" || {
		set_slot_tag "$target" "$previous_tag"
		kept "이미지를 받지 못했다: $tag"
	}
	compose up -d --no-deps --force-recreate "app-$target" || abandon_target "새 슬롯을 띄우지 못했다"
	wait_ready

	printf 'server app-%s:8080;\n' "$target" >"$UPSTREAM.tmp"
	mv -f "$UPSTREAM.tmp" "$UPSTREAM"
	local applied=""
	if apply_upstream; then applied=1; fi
	if [[ -z $applied ]] || ! health_through_nginx; then
		if ! restore_upstream && [[ -n $applied ]]; then
			# Nginx는 새 슬롯 설정 그대로 돈다. 파일을 그에 맞추고, 요청을 받는 새 슬롯은 멈추지 않는다.
			printf 'server app-%s:8080;\n' "$target" >"$UPSTREAM"
			switched=1
			failed "$target" "Nginx를 이전 슬롯으로 되돌리지 못했다. 요청은 app-$target이 받는다(멈추지 않았다)"
		fi
		abandon_target "Nginx를 새 슬롯으로 옮기지 못했다"
	fi
	switched=1

	printf '%s\n' "$target" >"$STATE.tmp"
	mv -f "$STATE.tmp" "$STATE"
	if [[ -n $current ]]; then
		# reload 전에 받은 요청이 이전 슬롯에서 끝날 시간을 준 뒤 graceful 종료한다.
		sleep 5
		compose stop -t 30 "app-$current" || echo "이전 슬롯 app-$current를 멈추지 못했다(요청은 새 슬롯이 받는다)"
		# reload 직후의 확인은 남아 있던 이전 worker·이전 슬롯이 답했을 수 있다. 이전 슬롯이 없는 지금 다시 본다.
		health_through_nginx ||
			failed "$target" "이전 슬롯을 멈춘 뒤 Nginx 경유 확인이 실패했다. 서버에서 Nginx·app-$target을 직접 본다(app-$current는 멈춤)"
	fi
	docker image prune -f >/dev/null || true
	echo "deployed $target $tag"
}

main "$@"
