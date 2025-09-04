package com.example.dbeventservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@AllArgsConstructor @NoArgsConstructor
public class UserSearchDTO {
    private Long id;
    private String username;
    private String displayName;
}
