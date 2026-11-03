# 0021. 프론트 TypeScript 6.0 고정

- 상태: 결정 · 날짜: 2026-11-02

## 배경
create-vite 9.3이 TypeScript 7.0을 넣었으나 typescript-eslint 8.72의 지원 범위는 `<6.1.0`이라 설치가 실패했다(ERESOLVE).

## 선택지
### A. `--legacy-peer-deps`로 강제 설치
- 장점: 최신 TypeScript
- 단점: lint가 깨져도 알아차리기 어려움
### B. TypeScript를 지원 범위 안(6.0.x)으로 내림
- 장점: 도구 체인이 지원 범위 안에서 동작
- 단점: 7.0 기능을 못 씀

## 결정
B. `~6.0.3`. tsconfig에 `strict: true` 명시(버전마다 기본값이 달라서).

## 근거
A는 lint 실패를 숨긴다.

## 포기한 것
TypeScript 7.0.

## 재검토 조건
typescript-eslint가 TypeScript 7을 지원하면 올린다.
