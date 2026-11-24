package com.jglearning.jg4seasons.wordtest.reading;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/**
 * 테스트용 답안지 읽기(외부 호출 없음). 기본은 모든 문항을 빈칸으로 읽는다. 테스트가 {@link #willRead}·{@link #willFail}·{@link #duringRead}로 바꾸고 끝에
 * {@link #reset}
 */
public class FakeAnswerSheetReader implements AnswerSheetReader {

    public static final String MODEL = "fake-model";

    private final AtomicInteger calls = new AtomicInteger();
    private volatile IntFunction<List<ReadItem>> answers = FakeAnswerSheetReader::blanks;
    private volatile ReadFailure.Kind failure;
    private final AtomicInteger failuresLeft = new AtomicInteger();
    private volatile IntConsumer duringRead = count -> {};
    private volatile boolean enabled = true;
    private volatile RuntimeException toThrow;
    private volatile byte[] lastImage;
    private volatile String lastMediaType;
    private volatile int lastItemCount = -1;

    @Override
    public ReadResult read(byte[] image, String mediaType, int itemCount) {
        calls.incrementAndGet();
        lastImage = image;
        lastMediaType = mediaType;
        lastItemCount = itemCount;
        duringRead.accept(itemCount);
        if (toThrow != null) {
            throw toThrow;
        }
        if (failure != null && failuresLeft.getAndDecrement() > 0) {
            throw new ReadFailure(failure, "가짜 실패");
        }
        return new ReadResult(answers.apply(itemCount), MODEL, "v1", 100, 50);
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    /** 문항 수 → 읽은 칸 목록 */
    public void willRead(IntFunction<List<ReadItem>> answers) {
        this.answers = answers;
    }

    /** 앞의 times번은 kind로 실패, 그 뒤는 willRead대로 */
    public void willFail(ReadFailure.Kind kind, int times) {
        this.failure = kind;
        this.failuresLeft.set(times);
    }

    /** 읽는 도중(외부 호출 자리)에 할 일 — 이때 DB 잠금이 없는지 확인할 때 */
    public void duringRead(IntConsumer hook) {
        this.duringRead = hook;
    }

    /** 읽기에서 이 예외를 던진다(예상 못 한 오류) */
    public void willThrow(RuntimeException exception) {
        this.toThrow = exception;
    }

    public byte[] lastImage() {
        return lastImage;
    }

    public String lastMediaType() {
        return lastMediaType;
    }

    public int lastItemCount() {
        return lastItemCount;
    }

    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int calls() {
        return calls.get();
    }

    public void reset() {
        calls.set(0);
        answers = FakeAnswerSheetReader::blanks;
        failure = null;
        failuresLeft.set(0);
        duringRead = count -> {};
        enabled = true;
        toThrow = null;
        lastImage = null;
        lastMediaType = null;
        lastItemCount = -1;
    }

    private static List<ReadItem> blanks(int count) {
        List<ReadItem> items = new ArrayList<>();
        for (int no = 1; no <= count; no++) {
            items.add(new ReadItem(no, "", false));
        }
        return items;
    }
}
