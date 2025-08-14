package com.example.dbeventservice.dto;

import lombok.Data;
import java.util.List;

@Data
public class IdentitiesPayload {
    private List<String> emails;   // plaintext
    private List<String> phones;   // plaintext
}
