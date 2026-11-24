package com.jglearning.jg4seasons.infra.storage;

/**
 * 읽으려는 사진이 상한보다 크다. presigned PUT은 크기를 서명에 걸 수 없어, 제출 검사 뒤 같은 주소로 큰 파일을 다시 올릴 수 있다 — 읽는 쪽이 상한을 넘겨 메모리에 올리지
 * 않게 막는다
 */
public class PhotoTooLargeException extends RuntimeException {

    public PhotoTooLargeException(long maxBytes) {
        super("사진이 " + maxBytes + "바이트보다 크다");
    }
}
