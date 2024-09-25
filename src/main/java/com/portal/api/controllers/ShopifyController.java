package com.portal.api.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.api.dto.response.ShopifyOrder;
import com.portal.api.model.Order;
import com.portal.api.repositories.OrderRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/shopify")
public class ShopifyController {

    private final OrderRepository orderRepository;

    public ShopifyController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @PostMapping("/orders")
    public ResponseEntity<Void> createOrder(@RequestBody ShopifyOrder orderAsJson) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        String ordersDataAsJson = objectMapper.writeValueAsString(orderAsJson);

        Order order = new Order();
        order.setOrder(ordersDataAsJson);
        orderRepository.save(order);

        return null;
    }
}

