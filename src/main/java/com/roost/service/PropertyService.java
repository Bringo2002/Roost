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

    public List<Property> getByPriceRange(double minPrice, double maxPrice) {
        return propertyRepository.findByPriceBetween(minPrice, maxPrice);
    }

    public void deleteProperty(Long id) {
        propertyRepository.deleteById(id);
    }

    public Property updateProperty(Long id, Property updated) {
        Property existing = propertyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Property not found with id: " + id));
        existing.setTitle(updated.getTitle());
        existing.setLocation(updated.getLocation());
        existing.setPrice(updated.getPrice());
        existing.setBedrooms(updated.getBedrooms());
        existing.setType(updated.getType());
        existing.setAvailable(updated.isAvailable());
        return propertyRepository.save(existing);
    }
}

