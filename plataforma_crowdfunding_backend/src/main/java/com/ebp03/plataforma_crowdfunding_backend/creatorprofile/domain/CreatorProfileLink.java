package com.ebp03.plataforma_crowdfunding_backend.creatorprofile.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "creator_profile_links")
public class CreatorProfileLink {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_profile_id", nullable = false)
    private CreatorProfile profile;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", nullable = false, length = 30)
    private CreatorProfileLinkType linkType;

    @Column(length = 120)
    private String label;

    @Column(nullable = false, length = 1000)
    private String url;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CreatorProfileLink() {
    }

    public CreatorProfileLink(CreatorProfile profile, CreatorProfileLinkType linkType,
                              String label, String url, int sortOrder) {
        this.id = UUID.randomUUID();
        this.profile = profile;
        this.linkType = linkType;
        this.label = label;
        this.url = url;
        this.sortOrder = sortOrder;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public CreatorProfile getProfile() { return profile; }
    public CreatorProfileLinkType getLinkType() { return linkType; }
    public String getLabel() { return label; }
    public String getUrl() { return url; }
    public int getSortOrder() { return sortOrder; }
}
