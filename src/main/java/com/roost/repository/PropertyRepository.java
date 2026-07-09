package com.roost.repository;

import com.roost.model.Property;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PropertyRepository extends JpaRepository<Property, Long> {
    List<Property>findByLocationContainingIgnoreCase(String Location);
    List<Property>findByAvailableTrue();
    List<Property>findByType(String type);
}
