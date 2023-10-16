package com.portal.api.repositories;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.portal.api.model.Headset;

public interface HeadsetRepository extends MongoRepository<Headset, String> {
}