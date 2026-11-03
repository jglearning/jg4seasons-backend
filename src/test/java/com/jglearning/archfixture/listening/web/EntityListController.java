package com.jglearning.archfixture.listening.web;

import com.jglearning.archfixture.listening.FixtureEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 위반 3건: 제네릭 안의 엔티티, 엔티티 배열, Optional 엔티티. */
@RestController
public class EntityListController {
    @GetMapping("/fixture/list")
    public ResponseEntity<List<FixtureEntity>> list() {
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/fixture/array")
    public FixtureEntity[] array() {
        return new FixtureEntity[0];
    }

    @GetMapping("/fixture/optional")
    public Optional<FixtureEntity> optional() {
        return Optional.empty();
    }
}
