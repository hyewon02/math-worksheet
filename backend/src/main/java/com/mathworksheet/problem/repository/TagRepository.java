package com.mathworksheet.problem.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mathworksheet.problem.domain.Tag;

public interface TagRepository extends JpaRepository<Tag, Long> {

    Optional<Tag> findByName(String name);
}
