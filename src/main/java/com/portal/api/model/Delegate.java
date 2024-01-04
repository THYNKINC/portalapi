package com.portal.api.model;

import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
@NoArgsConstructor
@Document(collection = "delegate")
public class Delegate  extends PortalUser {
	
	@Builder.Default
	final Role role = Role.delegate;
}
