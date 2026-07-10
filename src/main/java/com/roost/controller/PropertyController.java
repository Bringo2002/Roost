package com.roost.controller;


import com.roost.model.Property;
import com.roost.service.PropertyService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/properties")
public class PropertyController {

    private final PropertyService propertyService;

    public PropertyController(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    @GetMapping
    public List<Property> getAllProperties() {
        return propertyService.getAllProperties();
    }

    @PostMapping
    public Property addProperty(@RequestBody Property property) {
        return propertyService.addProperty(property);
    }

    @GetMapping("/hello")
    public String hello() {
        return "Roost API is LIVE";
    }

    @GetMapping("/location/{location}")
    public List<Property> getByLocation(@PathVariable String location) {
        return propertyService.getByLocation(location);
    }

    @GetMapping("/available")
    public List<Property> getAvailable() {
        return propertyService.getAvailableProperties();
    }

    @GetMapping("/type/{type}")
    public List<Property> getByType(@PathVariable String type) {
        return propertyService.getByType(type);
    }

    @GetMapping("/price")
    public List<Property> getByPriceRange(
            @RequestParam double min,
            @RequestParam double max) {
        return propertyService.getByPriceRange(min, max);
    }

    @DeleteMapping("/{id}")
    public void deleteProperty(@PathVariable Long id) {
        propertyService.deleteProperty(id);
    }

    @PutMapping("/{id}")
    public Property updateProperty(@PathVariable Long id, @RequestBody Property property) {
        return propertyService.updateProperty(id, property);
    }

}