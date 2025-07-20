package com.example.dbeventservice.model;

import com.example.dbeventservice.model.Enum.ChatType;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private User buyer;

    @ManyToOne
    private User seller;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ChatType type;


    private Long eventId; // To associate DIRECT and PROMOTER_GROUP with events

    @ManyToMany(fetch = FetchType.EAGER, cascade = {CascadeType.MERGE, CascadeType.PERSIST})
    @JoinTable(name = "chatroom_participants",
            joinColumns = @JoinColumn(name = "chatroom_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"))
    @Builder.Default
    private Set<User> participants = new HashSet<>();

    private String otherUsername;



    @ManyToOne
    private ResalePost resalePost;

    @Column(nullable = false)
    private boolean accepted = false; // Seller accepts or not
}
