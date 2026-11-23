package com.ebp03.plataforma_crowdfunding_backend.campaign.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;

public final class CampaignDiscussionRequest {
    private String body;

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new UnknownDiscussionFieldException(field);
    }

    public static final class UnknownDiscussionFieldException extends IllegalArgumentException {
        private final String field;
        public UnknownDiscussionFieldException(String field) { super("Unsupported discussion field"); this.field = field; }
        public String getField() { return field; }
    }
}
