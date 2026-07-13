package com.roost.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "verification_records")
public class VerificationRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long propertyId;
    private String tenantPhone;
    private double amountPaid;
    private String mpesaReceiptNumber;
    private String status; // PENDING, SECURED, REFUNDED

    private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToOne
    @JoinColumn(name = "tenant_id")
    private User tenant;
}
