package com.portal.api.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import com.portal.api.model.Child;
import com.portal.api.model.CreateChildRequest;
import com.portal.api.model.Parent;
import com.portal.api.repositories.ParentRepository;

import java.lang.reflect.Field;
import java.util.List;


@Service
public class MongoService {
	
	private final MongoTemplate mongoTemplate;
	
	private final ParentRepository parentRepository;

    @Autowired
    public MongoService(MongoTemplate mongoTemplate, ParentRepository parentRepository) {
        this.mongoTemplate = mongoTemplate;
        this.parentRepository = parentRepository;
    }

    /*
	public void upsertGameState(String username, GameState gameState) {
	    Query query = new Query(Criteria.where("username").is(username));

	    Update update = new Update();
	    Field[] fields = GameState.class.getDeclaredFields();
	    for (Field field : fields) {
	        if (field.getDeclaringClass().equals(GameState.class)) {
	            field.setAccessible(true);
	            try {
	                Object value = field.get(gameState);
	                if (value != null) {
	                    update.set(field.getName(), value);
	                }
	            } catch (IllegalAccessException e) {
	                // Handle the exception
	            }
	        }
	    }
	    mongoTemplate.upsert(query, update, GameState.class, "gamestate");
	}
	*/
	
	public Parent getParent(String username) {
		return parentRepository.findById(username).orElse(null);
	}
	
	public Parent upsertParent(Parent parent) {
		return parentRepository.save(parent);
	}
	
	public Parent updateParent(String username, Child child) {
		Parent parent = getParent(username);
		List<Child> children = parent.getChildren();
		children.add(child);
		parent.setChildren(children);
		return upsertParent(parent);
	}
	
}
