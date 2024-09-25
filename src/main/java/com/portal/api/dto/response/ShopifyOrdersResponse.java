package com.portal.api.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ShopifyOrdersResponse {
    private List<ShopifyOrder> orders;
}