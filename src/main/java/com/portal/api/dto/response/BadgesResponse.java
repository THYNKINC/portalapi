package com.portal.api.dto.response;

import java.util.List;

import com.portal.api.model.Badge;
import lombok.Data;

@Data
public class BadgesResponse {

	private List<Badge> badges;
}