package br.com.gestaodireta.ai.web.rest;

import br.com.gestaodireta.ai.service.AiTransactionParseService;
import br.com.gestaodireta.ai.service.dto.ParseTransactionTextRequest;
import br.com.gestaodireta.ai.service.dto.ParsedTransactionResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai/transactions")
public class AiTransactionController {

    private final AiTransactionParseService aiTransactionParseService;

    public AiTransactionController(AiTransactionParseService aiTransactionParseService) {
        this.aiTransactionParseService = aiTransactionParseService;
    }

    @PostMapping("/parse")
    @PreAuthorize("@aiAccess.canParseTransactionText(#request)")
    public ParsedTransactionResponse parse(
            @Valid @RequestBody ParseTransactionTextRequest request) {
        return aiTransactionParseService.parse(request);
    }
}
