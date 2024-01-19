package com.portal.api.scheduled;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.portal.api.model.Parent;
import com.portal.api.services.ParentService;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

//@Component
public class CreatedDateInitializer {
	
	private static final Logger logger = LoggerFactory.getLogger(CreatedDateInitializer.class);
	
	@Autowired
	private ParentService parents;
	
	@Value("${user-pool-id}")
	private String USER_POOL_ID;
	
	// every day
	@Scheduled(fixedRate = 86400000)
	private void initPlayers() throws Exception {
        
		logger.info("init player's created dates");
			
    	// Create a CognitoIdentityProviderClient
    	CognitoIdentityProviderClient cognitoClient = CognitoIdentityProviderClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

    	cognitoClient
    		.listUsers(b -> b.userPoolId(USER_POOL_ID))
    		.users()
    		.forEach(u -> {
    			
    			String username = u.username();
    			
    			Parent parent = parents.getParent(username);
    			
    			if (parent == null) {
    				logger.warn("Parent not found:" + username);
    				return;
    			}
    			
    			parent.setCreatedDate(Date.from(u.userCreateDate()));
    			
    			logger.info(parent.getEmail());
    			
    			parents.upsertParent(parent);
    		});
    }
}