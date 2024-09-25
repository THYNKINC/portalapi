package com.portal.api.scheduled;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.api.dto.response.ShopifyOrdersResponse;
import com.portal.api.services.ShopifyService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

//@Component
public class ShopifyTasks {

    private final ShopifyService shopifyService;

    public ShopifyTasks(ShopifyService shopifyService) {
        this.shopifyService = shopifyService;
    }

    //@Scheduled(fixedRate = 1800000) // 30 minutes in milliseconds
    //@Scheduled(fixedRate = 150000)
    public void getOrdersPeriodically() {
        ShopifyOrdersResponse shopifyOrdersResponse = shopifyService.getOrders();

        ObjectMapper objectMapper = new ObjectMapper();
        String ordersDataAsJson;
        try {
            ordersDataAsJson = objectMapper.writeValueAsString(shopifyOrdersResponse.getOrders());

            Path filePath = Paths.get("C:/Users/marko/Desktop/orders.json");
            Files.deleteIfExists(filePath);
            Files.createFile(filePath);
            Files.write(filePath, ordersDataAsJson.getBytes());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}