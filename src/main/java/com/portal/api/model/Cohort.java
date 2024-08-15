package com.portal.api.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Cohort {

    private String id;

    private String name;

    private String description;

    private List<Child> children;

    public void addChild(Child child) {
        if (children == null) {
            children = new ArrayList<Child>();
        }
        children.add(child);
    }

    public void removeChild(Child child) {
        if (children != null) {
            children.remove(child);
        }
    }

    public void removeChildren() {
        if (children != null) {
            children.clear();
        }
    }
}
