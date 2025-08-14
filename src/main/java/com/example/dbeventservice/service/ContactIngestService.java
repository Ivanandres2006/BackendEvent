package com.example.dbeventservice.service;

import com.example.dbeventservice.model.Enum.ContactType;
import com.example.dbeventservice.model.User;
import com.example.dbeventservice.model.UserContact;
import com.example.dbeventservice.model.UserIdentity;
import com.example.dbeventservice.repositories.UserContactRepository;
import com.example.dbeventservice.repositories.UserIdentityRepository;
import com.example.dbeventservice.repositories.UserRepository;
import com.example.dbeventservice.util.ContactHashUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service @RequiredArgsConstructor
public class ContactIngestService {
    private final UserRepository userRepo;
    private final UserContactRepository contactRepo;
    private final UserIdentityRepository identityRepo;
    private final ContactHashUtil hashUtil;

    @Transactional
    public void upsertIdentities(Long userId, List<String> emails, List<String> phones) {
        User me = userRepo.findById(userId).orElseThrow();
        for (String e : emails) {
            String h = hashUtil.hashEmail(e);
            // unique by (type, hash): rely on constraint; if exists, skip
            if (identityRepo.findByTypeAndValueHashIn(com.example.dbeventservice.model.Enum.ContactType.EMAIL, List.of(h)).isEmpty()) {
                identityRepo.save(UserIdentity.builder().user(me).type(ContactType.EMAIL).valueHash(h).build());
            }
        }
        for (String p : phones) {
            String h = hashUtil.hashPhone(p);
            if (identityRepo.findByTypeAndValueHashIn(ContactType.PHONE, List.of(h)).isEmpty()) {
                identityRepo.save(UserIdentity.builder().user(me).type(ContactType.PHONE).valueHash(h).build());
            }
        }
    }

    @Transactional
    public void importContacts(Long userId, List<String> emails, List<String> phones, boolean areHashed) {
        User me = userRepo.findById(userId).orElseThrow();
        for (String e : emails) {
            String h = areHashed ? e : hashUtil.hashEmail(e);
            contactRepo.save(UserContact.builder().owner(me).type(ContactType.EMAIL).valueHash(h).build());
        }
        for (String p : phones) {
            String h = areHashed ? p : hashUtil.hashPhone(p);
            contactRepo.save(UserContact.builder().owner(me).type(ContactType.PHONE).valueHash(h).build());
        }
    }
}
