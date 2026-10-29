package com.ohiggins.classflow.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Identidad externa vinculada a un usuario interno de ClassFlow.
 */
@Entity
@Table(
        name = "user_identities",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_user_identities_provider_subject",
                columnNames = {"provider", "tenant_id", "external_subject"}),
        indexes = {
                @Index(name = "idx_user_identities_user_id", columnList = "user_id"),
                @Index(name = "idx_user_identities_tenant_subject", columnList = "tenant_id, external_subject")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(name = "external_subject", nullable = false, length = 255)
    private String externalSubject;

    @Column(name = "tenant_id", nullable = false, length = 255)
    private String tenantId;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}