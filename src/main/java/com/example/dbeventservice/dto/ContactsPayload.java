package com.example.dbeventservice.dto;

import lombok.Data;
import java.util.List;

@Data
public class ContactsPayload {
    private boolean hashed;            // true if values are already SHA-256 hex using same normalization + pepper
    private List<String> emails;       // plaintext or hash (see 'hashed')
    private List<String> phones;       // plaintext or hash (see 'hashed')
}
