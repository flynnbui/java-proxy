package com.comp3331.proxy.config;

/**
 * Exception thrown for configuration errors.
 */
public class ConfigException extends Exception {
    public ConfigException(String message) {
        super(message);
    }
    
    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}