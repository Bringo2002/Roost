package com.roost.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
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

    @Column(columnDefinition = "TEXT")
    private String description;

    private String imageUrl;

    private boolean verified = false;

    private boolean holdingFeePaid = false;

    private Double latitude;

    private Double longitude;

    private String videoUrl;

    @ElementCollection
    @CollectionTable(name = "property_image_urls", joinColumns = @JoinColumn(name = "property_id"))
    @Column(name = "image_url")
    private java.util.List<String> imageUrls = new java.util.ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "owner_id")
    private User owner;

    @Transient
    private Double averageRating;

    @Transient
    private Long reviewCount;
}