package com.jglearning.jg4seasons.content.importer;

/** 회차 하나를 넣은 결과. reason은 REJECTED일 때만 */
public record ImportResult(String code, Status status, String reason) {

    public ImportResult {
        boolean hasReason = reason != null && !reason.isBlank();
        if ((status == Status.REJECTED) != hasReason) {
            throw new IllegalArgumentException("사유는 REJECTED일 때만, 그리고 반드시 있어야 한다: " + status + " " + reason);
        }
    }

    public enum Status {
        /** 행을 하나 이상 넣거나 고치거나 지웠다 */
        IMPORTED,
        /** DB가 이미 manifest와 같다 */
        UNCHANGED,
        /** 아무것도 바꾸지 않았다 */
        REJECTED
    }

    static ImportResult rejected(String code, String reason) {
        return new ImportResult(code, Status.REJECTED, reason);
    }
}
