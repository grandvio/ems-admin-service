package com.ems.admin.oss.controller;

import com.ems.admin.common.api.R;
import com.ems.admin.oss.service.UploadTicketService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class MeController {
    private final UploadTicketService uploadTicketService;

    public MeController(UploadTicketService uploadTicketService) {
        this.uploadTicketService = uploadTicketService;
    }

    @DeleteMapping("/avatar")
    public R<Void> deleteAvatar() {
        uploadTicketService.deleteAvatar();
        return R.ok();
    }
}
