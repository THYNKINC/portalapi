package com.portal.api.model;

import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PaginatedResponse<T> {

	List<T> content;
	long total;
	
	public PaginatedResponse(List<T> content, long total) {
		super();
		this.content = content;
		this.total = total;
	}
	
	
}
