package com.jglearning.archfixture.listening.web;

import com.jglearning.archfixture.listening.FixtureEntity;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** 위반: 엔티티를 제네릭 안에 감싸 반환한다. */
@RestController
public class EntityListController {
    public ResponseEntity<List<FixtureEntity>> list() {
        return ResponseEntity.ok(List.of());
    }
}
