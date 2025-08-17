package com.example.dbeventservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "badges")
@Getter @Setter
public class Badge {
    @Id @Column(length = 64)
    private String key;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "icon_url")
    private String iconUrl;

    @Column
    private String description;

    @Column(nullable = false)
    private boolean hidden = false;
}
