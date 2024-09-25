package com.portal.api.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;

@Getter
@Setter
public class ShopifyOrder {
    private long id;
    private ZonedDateTime createdAt;
    private String name;
    private String totalPrice;
}
