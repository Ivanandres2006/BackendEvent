package com.example.dbeventservice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CounterOfferRequest {
    @NotNull
    private Double amount;
}

