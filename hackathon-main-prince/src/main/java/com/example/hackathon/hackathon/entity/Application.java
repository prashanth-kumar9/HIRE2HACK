package com.example.hackathon.hackathon.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "applications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String candidateName;

    private String email;

    private String phoneNumber;

    private String position;

    @Column(nullable = false)
    private Boolean selected = false;

    private LocalDateTime selectedAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean smsSent = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean emailSent24Hrs = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean emailSent48Hrs = false;

    private LocalDateTime hrScheduled;

    private LocalDateTime smsSentTime;

    private LocalDateTime emailSent24HrsTime;

    private LocalDateTime emailSent48HrsTime;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
