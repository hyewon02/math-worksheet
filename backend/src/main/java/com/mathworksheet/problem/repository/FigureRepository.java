package com.mathworksheet.problem.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mathworksheet.problem.domain.Figure;

public interface FigureRepository extends JpaRepository<Figure, Long> {

    Optional<Figure> findByMarker(String marker);
}
