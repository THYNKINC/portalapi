package com.portal.api.util;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class MappingService {
	
	private static final int MAX_KEY = 53;
    private static Map<String, String> patternMap;

    static {
        patternMap = new HashMap<>();
        int tens = 1;
        int ones = 1;
        for (int key = 1; key <= MAX_KEY; key++) {
            String value = tens + "." + ones;
            patternMap.put(String.valueOf(key), value);
            
            ones++;
            if (ones > 3) {
                tens++;
                ones = 1;
            }
        }
    }

    public static String getValue(String key) {
        return patternMap.get(key);
    }

    public static String getKey(String value) {
        for (Map.Entry<String, String> entry : patternMap.entrySet()) {
            if (entry.getValue().equals(value)) {
                return entry.getKey();
            }
        }
        return null; // Key not found for the given value
    }
    
}
