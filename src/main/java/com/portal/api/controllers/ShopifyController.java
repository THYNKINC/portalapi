package com.portal.api.controllers;

import com.portal.api.model.Order;
import com.portal.api.repositories.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/shopify")
public class ShopifyController {

    private final OrderRepository orderRepository;

    public ShopifyController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @PostMapping("/orders")
    public ResponseEntity<Void> createOrder(@RequestBody String orderAsJson) {
        log.info("Received order: {}", orderAsJson);

        Order order = new Order();
        order.setOrder(orderAsJson);
        orderRepository.save(order);

        return null;
    }
}

