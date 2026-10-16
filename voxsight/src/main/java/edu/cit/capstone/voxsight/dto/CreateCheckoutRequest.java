package edu.cit.capstone.voxsight.dto;

public class CreateCheckoutRequest {
    private String username;
    private String email;
    private String planName;
    private Integer amountCentavos;
    private String successUrl;
    private String cancelUrl;

    public CreateCheckoutRequest() {
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPlanName() {
        return planName;
    }

    public void setPlanName(String planName) {
        this.planName = planName;
    }

    public Integer getAmountCentavos() {
        return amountCentavos;
    }

    public void setAmountCentavos(Integer amountCentavos) {
        this.amountCentavos = amountCentavos;
    }

    public String getSuccessUrl() {
        return successUrl;
    }

    public void setSuccessUrl(String successUrl) {
        this.successUrl = successUrl;
    }

    public String getCancelUrl() {
        return cancelUrl;
    }

    public void setCancelUrl(String cancelUrl) {
        this.cancelUrl = cancelUrl;
    }
}

