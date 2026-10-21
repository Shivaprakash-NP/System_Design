package com.hotel.system.controller;

import com.hotel.system.components.autocomplete.AutocompleteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final AutocompleteService autocompleteService;

    @GetMapping("/autocomplete")
    public ResponseEntity<Set<String>> getSuggestions(@RequestParam String q) {
        if (q == null || q.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        Set<String> suggestions = autocompleteService.getSuggestions(q, 10);
        return ResponseEntity.ok(suggestions);
    }

    @PostMapping("/admin/add-suggestion")
    public ResponseEntity<Void> addSuggestion(@RequestParam String term) {
        autocompleteService.addSuggestion(term);
        return ResponseEntity.ok().build();
    }
}