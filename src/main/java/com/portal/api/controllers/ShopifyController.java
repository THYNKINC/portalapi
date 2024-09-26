package com.portal.api.controllers;

import com.portal.api.model.Order;
import com.portal.api.repositories.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/protal/shopify")
public class ShopifyController {

    private static final Logger logger = LoggerFactory.getLogger(ShopifyController.class);

    private final OrderRepository orderRepository;

    public ShopifyController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @PostMapping("/orders")
    public void createOrder(@RequestBody String orderAsJson) {

        logger.info("Shopify order {}", orderAsJson);

        Order order = new Order();
        order.setOrder(orderAsJson);
        orderRepository.save(order);
    }
}

