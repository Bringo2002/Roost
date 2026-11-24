package com.roost.repository;

import com.roost.model.User;
import com.roost.model.UserReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface UserReportRepository extends JpaRepository<UserReport, Long> {

    /** How many reports {@code reporter} has filed against {@code reported} since {@code since}. */
    long countByReporterAndReportedAndCreatedAtAfter(User reporter, User reported, LocalDateTime since);
}
