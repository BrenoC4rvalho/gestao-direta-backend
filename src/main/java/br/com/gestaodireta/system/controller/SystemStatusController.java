package br.com.gestaodireta.system.controller;

import br.com.gestaodireta.system.dto.SystemStatusResponse;
import br.com.gestaodireta.system.service.SystemStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/system/status")
public class SystemStatusController {

    private final SystemStatusService systemStatusService;

    public SystemStatusController(SystemStatusService systemStatusService) {
        this.systemStatusService = systemStatusService;
    }

    @GetMapping
    public SystemStatusResponse getStatus() {
        return systemStatusService.getStatus();
    }
}
