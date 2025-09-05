package com.example.dbeventservice.dto;

import com.example.dbeventservice.model.Promoter;

public class PromoterSummaryDTO {
    public Long id;
    public String referralCode;
    public UserSummaryDTO user;

    public static PromoterSummaryDTO from(Promoter p) {
        PromoterSummaryDTO dto = new PromoterSummaryDTO();
        dto.id = p.getId();
        dto.referralCode = p.getReferralCode();
        if (p.getUser() != null) {
            dto.user = UserSummaryDTO.from(p.getUser());
        }
        return dto;
    }
}
