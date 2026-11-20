package com.jglearning.jg4seasons.listening.passage;

import com.jglearning.jg4seasons.listening.segment.Range;
import java.util.List;
import java.util.Optional;

/**
 * 한 목소리의 청취 구간을 같은 지문의 다른 목소리 시간축으로 옮긴다(ADR 0034). 목소리마다 음원 길이가 달라 ms를 그대로 합칠 수 없어서, 여러 목소리로 들은 구간을 합치기
 * 전에 시간축 하나로 모은다.
 *
 * <p>지문 음원을 조각으로 나눈다: 앞 여백, 문장 0, 간격 0, 문장 1, …, 문장 n-1, 뒤 여백. 문장 수가 같으므로 모든 목소리에서 조각의 수와 순서가 같다. 한 목소리의
 * 시각이 k번째 조각의 비율 r 지점이면 다른 목소리에서는 k번째 조각의 비율 r 지점이다. 이 변환은 끊김 없이 늘기만 하므로 구간은 양 끝점만 옮기면 구간 하나가 된다.
 *
 * <ul>
 *   <li>반올림은 안쪽(시작 올림, 끝 내림) — 옮긴 결과가 실제보다 넓어지지 않는다
 *   <li>길이 0인 조각의 경계(간격 없이 붙은 문장): 시작점은 뒤 조각의 처음, 끝점은 앞 조각의 끝으로 간다
 *   <li>한쪽이라도 문장 시각이 없으면 음원 전체를 조각 하나로 본다(길이 비율)
 *   <li>근사는 하나: 조각 하나 안에서는 말하는 빠르기가 고르다. 오차는 그 조각 안에서만 생긴다
 * </ul>
 *
 * <p>입력은 병합이 끝난 트랙 집계 구간이어야 한다. 원래 이어서 들은 것은 한 구간으로 넘어와 간격까지 덮는다.
 */
public final class PassageAxis {

    private PassageAxis() {}

    /** from 시간축의 구간을 to 시간축으로. from 길이를 넘는 부분은 버린다. 안쪽 반올림 뒤 길이가 0 이하면 빈 값 */
    public static Optional<Range> map(VoiceAxis from, VoiceAxis to, Range range) {
        int[] source = bounds(from, to);
        int[] target = bounds(to, from);
        if (range.startMs() >= from.durationMs()) {
            return Optional.empty();
        }
        int start = mapStart(source, target, range.startMs());
        int end = mapEnd(source, target, Math.min(range.endMs(), from.durationMs()));
        return Range.isValid(start, end) ? Optional.of(new Range(start, end)) : Optional.empty();
    }

    /** 여러 구간을 옮긴다. 사라진 구간은 뺀다. 순서는 그대로 */
    public static List<Range> mapAll(VoiceAxis from, VoiceAxis to, List<Range> ranges) {
        return ranges.stream().flatMap(range -> map(from, to, range).stream()).toList();
    }

    /** 조각 경계: 0, s0, e0, s1, e1, …, 길이. 둘 중 하나라도 문장 시각이 없으면 0, 길이 */
    private static int[] bounds(VoiceAxis axis, VoiceAxis other) {
        if (axis.sentences().isEmpty() || other.sentences().isEmpty()) {
            return new int[] {0, axis.durationMs()};
        }
        if (axis.sentences().size() != other.sentences().size()) {
            throw new IllegalArgumentException("같은 지문인데 문장 수가 다르다: "
                    + axis.sentences().size() + " / " + other.sentences().size());
        }
        int[] bounds = new int[axis.sentences().size() * 2 + 2];
        int index = 1;
        for (Range sentence : axis.sentences()) {
            bounds[index++] = sentence.startMs();
            bounds[index++] = sentence.endMs();
        }
        bounds[index] = axis.durationMs();
        return bounds;
    }

    /** 구간의 시작점: 그 시각에서 시작하는(또는 품는) 조각으로 — 경계에서는 뒤 조각. 올림 */
    private static int mapStart(int[] source, int[] target, int time) {
        for (int piece = 0; piece < source.length - 1; piece++) {
            if (source[piece] <= time && time < source[piece + 1]) {
                long scaled = Math.ceilDiv((long) (time - source[piece]) * (target[piece + 1] - target[piece]), (long)
                        (source[piece + 1] - source[piece]));
                return target[piece] + (int) scaled;
            }
        }
        return target[target.length - 1];
    }

    /** 구간의 끝점: 그 시각에서 끝나는(또는 품는) 조각으로 — 경계에서는 앞 조각. 내림 */
    private static int mapEnd(int[] source, int[] target, int time) {
        for (int piece = 0; piece < source.length - 1; piece++) {
            if (source[piece] < time && time <= source[piece + 1]) {
                long scaled = (long) (time - source[piece])
                        * (target[piece + 1] - target[piece])
                        / (source[piece + 1] - source[piece]);
                return target[piece] + (int) scaled;
            }
        }
        return 0;
    }
}
