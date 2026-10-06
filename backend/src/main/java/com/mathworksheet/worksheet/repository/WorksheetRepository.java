package com.mathworksheet.worksheet.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.mathworksheet.worksheet.domain.Worksheet;

public interface WorksheetRepository extends JpaRepository<Worksheet, Long> {

    List<Worksheet> findAllByOrderByUpdatedAtDesc();

    /** 문제지 번호 WS-0001 … 용 일련번호 */
    @Query(value = "SELECT NEXT VALUE FOR worksheet_number_seq", nativeQuery = true)
    long nextNumber();
}
