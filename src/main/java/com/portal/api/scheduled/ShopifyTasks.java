package com.portal.api.scheduled;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.api.dto.response.ShopifyOrdersResponse;
import com.portal.api.services.ShopifyService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class ShopifyTasks {

    private final ShopifyService shopifyService;

    public ShopifyTasks(ShopifyService shopifyService) {
        this.shopifyService = shopifyService;
    }

    //@Scheduled(fixedRate = 1800000) // 30 minutes in milliseconds
    @Scheduled(fixedRate = 150000)
    public void getOrdersPeriodically() {
        ShopifyOrdersResponse shopifyOrdersResponse = shopifyService.getOrders();

        // Convert the response into a JSON string
        ObjectMapper objectMapper = new ObjectMapper();
        String ordersDataAsJson;
        try {
            ordersDataAsJson = objectMapper.writeValueAsString(shopifyOrdersResponse.getOrders());

            // Writing data into a file
            writeDataAsJsonToFile(ordersDataAsJson);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void writeDataAsJsonToFile(String ordersDataAsJson) throws IOException {
        // Obtain a file on the Desktop
        Path filePath = Paths.get("C:/Users/marko/Desktop/orders.json");

        // Delete the file if it already exists
        Files.deleteIfExists(filePath);

        // Create a new file
        Files.createFile(filePath);

        // Write the JSON string to the file
        Files.write(filePath, ordersDataAsJson.getBytes());
    }
}