package com.portal.api.util;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.TypedAggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import com.portal.api.model.Child;
import com.portal.api.model.ChildSearchResult;
import com.portal.api.model.PaginatedResponse;
import com.portal.api.model.Parent;
import com.portal.api.repositories.ParentRepository;


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

	public Page<Parent> getParentsByNameStartingWith(String partialName, Pageable pageable) {
        return parentRepository.findByFirstNameIgnoreCaseStartingWithOrLastNameIgnoreCaseStartingWith(partialName, partialName, pageable);
    }
	
	public Page<Parent> getAllParents(String partialName, Pageable pageable) {
        return parentRepository.findAll(pageable);
    }
	
	public PaginatedResponse<Child> getChildrenByNameStartingWith(String nameStartingWith, Pageable page) {
        
		Criteria usernameCriteria = Criteria.where("username").regex("^" + nameStartingWith, "i");
        
		TypedAggregation<Parent> aggregation = Aggregation.newAggregation(Parent.class, 
				Aggregation.unwind("children"),
                Aggregation.replaceRoot("children"),
                Aggregation.match(usernameCriteria),
                Aggregation.facet(
                		Aggregation.limit(page.getPageSize()),
                		Aggregation.skip((long)(page.getPageNumber()) * page.getPageSize())
            	).as("paginatedChildren")
                .and(
                	Aggregation.count().as("count")
            	).as("totalCount"),
                Aggregation
                	.project("paginatedChildren")
                	.and("$totalCount.count")
                	.arrayElementAt(0)
                	.as("totalCount")
         );
		
		AggregationResults<ChildSearchResult> result = mongoTemplate.aggregate(aggregation, ChildSearchResult.class);
		
		return new PaginatedResponse<Child>(result.getUniqueMappedResult().getPaginatedChildren(),result.getUniqueMappedResult().getTotalCount());
    }
	
	public PaginatedResponse<Child> getAllChildren(Pageable page) {
		
		//Criteria usernameCriteria = Criteria.where("username").regex("^" + usernameFilter, "i");
        
		TypedAggregation<Parent> aggregation = Aggregation.newAggregation(Parent.class, 
				Aggregation.unwind("children"),
                Aggregation.replaceRoot("children"),
                Aggregation.facet(
                		Aggregation.limit(page.getPageSize()),
                		Aggregation.skip((long)(page.getPageNumber()) * page.getPageSize())
            	).as("paginatedChildren")
                .and(
                	Aggregation.count().as("count")
            	).as("totalCount"),
                Aggregation
                	.project("paginatedChildren")
                	.and("$totalCount.count")
                	.arrayElementAt(0)
                	.as("totalCount")
         );
		
		AggregationResults<ChildSearchResult> result = mongoTemplate.aggregate(aggregation, ChildSearchResult.class);
		
		return new PaginatedResponse<Child>(result.getUniqueMappedResult().getPaginatedChildren(),result.getUniqueMappedResult().getTotalCount());
    }
}
