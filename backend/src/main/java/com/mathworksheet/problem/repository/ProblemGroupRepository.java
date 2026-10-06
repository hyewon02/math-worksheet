package com.mathworksheet.problem.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mathworksheet.problem.domain.ProblemGroup;

public interface ProblemGroupRepository extends JpaRepository<ProblemGroup, Long> {
}
