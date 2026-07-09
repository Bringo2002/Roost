package com.roost.service;

import com.roost.model.Property;
import com.roost.repository.PropertyRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class PropertyService {

    private final PropertyRepository propertyRepository;

    public PropertyService(PropertyRepository propertyRepository) {
        this.propertyRepository = propertyRepository;
    }

    public List<Property> getAllProperties() {
        return propertyRepository.findAll();
    }

    public Property addProperty(Property property) {
        return propertyRepository.save(property);
    }

    public List<Property> getByLocation(String location) {
        return propertyRepository.findByLocationContainingIgnoreCase(location);
    }

    public List<Property> getAvailableProperties() {
        return propertyRepository.findByAvailableTrue();
    }

    public List<Property> getByType(String type) {
        return propertyRepository.findByType(type);
    }
}

