// dbeventservice/dto/ResaleAnalyticsDTO.java
package com.example.dbeventservice.dto;

import com.example.dbeventservice.model.ResalePost;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ResaleAnalyticsDTO {
    private Long postId;
    private String title;
    private Double price;
    private boolean sold;
    private String createdAt;
    private String soldAt;
    private long views;
    private long chatOpens;
    private Long timeToSellSeconds;

    public static ResaleAnalyticsDTO from(ResalePost p) {
        Long tts = (p.getSoldAt()!=null && p.getCreatedAt()!=null)
                ? java.time.Duration.between(p.getCreatedAt(), p.getSoldAt()).getSeconds()
                : null;
        return ResaleAnalyticsDTO.builder()
                .postId(p.getId())
                .title(p.getTitle())
                .price(p.getPrice())
                .sold(p.isSold())
                .createdAt(p.getCreatedAt()!=null ? p.getCreatedAt().toString() : null)
                .soldAt(p.getSoldAt()!=null ? p.getSoldAt().toString() : null)
                .views(p.getViewsCount())
                .chatOpens(p.getChatOpenCount())
                .timeToSellSeconds(tts)
                .build();
    }
}
