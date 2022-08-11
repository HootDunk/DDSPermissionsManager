package io.unityfoundation.dds.permissions.manager;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.authentication.ServerAuthentication;
import io.micronaut.security.utils.SecurityService;
import jakarta.inject.Singleton;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

@Replaces(SecurityService.class)
@Singleton
public class MockSecurityService implements SecurityService {

    private ServerAuthentication serverAuthentication;

    @PostConstruct
    void postConstruct() {
        serverAuthentication = new ServerAuthentication("montesm@test.test", Collections.emptyList(), Map.of("isAdmin", true));
    }

    @Override
    public Optional<String> username() {
        return Optional.empty();
    }

    @Override
    public Optional<Authentication> getAuthentication() {
        return Optional.of(serverAuthentication);
    }

    @Override
    public boolean isAuthenticated() {
        return false;
    }

    @Override
    public boolean hasRole(String role) {
        return false;
    }

    public void setServerAuthentication(ServerAuthentication serverAuthentication) {
        this.serverAuthentication = serverAuthentication;
    }
}
