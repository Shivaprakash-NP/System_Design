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
    public ResponseEntity<Set<String>> getSuggestions(
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "q", required = false) String q) {
        String search = (query != null && !query.trim().isEmpty()) ? query : q;
        if (search == null || search.trim().isEmpty()) {
            return ResponseEntity.ok(Set.of());
        }
        
        Set<String> suggestions = autocompleteService.getSuggestions(search, 10);
        return ResponseEntity.ok(suggestions);
    }

    @PostMapping("/admin/add-suggestion")
    public ResponseEntity<Void> addSuggestion(@RequestParam String term) {
        autocompleteService.addSuggestion(term);
        return ResponseEntity.ok().build();
    }
}