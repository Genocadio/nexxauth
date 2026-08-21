package com.nexxserve.nauth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * A tenant / workspace. Created at signup, identified by a unique slug.
 */
@Getter
@Setter
@Entity
@Table(name = "platforms")
public class Platform extends BaseEntity {

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 100)
    private String slug;

    @OneToMany(mappedBy = "platform")
    private List<PlatformUser> users = new ArrayList<>();
}
