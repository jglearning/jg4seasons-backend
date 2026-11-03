# 0017. 오류 응답은 RFC 9457 Problem Details + 확장 `code`

- 상태: 결정 · 날짜: 2026-11-01

## 배경
API 명세 초안은 `{code, message, details}` 자체 형식이었다. 다른 프로젝트에서 이미 Problem Details 규칙을 쓰고 있다.

## 선택지
### A. 자체 형식 유지
- 장점: 단순
- 단점: 프로젝트마다 프론트 오류 처리 코드가 달라짐. 형식 설명이 필요
### B. RFC 9457 Problem Details + 확장 필드
- 장점: 표준이라 설명 비용 없음. 프론트 오류 처리 코드를 프로젝트 간에 공유
- 단점: 필드가 조금 많음

## 결정
B. `application/problem+json`, 필드 `type`·`title`·`status`·`detail`·`instance` + 확장 `code`·`requestId`·`errors[]`. 프론트는 `title`·`detail`을 사용자 메시지로, `code`로 분기한다.

## 근거
표준이고, 프론트 오류 처리를 통일할 수 있다. API 명세에 반영했다.

## 포기한 것
없음.

## 재검토 조건
없음.
