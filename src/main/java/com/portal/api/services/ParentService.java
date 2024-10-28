package com.portal.api.services;

import com.portal.api.dto.request.CreateChildRequest;
import com.portal.api.dto.request.CreateParentRequest;
import com.portal.api.dto.response.PaginatedResponse;
import com.portal.api.model.Child;
import com.portal.api.model.Parent;
import com.portal.api.repositories.ParentRepository;
import com.portal.api.util.CountDTO;
import com.portal.api.util.DateTimeUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.TypedAggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpResponse;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Service
public class ParentService {

	static final String CUSTOM_PATTERN = "MM-dd-yyyy";

	private final MongoTemplate mongoTemplate;
	
	private final ParentRepository parentRepository;

	private final AuthService authService;

    @Autowired
    public ParentService(MongoTemplate mongoTemplate, ParentRepository parentRepository, AuthService authService) {
        this.mongoTemplate = mongoTemplate;
        this.parentRepository = parentRepository;
        this.authService = authService;
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

	public void createParent(CreateParentRequest createParentRequest) {

		SignUpResponse signUpResponse = authService.registerUser(createParentRequest);

		Parent parent = new Parent();
		parent.setCreatedDate(new Date());
		parent.setChildren(new ArrayList<>());
		parent.setEmail(createParentRequest.getEmail());
		parent.setFirstName(createParentRequest.getFirstName());
		parent.setLastName(createParentRequest.getLastName());
		parent.setUsername(signUpResponse.userSub());
		parent.setSalutation(createParentRequest.getSalutation());

		upsertParent(parent);
	}

	public Parent getParent(String username) {
		return parentRepository.findById(username).orElse(null);
	}
	
	public Parent getParentByChildName(String username) {
		return parentRepository.findOneByChildrenUsername(username);
	}
	
	public Parent upsertParent(Parent parent) {
		return parentRepository.save(parent);
	}

	public void addChildToParent(CreateChildRequest createChildRequest,  String username) {
		Child child = new Child();
		child.setFirstName(createChildRequest.getFirstName());
		child.setLastName(createChildRequest.getLastName());
		child.setUsername(createChildRequest.getUsername());

		if (createChildRequest.getDob() != null && !createChildRequest.getDob().isEmpty()) {
			LocalDate dob = DateTimeUtil.isValidLocalDate(createChildRequest.getDob(), CUSTOM_PATTERN);
			child.setDob(dob.format(DateTimeFormatter.ofPattern(CUSTOM_PATTERN)));
		} else {
			child.setDob("");
		}

		child.setCreatedDate(new Date());

		updateParent(username, child);
	}
	
	public void updateParent(String username, Child child) {
		Parent parent = getParent(username);
		List<Child> children = parent.getChildren();
		children.add(child);
		parent.setChildren(children);
		upsertParent(parent);
	}

	public Page<Parent> getParentsByNameStartingWith(String partialName, Pageable pageable) {
        return parentRepository.findByFirstNameIgnoreCaseStartingWithOrLastNameIgnoreCaseStartingWith(partialName, partialName, pageable);
    }
	
	public Page<Parent> getAllParents(String partialName, Pageable pageable) {
        return parentRepository.findAll(pageable);
    }
	
	public PaginatedResponse<Child> getChildrenByFilter(String nameStartingWith, Map<String, String> labels, Pageable pageable) {
        
		Criteria matchCriteria = new Criteria();
		
		if (nameStartingWith != null) {
			
			matchCriteria.orOperator(
				Criteria.where("username")
				.regex("^" + nameStartingWith, "i"),
				Criteria.where("firstName")
				.regex("^" + nameStartingWith, "i"),
				Criteria.where("lastName")
				.regex("^" + nameStartingWith, "i"));
		}
		
		if (labels != null) {
			
			labels.forEach((key, value) -> {
				
				if (key.startsWith("l_"))
					matchCriteria
						.and("labels." + key.substring(2))
						.is(value);
			});
		}

		// Aggregation for counting
		AggregationOperation replaceRootOperation = Aggregation.replaceRoot().withValueOf("$children");
		AggregationOperation unwindOperation = Aggregation.unwind("children");
		AggregationOperation matchOperation = Aggregation.match(matchCriteria);
        AggregationOperation countOperation = Aggregation.count().as("total");
        Aggregation countAggregation = Aggregation.newAggregation(unwindOperation, replaceRootOperation, matchOperation, countOperation);

        AggregationResults<CountDTO> countResults = mongoTemplate.aggregate(countAggregation, "delegate", CountDTO.class);
        CountDTO results = countResults.getUniqueMappedResult();
        
        if (results == null)
        	return new PaginatedResponse<>();
        
        long total = results.getTotal();

        // Aggregation for paginated result
        AggregationOperation skipOperation = Aggregation.skip(pageable.getOffset());
        AggregationOperation limitOperation = Aggregation.limit(pageable.getPageSize());
        
        AggregationOperation sortOperation = Aggregation.sort(Sort.by(Direction.DESC, "createdDate"));
        
        Aggregation aggregation = Aggregation.newAggregation(
        		unwindOperation,
                replaceRootOperation,
        		matchOperation,
        		sortOperation,
        		skipOperation,
                limitOperation
        );

        //List<Child> paginatedChildren = mongoTemplate.aggregate(aggregation, "parent", Child.class).getMappedResults();
        List<Child> paginatedChildrenFromCoaches = mongoTemplate.aggregate(aggregation, "delegate", Child.class).getMappedResults();

		return new PaginatedResponse<Child>(paginatedChildrenFromCoaches, total);
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
		AggregationOperation replaceRootOperation = Aggregation.replaceRoot().withValueOf("$children");
		AggregationOperation unwindOperation = Aggregation.unwind("children");
		AggregationOperation countOperation = Aggregation.count().as("total");
        Aggregation countAggregation = Aggregation.newAggregation(unwindOperation, replaceRootOperation, countOperation);

        AggregationResults<CountDTO> countResults = mongoTemplate.aggregate(countAggregation, "parent", CountDTO.class);
        CountDTO results = countResults.getUniqueMappedResult();
        
        if (results == null)
        	return new PaginatedResponse<>();
        
        long total = results.getTotal();

        AggregationOperation sortOperation = Aggregation.sort(Sort.by(Direction.DESC, "createdDate"));
        
        // Aggregation for paginated result
        AggregationOperation skipOperation = Aggregation.skip(pageable.getOffset());
        AggregationOperation limitOperation = Aggregation.limit(pageable.getPageSize());
        
        Aggregation aggregation = Aggregation.newAggregation(
        		unwindOperation,
                replaceRootOperation,
        		sortOperation,
        		skipOperation,
                limitOperation
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
