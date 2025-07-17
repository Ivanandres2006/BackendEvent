package com.example.dbeventservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AdminUserDTO {
    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private boolean canCreateEvents;
}
