package com.jglearning.jg4seasons.content.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jglearning.jg4seasons.content.importer.ImportResult.Status;
import org.junit.jupiter.api.Test;

class ImportResultTest {

    @Test
    void 거부는_사유가_있어야_한다() {
        assertThatThrownBy(() -> new ImportResult("L1-B1-01", Status.REJECTED, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ImportResult("L1-B1-01", Status.REJECTED, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ImportResult.rejected("L1-B1-01", "기준 데이터에 없는 회차").reason()).isEqualTo("기준 데이터에 없는 회차");
    }

    @Test
    void 성공에는_사유가_없다() {
        assertThatThrownBy(() -> new ImportResult("L1-B1-01", Status.IMPORTED, "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new ImportResult("L1-B1-01", Status.UNCHANGED, null).reason())
                .isNull();
    }
}
