package com.example.dbeventservice.dto;

import lombok.*;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PaymentOptionsDTO {
    private String country;
    private String currency; // "USD" or "VES" depending on event/your rules
    private List<String> allowed;    // ["card", ... maybe "pagoMovil", "zelle", "cash"]
    private String zelleEmail;
    private String pagoMovilPhone;
    private String pagoMovilCi;
    private String pagoMovilBank;
    private String cashNote;
}
