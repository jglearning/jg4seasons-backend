package com.jglearning.jg4seasons.content;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.Repository;

interface BookRepository extends Repository<Book, Long> {

    @EntityGraph(attributePaths = "level")
    Optional<Book> findById(Long id);
}
