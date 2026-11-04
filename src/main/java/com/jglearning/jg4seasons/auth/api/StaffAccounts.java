package com.jglearning.jg4seasons.auth.api;

import java.util.List;

/**
 * 교사·관리자 계정(ADM-04). 다른 모듈(admin)이 쓴다. academyId·actorId는 인증 주체(JWT)에서 온 값이어야 한다.
 *
 * <p>다른 학원 계정·학생 id는 404 NOT_FOUND. 아이디는 공백 제거 + 소문자, 3~30자 영문·숫자·.·_·-(아니면 400 VALIDATION), 이미 있으면(전체 학원)
 * 409 LOGIN_ID_TAKEN. 비밀번호는 8자 이상·72바이트 이하(아니면 400). 자기 비활성화는 409 SELF_CHANGE_NOT_ALLOWED. 비활성화·비밀번호 재설정이면
 * 그 사용자의 refresh를 모두 끊는다.
 */
public interface StaffAccounts {

    /** 요청의 로그인 아이디 형식(앞뒤 공백 허용). 요청 DTO의 @Pattern에 쓴다 */
    String LOGIN_ID_REQUEST_REGEXP = "^\\s*[A-Za-z0-9._-]{3,30}\\s*$";

    String LOGIN_ID_MESSAGE = "아이디는 영문·숫자·.·_·- 3~30자예요";

    /**
     * 요청한 사람(JWT의 사용자)이 지금도 이 학원의 활성 관리자인지 확인한다. 아니면 401 UNAUTHORIZED. JWT의 role은 발급 때 값이라, 비활성화·역할
     * 변경·학원 정지 뒤에도 남은 access(최대 30분)로 관리 API를 쓰지 못하게 쓰기 전에 부른다
     */
    void requireActiveAdmin(long academyId, long actorId);

    List<StaffAccount> list(long academyId);

    StaffAccount find(long academyId, long staffId);

    StaffAccount create(long academyId, NewStaff staff);

    StaffAccount change(long academyId, long actorId, long staffId, StaffChange change);
}
