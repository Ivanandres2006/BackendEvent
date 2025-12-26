package com.example.dbeventservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String type = "event"; // "event" or "reunion"

    private String title;
    private String description;
    private String location;
    private LocalDateTime dateTime;
    private String imageUrl;

    private String state;
    private String address;
    private String country;
    private Double latitude;
    private Double longitude;

    @Column(nullable = false)
    private boolean requireCedula = false;

    private Double price; // ✅ Base/default price (optional)
    private Integer availableTickets; // ✅ Aggregate available quantity

    //---WaitList---
    @Column(nullable = false)
    private boolean listOnly = false;
    private String guestListPdf;
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WaitlistEntry> waitlistEntries = new ArrayList<>();
    //-------------

    @ManyToOne
    @JoinColumn(name = "created_by_id")
    private User createdBy;



    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TicketTier> ticketTiers = new ArrayList<>();

    @ManyToMany
    @JoinTable(
            name = "event_attendees",
            joinColumns = @JoinColumn(name = "event_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> attendees = new HashSet<>();

    @JsonProperty("attendees")
    public Set<String> getAttendeeEmails() {
        return attendees.stream().map(User::getEmail).collect(Collectors.toSet());
    }

    @JsonProperty("createdByEmail")
    public String getCreatorEmail() {
        return createdBy != null ? createdBy.getEmail() : null;
    }

    @JsonProperty("createdByUsername")
    public String getCreatorUsername() {
        return createdBy != null ? createdBy.getUsername() : null;
    }

    private String budgetPurpose;
    private Double budgetGoal;
    private String zelleEmail;
    private String zellePhone;

    private String pagoMovilPhone;
    private String pagoMovilCi;
    private String pagoMovilBank;
    private String cashNote;


    @Column
    private boolean allowPagoMovil;

    @Column
    private boolean allowCash;


    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReunionGuest> reunionGuests = new ArrayList<>();


    public void updateAggregateInfoFromTiers() {
        // Safely sum all available quantities (default to 0 if null)
        this.availableTickets = ticketTiers.stream()
                .mapToInt(t -> t.getAvailableQuantity() != null ? t.getAvailableQuantity() : 0)
                .sum();

        // Optionally calculate the minimum price from non-null prices
        this.price = ticketTiers.stream()
                .filter(t -> t.getPrice() != null)
                .mapToDouble(TicketTier::getPrice)
                .min()
                .orElse(0.0); // Default to 0.0 if no valid prices
    }

    @JsonProperty("budgetGoal")
    public Double getBudgetGoal() {
        return budgetGoal;
    }

    @JsonProperty("budgetPurpose")
    public String getBudgetPurpose() {
        return budgetPurpose;
    }

    @JsonProperty("zelleEmail")
    public String getZelleEmail() {
        return zelleEmail;
    }

    @JsonProperty("zellePhone")
    public String getZellePhone() {
        return zellePhone;
    }


    @JsonProperty("reunionGuests")
    public List<ReunionGuest> getGuests() {
        return reunionGuests;
    }

    @JsonProperty("pagoMovilPhone")
    public String getPagoMovilPhone() {
        return pagoMovilPhone;
    }

    @JsonProperty("pagoMovilCi")
    public String getPagoMovilCi() {
        return pagoMovilCi;
    }

    @JsonProperty("pagoMovilBank")
    public String getPagoMovilBank() {
        return pagoMovilBank;
    }

    @JsonProperty("cashNote")
    public String getCashNote() {
        return cashNote;
    }

}
