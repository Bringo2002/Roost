package com.roost.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "properties")
public class Property {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String location;
    private double price;
    private int bedrooms;
    private String type; // rental, sale, airbnb
    private boolean available;
    private String landlordPhone;
}