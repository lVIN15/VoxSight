package edu.cit.capstone.voxsight.dto.auth;

public class SimpleMessageResponse {
    private boolean success;
    private String message;

    public SimpleMessageResponse() {}

    public SimpleMessageResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static SimpleMessageResponse ok(String message) {
        return new SimpleMessageResponse(true, message);
    }

    public static SimpleMessageResponse error(String message) {
        return new SimpleMessageResponse(false, message);
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
