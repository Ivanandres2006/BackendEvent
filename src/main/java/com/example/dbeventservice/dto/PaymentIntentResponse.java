package com.example.dbeventservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class PaymentIntentResponse {
    private String clientSecret;
    private String customerId;
    private String ephemeralKey;
}
