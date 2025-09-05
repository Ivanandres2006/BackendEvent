package com.example.dbeventservice.dto;

import java.util.List;

public class PromoterStatsResponse {
    public String referralCode;
    public int ticketsSold;
    public List<ReferredBuyerDTO> buyers;
}
