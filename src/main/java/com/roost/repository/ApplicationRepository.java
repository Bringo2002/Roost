package com.roost.repository;

import com.roost.model.Application;
import com.roost.model.Property;
import com.roost.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, Long> {

    List<Application> findByPropertyOrderByCreatedAtDesc(Property property);

    List<Application> findByApplicantOrderByCreatedAtDesc(User applicant);

    boolean existsByPropertyAndApplicant(Property property, User applicant);
}
