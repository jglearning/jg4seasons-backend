package com.jglearning.jg4seasons.talk.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 프론트가 타입을 만드는 명세(docs/api/openapi.json)의 프리토킹 칸 모양. 명세 테스트는 생성본 = 커밋본만 보므로, 생성 규칙이 칸 타입을 바꿔도 여기서 잡는다(#77 리뷰 1 —
 * JsonNode usage가 string으로 나가 프론트가 문자열을 보내면 400)
 */
class TalkOpenApiContractTest {

    static final JsonNode SPEC = JsonMapper.builder().build().readTree(read());

    @Test
    void 종료_요청의_usage는_객체_또는_null() {
        JsonNode usage = SPEC.at("/components/schemas/TalkSessionEndRequest/properties/usage");

        assertThat(types(usage)).contains("object").doesNotContain("string");
    }

    private static List<String> types(JsonNode node) {
        List<String> types = new ArrayList<>();
        JsonNode type = node.get("type");
        if (type != null && type.isArray()) {
            type.forEach(t -> types.add(t.asString()));
        } else if (type != null) {
            types.add(type.asString());
        }
        return types;
    }

    private static String read() {
        try {
            return Files.readString(Path.of("docs/api/openapi.json"));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
