package com.dify.workflow.parser;

/**
 * Exception thrown when Dify YAML parsing fails.
 */
public class DifyParseException extends RuntimeException {

    public DifyParseException(String message) {
        super(message);
    }

    public DifyParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
