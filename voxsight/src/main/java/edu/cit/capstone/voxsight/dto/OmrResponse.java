package edu.cit.capstone.voxsight.dto;

public record OmrResponse(boolean success, String url, String filename, String error) {
    public static OmrResponse ofSuccess(String url, String filename) {
        return new OmrResponse(true, url, filename, null);
    }

    public static OmrResponse ofError(String error) {
        return new OmrResponse(false, null, null, error);
    }
}
