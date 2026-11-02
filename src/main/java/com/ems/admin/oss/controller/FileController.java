package com.ems.admin.oss.controller;

import com.ems.admin.common.api.R;
import com.ems.admin.oss.dto.OssDtos;
import com.ems.admin.oss.service.UploadTicketService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {
    private final UploadTicketService uploadTicketService;

    public FileController(UploadTicketService uploadTicketService) {
        this.uploadTicketService = uploadTicketService;
    }

    @PostMapping("/upload-tickets")
    public R<OssDtos.UploadTicketResponse> issue(@Valid @RequestBody OssDtos.UploadTicketRequest request) {
        return R.ok(uploadTicketService.issue(request));
    }

    @PostMapping("/upload-tickets/{id}/confirm")
    public R<Void> confirm(@PathVariable Long id) {
        uploadTicketService.confirm(id);
        return R.ok();
    }
}
