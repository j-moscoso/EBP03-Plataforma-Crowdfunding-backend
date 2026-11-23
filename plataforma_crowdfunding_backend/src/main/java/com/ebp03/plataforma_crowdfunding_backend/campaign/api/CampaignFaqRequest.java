package com.ebp03.plataforma_crowdfunding_backend.campaign.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class CampaignFaqRequest {
    private Boolean faq;

    @JsonProperty("isFaq")
    public Boolean getFaq() { return faq; }
    @JsonProperty("isFaq")
    public void setFaq(Boolean faq) { this.faq = faq; }

    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new UnknownDiscussionFieldException(field);
    }

    public static final class UnknownDiscussionFieldException extends IllegalArgumentException {
        private final String field;
        public UnknownDiscussionFieldException(String field) { super("Unsupported FAQ field"); this.field = field; }
        public String getField() { return field; }
    }
}
