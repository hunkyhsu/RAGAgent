package com.hunkyhsu.ragagent.exception;

import lombok.Getter;

@Getter
public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException(String message) {
        super(message);
    }
}