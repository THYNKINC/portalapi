package com.portal.api.model;

import java.util.List;

import lombok.Data;

@Data
public class ChildSearchResult {
    
	List<Child> paginatedChildren;
    long totalCount;
}