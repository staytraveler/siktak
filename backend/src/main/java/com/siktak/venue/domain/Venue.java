package com.siktak.venue.domain;

import com.siktak.common.domain.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 제휴 식당 (erd.md venues, PRD INF-01·03). */
@Getter
@Entity
@Table(name = "venues")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Venue extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "region_id", nullable = false)
    private Long regionId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String address;

    @Column(name = "map_url")
    private String mapUrl;

    /** {@code UNDER_20K} / {@code 20K_40K} / {@code OVER_40K} (matching.md 4장). */
    @Column(name = "price_range")
    private String priceRange;

    private String description;

    @Column(nullable = false)
    private boolean active = true;

    public static Venue create(Long regionId, String name, String address, String mapUrl, String priceRange,
            String description) {
        Venue v = new Venue();
        v.regionId = regionId;
        v.name = name;
        v.address = address;
        v.mapUrl = mapUrl;
        v.priceRange = priceRange;
        v.description = description;
        return v;
    }
}
