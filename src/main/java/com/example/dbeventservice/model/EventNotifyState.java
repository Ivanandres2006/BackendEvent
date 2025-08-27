// EventNotifyState.java
package com.example.dbeventservice.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class EventNotifyState {
    @Id
    private Long eventId;
    private boolean sent50;
    private boolean sent80;
    private boolean sent100;
}
