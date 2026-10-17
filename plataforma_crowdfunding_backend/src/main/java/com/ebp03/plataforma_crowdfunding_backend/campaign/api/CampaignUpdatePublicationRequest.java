package com.ebp03.plataforma_crowdfunding_backend.campaign.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

public final class CampaignUpdatePublicationRequest {
    @NotBlank(message = "El título es obligatorio.")
    @Size(max = 140, message = "El título no puede superar 140 caracteres.")
    private String title;

    @NotBlank(message = "El contenido es obligatorio.")
    @Size(max = 50000, message = "El contenido supera el límite permitido.")
    private String body;

    @Size(max = 10, message = "No se permiten más de 10 medios.")
    @Valid
    private List<CampaignUpdateMediaRequest> media = new ArrayList<>();

    public String getTitle() { return title; }
    public String getBody() { return body; }
    public List<CampaignUpdateMediaRequest> getMedia() { return media; }

    public void setTitle(String title) { this.title = title == null ? null : title.strip(); }
    public void setBody(String body) { this.body = body == null ? null : body.strip(); }
    public void setMedia(List<CampaignUpdateMediaRequest> media) { this.media = media == null ? new ArrayList<>() : media; }

    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new UnknownCampaignUpdateFieldException(field);
    }

    public static final class UnknownCampaignUpdateFieldException extends IllegalArgumentException {
        private final String field;

        public UnknownCampaignUpdateFieldException(String field) {
            super("Unsupported campaign update field");
            this.field = field;
        }

        public String getField() { return field; }
    }
}
