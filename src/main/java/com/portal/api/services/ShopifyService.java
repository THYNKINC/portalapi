package com.portal.api.services;

import com.portal.api.dto.response.ShopifyOrdersResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class ShopifyService {

    private final RestTemplate shopifyRestClient;

    public ShopifyService(RestTemplate shopifyRestClient) {
        this.shopifyRestClient = shopifyRestClient;
    }

    public ShopifyOrdersResponse getOrders() {
        try {
            String path = "/orders.json?fields=created_at,id,name,total-price";
            return shopifyRestClient.getForEntity(path, ShopifyOrdersResponse.class).getBody();
        } catch (RestClientException e) {
            // log somewhere
            return new ShopifyOrdersResponse();
        }
    }
}