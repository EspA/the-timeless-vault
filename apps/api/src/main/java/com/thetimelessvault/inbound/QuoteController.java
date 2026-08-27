package com.thetimelessvault.inbound;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/quotes")
public class QuoteController {

    private final QuoteService quotes;

    public QuoteController(QuoteService quotes) {
        this.quotes = quotes;
    }

    @GetMapping
    public QuoteDtos.QuotePage list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return quotes.page(page, size);
    }

    @GetMapping("/{id}")
    public QuoteDtos.QuoteView get(@PathVariable UUID id) {
        return quotes.view(id);
    }

    @PostMapping
    public QuoteDtos.QuoteView create(@RequestBody QuoteService.UpsertRequest request) {
        return quotes.createView(request);
    }

    @PutMapping("/{id}")
    public QuoteDtos.QuoteView update(
            @PathVariable UUID id,
            @RequestBody QuoteService.UpsertRequest request
    ) {
        return quotes.updateView(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        quotes.delete(id);
    }

    @PostMapping("/{id}/purchase-order")
    public QuoteDtos.QuoteView convert(
            @PathVariable UUID id,
            @RequestBody QuoteService.ConvertRequest request
    ) {
        return quotes.convertToPurchaseOrder(id, request);
    }
}
