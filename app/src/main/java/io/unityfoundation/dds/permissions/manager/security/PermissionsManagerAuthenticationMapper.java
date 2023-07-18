package io.unityfoundation.dds.permissions.manager.security;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.security.authentication.AuthenticationFailureReason;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.oauth2.endpoint.authorization.state.State;
import io.micronaut.security.oauth2.endpoint.token.response.DefaultOpenIdAuthenticationMapper;
import io.micronaut.security.oauth2.endpoint.token.response.OpenIdAuthenticationMapper;
import io.micronaut.security.oauth2.endpoint.token.response.OpenIdClaims;
import io.micronaut.security.oauth2.endpoint.token.response.OpenIdTokenResponse;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserService;
import io.unityfoundation.dds.permissions.manager.model.user.User;
import io.unityfoundation.dds.permissions.manager.model.user.UserRole;
import io.unityfoundation.dds.permissions.manager.model.user.UserService;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.*;

@Replaces(DefaultOpenIdAuthenticationMapper.class)
@Singleton
@Named("google")
public class PermissionsManagerAuthenticationMapper implements OpenIdAuthenticationMapper {

    private final UserService userService;
    private final GroupUserService groupUserService;

    public PermissionsManagerAuthenticationMapper(UserService userService, GroupUserService groupUserService) {
        this.userService = userService;
        this.groupUserService = groupUserService;
    }

    @Override
    public AuthenticationResponse createAuthenticationResponse(String providerName,
                                                               OpenIdTokenResponse tokenResponse,
                                                               OpenIdClaims openIdClaims,
                                                               State state) {
        return getAuthenticationResponse(openIdClaims.getEmail());
    }

    public AuthenticationResponse getAuthenticationResponse(String userEmail) {
        return Optional.ofNullable(userEmail)
                .flatMap(userService::getUserByEmail)
                .map(user -> isNonAdminAndNotAMemberOfAnyGroups(user) ?
                        AuthenticationResponse.failure(AuthenticationFailureReason.USER_DISABLED) :
                        AuthenticationResponse.success(
                                userEmail,
                                Collections.emptyList(),
                                userAttributes(userEmail, user)
                        ))
                .orElseGet(() -> AuthenticationResponse.failure(AuthenticationFailureReason.USER_NOT_FOUND));
    }

    private boolean isNonAdminAndNotAMemberOfAnyGroups(User user) {
        return !user.isAdmin() && groupUserService.countMembershipsByUserId(user.getId()) == 0;
    }

    private HashMap<String, Object> userAttributes(String userEmail, User user) {
        HashMap<String, Object> attributes = new HashMap<>();
        List<Map<String, Object>> permissions = groupUserService.getAllPermissionsPerGroupUserIsMemberOf(user.getId());
        attributes.put("name", userEmail);
        attributes.put("permissionsByGroup", permissions);
        attributes.put("permissionsLastUpdated", user.getPermissionsLastUpdated());
        return attributes;
    }
}