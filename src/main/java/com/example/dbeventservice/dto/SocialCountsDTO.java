// src/main/java/.../dto/SocialCountsDTO.java
package com.example.dbeventservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data @AllArgsConstructor
public class SocialCountsDTO {
    private long followers; // ACCEPTED followers of user
    private long following; // users this user ACCEPTED-follows
    private long friends;   // mutual ACCEPTED
}
