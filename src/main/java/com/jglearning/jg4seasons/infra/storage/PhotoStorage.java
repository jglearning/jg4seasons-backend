package com.jglearning.jg4seasons.infra.storage;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

/**
 * 숙제 사진 저장소(단어시험 사진 채점). 외부 연동(S3)을 이 인터페이스 뒤에 둔다 — 구현은 {@code jg4seasons.word-test.storage.type}으로 고른다(s3·local, 테스트는
 * 메모리). 키는 부르는 쪽이 정하고 검사한다. 키·주소는 학생 id를 담으므로 로그에 남기지 않는다.
 */
public interface PhotoStorage {

    /** 브라우저가 바로 PUT할 서명 주소. 응답의 headers를 요청 헤더로 그대로 보내야 한다(서명에 Content-Type 포함) */
    UploadTicket presignPut(String key, String contentType, Duration ttl);

    /** 올라간 사진의 크기·형식. 없으면 empty */
    Optional<StoredPhoto> head(String key);

    /**
     * 사진 바이트. maxBytes를 넘으면 내려받지 않고 PhotoTooLargeException(제출 뒤 같은 주소로 큰 파일을 덮어쓴 경우), 없으면 NoSuchElementException
     */
    byte[] read(String key, long maxBytes);

    /** 짧은 유효 시간의 보기 주소(교사 화면). 만들 수 없는 저장소면 empty */
    Optional<URI> presignGet(String key, Duration ttl);
}
