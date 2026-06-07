package com.dify.workflow.model.node;
import com.alibaba.fastjson2.annotation.JSONField;

/**
 * Comparison operators used in if-else conditions.
 */
public final class ComparisonOperator {

    private ComparisonOperator() {}

    public static final String CONTAINS = "contains";
    public static final String NOT_CONTAINS = "not contains";
    public static final String STARTS_WITH = "starts with";
    public static final String NOT_STARTS_WITH = "not starts with";
    public static final String ENDS_WITH = "ends with";
    public static final String NOT_ENDS_WITH = "not ends with";
    public static final String EQUALS = "equals";
    public static final String NOT_EQUALS = "not equals";
    public static final String GREATER_THAN = "greater than";
    public static final String LESS_THAN = "less than";
    public static final String GREATER_OR_EQUAL = "greater than or equal to";
    public static final String LESS_OR_EQUAL = "less than or equal to";
    public static final String IS_EMPTY = "is empty";
    public static final String IS_NOT_EMPTY = "is not empty";
}
