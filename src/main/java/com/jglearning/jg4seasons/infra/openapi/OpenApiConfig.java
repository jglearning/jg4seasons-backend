package com.jglearning.jg4seasons.infra.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * docs/api/openapi.json의 머리말.
 *
 * <p>servers는 상대 경로("/")로 둔다. 요청 호스트(localhost 등)가 커밋되는 명세에 들어가지 않게 하려는 것이다. FE·API는 같은 도메인이다(ADR-18).
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info().title("jg4seasons API").version("v1"))
                .servers(List.of(new Server().url("/")));
    }
}
