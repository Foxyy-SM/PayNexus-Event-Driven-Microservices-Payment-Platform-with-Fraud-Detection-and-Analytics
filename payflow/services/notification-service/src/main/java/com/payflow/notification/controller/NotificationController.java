package com.payflow.notification.controller;

import com.payflow.common.security.PayflowPrincipal;
import com.payflow.notification.domain.NotificationRecord;
import com.payflow.notification.service.NotificationDispatcher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationDispatcher dispatcher;

    public NotificationController(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @GetMapping("/me")
    public List<NotificationRecord> mine(@AuthenticationPrincipal PayflowPrincipal principal) {
        return dispatcher.forUser(UUID.fromString(principal.userId()));
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public List<NotificationRecord> byUser(@PathVariable UUID userId) {
        return dispatcher.forUser(userId);
    }
}
