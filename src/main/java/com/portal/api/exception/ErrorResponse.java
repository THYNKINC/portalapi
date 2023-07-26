package com.portal.api.exception;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ErrorResponse {
    private int status;

    @JsonProperty("error_message")
    private String errorMessage;

    public ErrorResponse(int status, String errorMessage) {
        this.status = status;
        this.errorMessage = errorMessage;
    }

	public int getStatus() {
		return status;
	}

	public void setStatus(int status) {
		this.status = status;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

    // Getters and setters (or use Lombok for automatic generation)
}