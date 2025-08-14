package com.example.dbeventservice.controller;

import com.example.dbeventservice.auth.jwt.UserDetailsImpl;
import com.example.dbeventservice.dto.ContactsPayload;
import com.example.dbeventservice.dto.IdentitiesPayload;
import com.example.dbeventservice.dto.SuggestionDTO;
import com.example.dbeventservice.service.ContactIngestService;
import com.example.dbeventservice.service.SuggestionService;
import com.example.dbeventservice.util.ContactHashUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/suggest")
@RequiredArgsConstructor
public class SuggestionController {
    private final ContactIngestService ingestService;
    private final SuggestionService suggestionService;
    private final ContactHashUtil hashUtil;

    // User publishes their own discovery handles (hashed & indexed)
    @PostMapping("/me/identities")
    public ResponseEntity<?> upsertIdentities(@AuthenticationPrincipal UserDetailsImpl me,
                                              @RequestBody IdentitiesPayload body) {
        var emails = body.getEmails() == null ? List.<String>of() : body.getEmails();
        var phones = body.getPhones() == null ? List.<String>of() : body.getPhones();
        ingestService.upsertIdentities(me.getId(), emails, phones);
        return ResponseEntity.ok().build();
    }

    // User uploads contacts (either plaintext or pre-hashed with same method)
    @PostMapping("/me/contacts")
    public ResponseEntity<?> importContacts(@AuthenticationPrincipal UserDetailsImpl me,
                                            @RequestBody ContactsPayload body) {
        var emails = body.getEmails() == null ? List.<String>of() : body.getEmails();
        var phones = body.getPhones() == null ? List.<String>of() : body.getPhones();
        ingestService.importContacts(me.getId(), emails, phones, body.isHashed());
        return ResponseEntity.ok().build();
    }

    // Get suggestions (contacts + mutuals), server hashes plaintext if needed
    @PostMapping("/me")
    public List<SuggestionDTO> suggest(@AuthenticationPrincipal UserDetailsImpl me,
                                       @RequestBody ContactsPayload body,
                                       @RequestParam(defaultValue = "20") int limit) {
        List<String> emailHashes = new ArrayList<>();
        List<String> phoneHashes = new ArrayList<>();
        var emails = body.getEmails() == null ? List.<String>of() : body.getEmails();
        var phones = body.getPhones() == null ? List.<String>of() : body.getPhones();

        if (body.isHashed()) {
            emailHashes = emails; phoneHashes = phones;
        } else {
            for (var e : emails) emailHashes.add(hashUtil.hashEmail(e));
            for (var p : phones) phoneHashes.add(hashUtil.hashPhone(p));
        }
        return suggestionService.suggestions(me.getId(), emailHashes, phoneHashes, limit);
    }
}
