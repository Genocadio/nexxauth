package com.nexxserve.nauth.service;

import com.nexxserve.nauth.repository.OrganisationClientRepository;
import com.nexxserve.nauth.security.ClientTokens;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assigns an opaque client key to any client created before the key column
 * existed (migration V12 added the column; this backfills existing rows at
 * startup so apps keep working without re-creating clients). Idempotent:
 * clients that already have a key are left untouched.
 */
@Component
public class OrganisationClientKeyBackfill implements ApplicationRunner {

    private final OrganisationClientRepository clientRepository;

    public OrganisationClientKeyBackfill(OrganisationClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        clientRepository.findByClientKeyIsNull().forEach(client -> {
            client.setClientKey(ClientTokens.generateKey());
            clientRepository.save(client);
        });
    }
}