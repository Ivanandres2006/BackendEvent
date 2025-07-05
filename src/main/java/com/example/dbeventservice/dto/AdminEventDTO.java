package com.example.dbeventservice.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
@AllArgsConstructor
public class AdminEventDTO {
    private Long id;
    private String title;
    private String location;
    private int totalTickets;
    private int soldTickets;
    private double totalRevenue;
}
