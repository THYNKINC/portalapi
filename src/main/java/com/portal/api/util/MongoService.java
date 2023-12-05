package com.portal.api.util;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.LimitOperation;
import org.springframework.data.mongodb.core.aggregation.SkipOperation;
import org.springframework.data.mongodb.core.aggregation.TypedAggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
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
	
	public Parent getParentByChildName(String username) {
		return parentRepository.findOneByChildrenUsername(username);
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
	
	public PaginatedResponse<Child> getChildrenByNameStartingWith(String nameStartingWith, Pageable pageable) {
        
		Criteria matchCriteria = Criteria.where("username").regex("^" + nameStartingWith);

		// Aggregation for counting
		AggregationOperation replaceRootOperation = Aggregation.replaceRoot().withValueOf("$children");
		AggregationOperation unwindOperation = Aggregation.unwind("children");
		AggregationOperation matchOperation = Aggregation.match(matchCriteria);
        AggregationOperation countOperation = Aggregation.count().as("total");
        Aggregation countAggregation = Aggregation.newAggregation(unwindOperation, replaceRootOperation, matchOperation, countOperation);

        AggregationResults<CountDTO> countResults = mongoTemplate.aggregate(countAggregation, "parent", CountDTO.class);
        long total = countResults.getUniqueMappedResult().getTotal();

        // Aggregation for paginated result
        AggregationOperation skipOperation = Aggregation.skip(pageable.getOffset());
        AggregationOperation limitOperation = Aggregation.limit(pageable.getPageSize());
        
        Aggregation aggregation = Aggregation.newAggregation(
        		unwindOperation,
                replaceRootOperation,
        		matchOperation,
        		skipOperation,
                limitOperation
        );

        List<Child> paginatedChildren = mongoTemplate.aggregate(aggregation, "parent", Child.class).getMappedResults();

		return new PaginatedResponse<Child>(paginatedChildren, total);
    }
	
	public List<Child> getChildrenByUsername(Collection<String> usernames) {
        
		Criteria usernameCriteria = Criteria.where("username").in(usernames);
        
		TypedAggregation<Parent> aggregation = Aggregation.newAggregation(Parent.class, 
				Aggregation.unwind("children"),
                Aggregation.replaceRoot("children"),
                Aggregation.match(usernameCriteria)
         );
		
		AggregationResults<Child> result = mongoTemplate
				.aggregate(aggregation, Child.class);
		
		return result.getMappedResults();
    }
	
	public PaginatedResponse<Child> getAllChildren(Pageable pageable) {
		
        // Aggregation for counting
		AggregationOperation unwindOperation = Aggregation.unwind("children");
        AggregationOperation groupCountOperation = Aggregation.group().count().as("total");
        Aggregation countAggregation = Aggregation.newAggregation(unwindOperation, groupCountOperation);

        AggregationResults<CountDTO> countResults = mongoTemplate.aggregate(countAggregation, "parent", CountDTO.class);
        long total = countResults.getUniqueMappedResult().getTotal();

        // Aggregation for paginated result
        AggregationOperation skipOperation = Aggregation.skip(pageable.getOffset());
        AggregationOperation limitOperation = Aggregation.limit(pageable.getPageSize());

        AggregationOperation replaceRootOperation = Aggregation.replaceRoot().withValueOf("$children");
        
        Aggregation aggregation = Aggregation.newAggregation(
                unwindOperation,
                skipOperation,
                limitOperation,
                replaceRootOperation
        );

        List<Child> paginatedChildren = mongoTemplate.aggregate(aggregation, "parent", Child.class).getMappedResults();

		return new PaginatedResponse<Child>(paginatedChildren, total);
    }

	public void updateChild(Child child) {
		
		Parent parent = getParentByChildName(child.getUsername());
		
		List<Child> children = parent.getChildren().stream()
				.map(e -> e.getUsername().equals(child.getUsername()) ? child : e)
				.collect(Collectors.toList());
		
		parent.setChildren(children);
		upsertParent(parent);
	}
}
