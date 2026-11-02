package com.ems.admin.oss.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDateTime;

public final class OssDtos {
    private OssDtos() {
    }

    public record UploadTicketRequest(
            @NotBlank @Pattern(regexp = "AVATAR|STATION", message = "只能是 AVATAR 或 STATION") String bizType,
            Long bizId,
            @NotBlank @Pattern(regexp = "image/jpeg|image/png|image/webp", message = "只支持 jpeg、png、webp") String contentType,
            @NotNull @Positive(message = "必须大于 0") Long fileSize
    ) {
    }

    public record UploadTicketResponse(
            Long ticketId,
            String objectKey,
            String uploadUrl,
            String method,
            String contentType,
            LocalDateTime expireAt
    ) {
    }

    @Data
    public static class StationImageVO {
        private Long id;
        private String url;
        private Integer cover;
        private Integer sortNum;
        private LocalDateTime createdAt;
    }
}
