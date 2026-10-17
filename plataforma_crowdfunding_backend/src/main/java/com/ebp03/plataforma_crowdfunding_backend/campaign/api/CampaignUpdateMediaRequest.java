package com.ebp03.plataforma_crowdfunding_backend.campaign.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class CampaignUpdateMediaRequest {
    @NotBlank(message = "El tipo de medio es obligatorio.")
    @Pattern(regexp = "IMAGE|VIDEO", message = "El tipo de medio debe ser IMAGE o VIDEO.")
    private String mediaType;

    @NotBlank(message = "La URL del medio es obligatoria.")
    @Size(max = 1000, message = "La URL no puede superar 1000 caracteres.")
    @ValidExternalMediaUrl
    private String url;

    @Min(value = 0, message = "El orden debe ser un entero no negativo.")
    private Integer sortOrder;

    public String getMediaType() { return mediaType; }
    public String getUrl() { return url; }
    public Integer getSortOrder() { return sortOrder; }

    public void setMediaType(String mediaType) { this.mediaType = mediaType; }
    public void setUrl(String url) { this.url = url; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new CampaignUpdatePublicationRequest.UnknownCampaignUpdateFieldException("media." + field);
    }
}
