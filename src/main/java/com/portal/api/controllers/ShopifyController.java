package com.portal.api.controllers;

import com.portal.api.model.Order;
import com.portal.api.repositories.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/shopify")
public class ShopifyController {

    private static final Logger logger = LoggerFactory.getLogger(ShopifyController.class);

    @Value("${shopify.webhook.signature}")
    private String shopifySignature;

    private final OrderRepository orderRepository;

    public ShopifyController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @PostMapping("/orders")
    public void createOrder(@RequestBody String orderAsJson, @RequestHeader("X-Shopify-Hmac-Sha256") String shopifySignatureHeader) {
        logger.info("Shopify order {}", orderAsJson);
        logger.info("Shopify X-Shopify-Hmac-Sha256 {}", shopifySignatureHeader);

        if (!shopifySignature.equals(shopifySignatureHeader)) {
            return;
        }

        Order order = new Order();
        order.setOrder(orderAsJson);
        orderRepository.save(order);
    }
}

