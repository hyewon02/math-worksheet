package com.mathworksheet.worksheet.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mathworksheet.worksheet.domain.Template;

public interface TemplateRepository extends JpaRepository<Template, Long> {

    Optional<Template> findByFilePathAndBuiltInTrue(String filePath);
}
