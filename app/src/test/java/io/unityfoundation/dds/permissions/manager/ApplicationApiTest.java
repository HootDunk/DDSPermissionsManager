package io.unityfoundation.dds.permissions.manager;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.model.Page;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.security.authentication.ServerAuthentication;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.unityfoundation.dds.permissions.manager.model.application.ApplicationDTO;
import io.unityfoundation.dds.permissions.manager.model.applicationpermission.AccessPermissionDTO;
import io.unityfoundation.dds.permissions.manager.model.applicationpermission.AccessType;
import io.unityfoundation.dds.permissions.manager.model.group.Group;
import io.unityfoundation.dds.permissions.manager.model.group.GroupRepository;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserDTO;
import io.unityfoundation.dds.permissions.manager.model.topic.TopicDTO;
import io.unityfoundation.dds.permissions.manager.model.topic.TopicKind;
import io.unityfoundation.dds.permissions.manager.model.user.User;
import io.unityfoundation.dds.permissions.manager.model.user.UserRepository;
import io.unityfoundation.dds.permissions.manager.model.user.UserRole;
import io.unityfoundation.dds.permissions.manager.testing.util.DbCleanup;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;

import java.text.Collator;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.micronaut.http.HttpStatus.*;
import static io.unityfoundation.dds.permissions.manager.model.application.ApplicationService.E_TAG_HEADER_NAME;
import static org.junit.jupiter.api.Assertions.*;

@MicronautTest(environments={"app-api-test-data"})
@Property(name = "micronaut.http.client.follow-redirects", value = StringUtils.FALSE)
public class ApplicationApiTest {

    private BlockingHttpClient blockingClient;

    @Inject
    MockSecurityService mockSecurityService;

    @Inject
    MockAuthenticationFetcher mockAuthenticationFetcher;

    @Inject
    MockApplicationSecretsClient mockApplicationSecretsClient;

    @Inject
    GroupRepository groupRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    DbCleanup dbCleanup;

    @Inject
    @Client("/api")
    HttpClient client;

    @BeforeEach
    void setup() {
        blockingClient = client.toBlocking();
    }

    @Nested
    class WhenAsAdmin {

        @BeforeEach
        void setup() {
            dbCleanup.cleanup();
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());
        }

        @Test
        public void cannotCreateWithoutGroupSpecified() {
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                createApplication("TestApplication", null);
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<List> bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(group -> ResponseStatusCodes.APPLICATION_REQUIRES_GROUP_ASSOCIATION.equals(group.get("code"))));
        }

        @Test
        public void canCreateApplicationWithGroupSpecified() {
            HttpResponse<?> response;

            // create group
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

            // create application
            response = createApplication("TestApplication", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationOptional.isPresent());
        }

        @Test
        public void cannotCreateApplicationWithSameNameAsAnotherInSameGroup() {
            HttpResponse<?> response;

            // create group
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

            // create application
            response = createApplication("TestApplication", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationOptional.isPresent());

            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                createApplication("TestApplication", primaryGroup.getId());
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<List> bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(group -> ResponseStatusCodes.APPLICATION_ALREADY_EXISTS.equals(group.get("code"))));
        }

        @Test
        public void canCreateApplicationWithSameNameInAnotherGroup() {
            HttpResponse<?> response;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

            response = createGroup("SecondaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> secondaryOptional = response.getBody(Group.class);
            assertTrue(secondaryOptional.isPresent());
            Group secondaryGroup = secondaryOptional.get();

            // create application
            response = createApplication("TestApplication", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationOptional.isPresent());

            response = createApplication("TestApplication", secondaryGroup.getId());
            assertEquals(OK, response.getStatus());
        }

        @Test
        public void cannotCreateApplicationWithNullNorWhitespace() {
            HttpResponse<?> response;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

            // null
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                createApplication(null, primaryGroup.getId());
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<List> bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(group -> ResponseStatusCodes.APPLICATION_NAME_CANNOT_BE_BLANK_OR_NULL.equals(group.get("code"))));

            // space
            exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                createApplication("     ", primaryGroup.getId());
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(group -> ResponseStatusCodes.APPLICATION_NAME_CANNOT_BE_BLANK_OR_NULL.equals(group.get("code"))));
        }

        @Test
        public void cannotCreateWithNameLessThanThreeCharacters() {
            HttpResponse<?> response;

            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<Group> thetaOptional = response.getBody(Group.class);
            assertTrue(thetaOptional.isPresent());
            Group theta = thetaOptional.get();

            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                createApplication("a", theta.getId());
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<List> bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(group -> ResponseStatusCodes.APPLICATION_NAME_CANNOT_BE_LESS_THAN_THREE_CHARACTERS.equals(group.get("code"))));
        }

        @Test
        public void createShouldTrimWhitespace() {
            HttpResponse<?> response;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

            // create application
            response = createApplication("   Abc123  ", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationOptional.isPresent());
            assertEquals("Abc123", applicationOptional.get().getName());
        }

        @Test
        public void cannotCreateApplicationWithSameNameInGroup() {

            HttpResponse<?> response;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

            // create application
            response = createApplication("Abc123", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationOptional.isPresent());
            assertEquals("Abc123", applicationOptional.get().getName());

            // duplicate create attempt
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                createApplication("Abc123", primaryGroup.getId());
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<List> bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(group -> ResponseStatusCodes.APPLICATION_ALREADY_EXISTS.equals(group.get("code"))));
        }

        @Test
        public void canViewAllApplications() {
            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

            response = createGroup("SecondaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> secondaryOptional = response.getBody(Group.class);
            assertTrue(secondaryOptional.isPresent());
            Group secondaryGroup = secondaryOptional.get();

            // create applications
            response = createApplication("TestApplication", primaryGroup.getId());
            assertEquals(OK, response.getStatus());

            response = createApplication("TestApplication", secondaryGroup.getId());
            assertEquals(OK, response.getStatus());

            request = HttpRequest.GET("/applications");
            Page page = blockingClient.retrieve(request, Page.class);
            assertEquals(2, page.getContent().size());
        }

        @Test
        void canListAllApplicationsWithFilter(){
            // Group - Applications
            // ---
            // PrimaryGroup - Xyz789, abc098
            // SecondaryGroup - Abc123

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

            response = createGroup("SecondaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> secondaryOptional = response.getBody(Group.class);
            assertTrue(secondaryOptional.isPresent());
            Group secondaryGroup = secondaryOptional.get();

            // create applications
            response = createApplication("Xyz789", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            response = createApplication("abc098", primaryGroup.getId());
            assertEquals(OK, response.getStatus());

            response = createApplication("Abc123", secondaryGroup.getId());
            assertEquals(OK, response.getStatus());

            // support case-insensitive
            request = HttpRequest.GET("/applications?filter=abc");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> applicationPage = response.getBody(Page.class);
            assertTrue(applicationPage.isPresent());
            assertEquals(2, applicationPage.get().getContent().size());

            // group search
            request = HttpRequest.GET("/applications?filter=secondary");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            applicationPage = response.getBody(Page.class);
            assertTrue(applicationPage.isPresent());
            assertEquals(1, applicationPage.get().getContent().size());
        }

        @Test
        void canListAllApplicationsNameInAscendingOrderByDefault(){
            // Group - Applications
            // ---
            // PrimaryGroup - Xyz789, abc098
            // SecondaryGroup - Abc123, Xyz789

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

            response = createGroup("SecondaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> secondaryOptional = response.getBody(Group.class);
            assertTrue(secondaryOptional.isPresent());
            Group secondaryGroup = secondaryOptional.get();

            // create applications
            response = createApplication("Xyz789", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            response = createApplication("abc098", primaryGroup.getId());
            assertEquals(OK, response.getStatus());

            response = createApplication("Abc123", secondaryGroup.getId());
            assertEquals(OK, response.getStatus());
            response = createApplication("Xyz789", secondaryGroup.getId());
            assertEquals(OK, response.getStatus());

            // test
            request = HttpRequest.GET("/applications");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> applicationsOptional = response.getBody(Page.class);
            assertTrue(applicationsOptional.isPresent());
            List<Map> applications = applicationsOptional.get().getContent();

            List<String> appNames = applications.stream()
                    .flatMap(map -> Stream.of((String) map.get("name")))
                    .collect(Collectors.toList());
            Collator collator = Collator.getInstance();
            collator.setStrength(Collator.PRIMARY);
            assertEquals(appNames.stream().sorted(collator).collect(Collectors.toList()), appNames);

            List<String> xyzApplications = applications.stream().filter(map -> {
                String appName = (String) map.get("name");
                return appName.equals("Xyz789");
            }).flatMap(map -> Stream.of((String) map.get("groupName"))).collect(Collectors.toList());
            assertEquals(xyzApplications.stream().sorted(collator).collect(Collectors.toList()), xyzApplications);
        }

        @Test
        void canListAllApplicationsNameInDescendingOrder(){
            // Group - Applications
            // ---
            // PrimaryGroup - Xyz789, abc098
            // SecondaryGroup - Abc123

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

            response = createGroup("SecondaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> secondaryOptional = response.getBody(Group.class);
            assertTrue(secondaryOptional.isPresent());
            Group secondaryGroup = secondaryOptional.get();

            // create applications
            response = createApplication("Xyz789", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            response = createApplication("abc098", primaryGroup.getId());
            assertEquals(OK, response.getStatus());

            response = createApplication("Abc123", secondaryGroup.getId());
            assertEquals(OK, response.getStatus());

            // test
            request = HttpRequest.GET("/applications?sort=name,desc");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> applicationsOptional = response.getBody(Page.class);
            assertTrue(applicationsOptional.isPresent());
            List<Map> applications = applicationsOptional.get().getContent();

            Collator collator = Collator.getInstance();
            collator.setStrength(Collator.PRIMARY);
            List<String> appNames = applications.stream()
                    .flatMap(map -> Stream.of((String) map.get("name")))
                    .collect(Collectors.toList());
            assertEquals(appNames.stream().sorted(collator.reversed()).collect(Collectors.toList()), appNames);
        }

        @Test
        public void canUpdateApplicationName() {
            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

            // create applications
            response = createApplication("TestApplication", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationOptional.isPresent());
            ApplicationDTO application = applicationOptional.get();

            application.setName("TestApplicationUpdate");
            request = HttpRequest.POST("/applications/save", application);
            response = blockingClient.exchange(request, ApplicationDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> updatedApplicationOptional = response.getBody(ApplicationDTO.class);
            assertTrue(updatedApplicationOptional.isPresent());
            ApplicationDTO updatedApplication = updatedApplicationOptional.get();

            assertEquals("TestApplicationUpdate", updatedApplication.getName());
        }

        @Test
        public void cannotUpdateApplicationGroup() {
            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

            response = createGroup("SecondaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> secondaryOptional = response.getBody(Group.class);
            assertTrue(secondaryOptional.isPresent());
            Group secondaryGroup = secondaryOptional.get();

            // create applications
            response = createApplication("TestApplication", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationOptional.isPresent());
            ApplicationDTO application = applicationOptional.get();

            // change group
            application.setGroup(secondaryGroup.getId());
            request = HttpRequest.POST("/applications/save", application);
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(request);
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<List> bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(group -> ResponseStatusCodes.APPLICATION_CANNOT_UPDATE_GROUP_ASSOCIATION.equals(group.get("code"))));
        }

        @Test
        public void cannotUpdateApplicationNameIfOneAlreadyExistsInSameGroup() {
            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

            // create applications
            response = createApplication("ApplicationOne", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationOneOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationOneOptional.isPresent());
            ApplicationDTO applicationOne = applicationOneOptional.get();

            response = createApplication("ApplicationTwo", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationTwoOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationTwoOptional.isPresent());
            ApplicationDTO applicationTwo = applicationTwoOptional.get();

            // update attempt
            applicationTwo.setName(applicationOne.getName());
            request = HttpRequest.POST("/applications/save", applicationTwo);
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(request, ApplicationDTO.class);
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<List> bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(group -> ResponseStatusCodes.APPLICATION_ALREADY_EXISTS.equals(group.get("code"))));
        }

        @Test
        public void canDeleteFromGroup() {
            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

            // create application
            response = createApplication("ApplicationOne", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationOneOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationOneOptional.isPresent());
            ApplicationDTO applicationOne = applicationOneOptional.get();

            // delete
            request = HttpRequest.POST("/applications/delete/" + applicationOne.getId(), Map.of());
            response = blockingClient.exchange(request);
            assertEquals(SEE_OTHER, response.getStatus());
        }

        @Test
        void canGeneratePassphraseAndVerify() {
            HttpRequest request;
            HttpResponse response;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

            // create application
            response = createApplication("ApplicationOne", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationOneOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationOneOptional.isPresent());
            ApplicationDTO applicationOne = applicationOneOptional.get();

            // generate passphrase for application
            request = HttpRequest.GET("/applications/generate-passphrase/" + applicationOne.getId());
            response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            Optional<String> optional = response.getBody(String.class);
            assertTrue(optional.isPresent());

            Map credentials;

            // The micronaut client follow redirects by default. Disabling it allows us the capture the existence
            // of the JWT cookie and prevent an exception being thrown due to port 8080 connection being refused.
            // JUnit test run on a random port and the application.yml failed/success auth paths are hard-coded
            // to port 8080. In addition, the redirect response's JWT cookie is not populated.
            // failed login
            credentials = Map.of(
                    "username", applicationOne.getId().toString(),
                    "password", "FailedLogin"
            );
            request = HttpRequest.POST("/login", credentials);
            response = blockingClient.exchange(request, Map.class);
            assertEquals(SEE_OTHER, response.getStatus());
            assertTrue(response.getCookie("JWT").isEmpty());

            // successful login
            credentials = Map.of(
                    "username", applicationOne.getId().toString(),
                    "password", optional.get()
            );
            request = HttpRequest.POST("/login", credentials);
            response = blockingClient.exchange(request, Map.class);
            assertEquals(SEE_OTHER, response.getStatus());
            assertTrue(response.getCookie("JWT").isPresent());
        }
    }

    @Nested
    class WhenAsAnApplicationAdmin {

        @BeforeEach
        void setup() {
            dbCleanup.cleanup();
            userRepository.save(new User("montesm@test.test", true));
            userRepository.save(new User("jjones@test.test"));
        }

        void loginAsNonAdmin() {
            mockSecurityService.setServerAuthentication(new ServerAuthentication(
                    "jjones@test.test",
                    Collections.emptyList(),
                    Map.of("isAdmin", false)
            ));
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());
        }

        @Test
        public void cannotCreateWithoutGroupSpecified() {
            loginAsNonAdmin();
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                createApplication("TestApplication", null);
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<List> bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(group -> ResponseStatusCodes.APPLICATION_REQUIRES_GROUP_ASSOCIATION.equals(group.get("code"))));
        }

        @Test
        public void cannotCreateIfNotMemberOfGroup() {
            HttpResponse<?> response;

            mockSecurityService.postConstruct();  // create Application as Admin

            // save group without members
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryGroupOptional = response.getBody(Group.class);
            assertTrue(primaryGroupOptional.isPresent());
            Group primaryGroup = primaryGroupOptional.get();

            loginAsNonAdmin();

            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                createApplication("TestApplication", primaryGroup.getId());
            });
            assertEquals(UNAUTHORIZED, exception.getStatus());
            Optional<List> bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(group -> ResponseStatusCodes.UNAUTHORIZED.equals(group.get("code"))));
        }

        @Test
        public void canCreate() {
            mockSecurityService.postConstruct();

            // create group
            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

            User justin = userRepository.findByEmail("jjones@test.test").get();

            // add user to group as an application admin
            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(primaryGroup.getId());
            dto.setEmail(justin.getEmail());
            dto.setApplicationAdmin(true);
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            loginAsNonAdmin();

            // create application with above group
            response = createApplication("TestApplication", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            assertTrue(response.getBody(ApplicationDTO.class).isPresent());
        }

        @Test
        public void cannotCreateIfNonApplicationAdminMemberOfGroup() {
            mockSecurityService.postConstruct();

            // create group
            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

            // get user
            User justin = userRepository.findByEmail("jjones@test.test").get();

            // add user to group as an application admin
            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(primaryGroup.getId());
            dto.setEmail(justin.getEmail());
            dto.setTopicAdmin(true);
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            loginAsNonAdmin();

            // create application with above group
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                createApplication("TestApplication", primaryGroup.getId());
            });
            assertEquals(UNAUTHORIZED, exception.getStatus());
            Optional<List> bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(group -> ResponseStatusCodes.UNAUTHORIZED.equals(group.get("code"))));
        }


        @Test
        public void canViewGroupApplicationsAsMember() {
            // PrimaryGroup - TestApplicationOne, TestApplicationTwo
            // SecondaryGroup - Three, Four

            mockSecurityService.postConstruct();

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

            response = createGroup("SecondaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> secondaryGroupOptional = response.getBody(Group.class);
            assertTrue(secondaryGroupOptional.isPresent());

            // get user
            User justin = userRepository.findByEmail("jjones@test.test").get();

            // add user to group
            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(primaryGroup.getId());
            dto.setEmail(justin.getEmail());
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            response = createApplication("TestApplicationOne", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            response = createApplication("TestApplicationTwo", primaryGroup.getId());
            assertEquals(OK, response.getStatus());

            response = createApplication("Three", secondaryGroupOptional.get().getId());
            assertEquals(OK, response.getStatus());
            response = createApplication("Four", secondaryGroupOptional.get().getId());
            assertEquals(OK, response.getStatus());

            loginAsNonAdmin();

            request = HttpRequest.GET("/applications");
            Page page = blockingClient.retrieve(request, Page.class);
            assertEquals(2, page.getContent().size());
            List<Map> content = page.getContent();
            assertTrue(content.stream().noneMatch(map -> {
                String groupName = (String) map.get("groupName");
                return Objects.equals(groupName, "SecondaryGroup");
            }));
        }

        @Test
        void canListApplicationsWithFilterLimitedToGroupMembership(){
            // PrimaryGroup - TestApplicationOne, TestApplicationTwo
            // SecondaryGroup - Three, Four

            mockSecurityService.postConstruct();

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

            response = createGroup("SecondaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> secondaryGroupOptional = response.getBody(Group.class);
            assertTrue(secondaryGroupOptional.isPresent());

            // get user
            User justin = userRepository.findByEmail("jjones@test.test").get();

            // add user to group
            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(primaryGroup.getId());
            dto.setEmail(justin.getEmail());
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            response = createApplication("TestApplicationOne", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            response = createApplication("TestApplicationTwo", primaryGroup.getId());
            assertEquals(OK, response.getStatus());

            response = createApplication("Three", secondaryGroupOptional.get().getId());
            assertEquals(OK, response.getStatus());
            response = createApplication("Four", secondaryGroupOptional.get().getId());
            assertEquals(OK, response.getStatus());

            loginAsNonAdmin();

            // application
            request = HttpRequest.GET("/applications?filter=application");
            Page page = blockingClient.retrieve(request, Page.class);
            assertEquals(2, page.getContent().size());
            List<Map> content = page.getContent();
            assertTrue(content.stream().noneMatch(map -> {
                String groupName = (String) map.get("groupName");
                return Objects.equals(groupName, "SecondaryGroup");
            }));

            // group
            request = HttpRequest.GET("/applications?filter=aryGrouP");
            page = blockingClient.retrieve(request, Page.class);
            assertEquals(2, page.getContent().size());
            content = page.getContent();
            assertTrue(content.stream().noneMatch(map -> {
                String groupName = (String) map.get("groupName");
                return Objects.equals(groupName, "SecondaryGroup");
            }));
        }

        @Test
        public void canUpdateApplicationName() {
            mockSecurityService.postConstruct();

            // create group
            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

            // get user
            User justin = userRepository.findByEmail("jjones@test.test").get();

            // add user to group as an application admin
            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(primaryGroup.getId());
            dto.setEmail(justin.getEmail());
            dto.setApplicationAdmin(true);
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // add application to group
            response = createApplication("TestApplication", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationOptional.isPresent());
            ApplicationDTO application = applicationOptional.get();

            loginAsNonAdmin();

            application.setName("TestApplicationUpdate");
            request = HttpRequest.POST("/applications/save", application);
            response = blockingClient.exchange(request, ApplicationDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationUpdateOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationUpdateOptional.isPresent());
            ApplicationDTO applicationUpdate = applicationUpdateOptional.get();

            assertEquals("TestApplicationUpdate", applicationUpdate.getName());
        }

        @Test
        public void cannotUpdateApplicationGroupIfTargetGroupApplicationAdmin() {
            mockSecurityService.postConstruct();

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create two groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Group primaryGroup = response.getBody(Group.class).get();

            response = createGroup("SecondaryGroup");
            assertEquals(OK, response.getStatus());
            Group secondaryGroup = response.getBody(Group.class).get();

            // get user
            User justin = userRepository.findByEmail("jjones@test.test").get();

            // add user to groups as an application admin
            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(primaryGroup.getId());
            dto.setEmail(justin.getEmail());
            dto.setApplicationAdmin(true);
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            dto.setPermissionsGroup(secondaryGroup.getId());
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // add application to group
            response = createApplication("TestApplication", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            assertTrue(response.getBody(ApplicationDTO.class).isPresent());
            ApplicationDTO application = response.getBody(ApplicationDTO.class).get();

            loginAsNonAdmin();

            // change the application to the other group
            application.setGroup(secondaryGroup.getId());
            request = HttpRequest.POST("/applications/save", application);
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<List> bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(group -> ResponseStatusCodes.APPLICATION_CANNOT_UPDATE_GROUP_ASSOCIATION.equals(group.get("code"))));
        }

        @Test
        public void cannotUpdateApplicationGroupIfNotApplicationAdminOfTargetGroup() {
            mockSecurityService.postConstruct();

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create two groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Group primaryGroup = response.getBody(Group.class).get();

            response = createGroup("SecondaryGroup");
            assertEquals(OK, response.getStatus());
            Group secondaryGroup = response.getBody(Group.class).get();

            // get user
            User justin = userRepository.findByEmail("jjones@test.test").get();

            // add user to groups as an application admin
            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(primaryGroup.getId());
            dto.setEmail(justin.getEmail());
            dto.setApplicationAdmin(true);
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            dto.setPermissionsGroup(secondaryGroup.getId());
            dto.setApplicationAdmin(false);
            dto.setTopicAdmin(true);
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // add application to group
            response = createApplication("TestApplication", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            assertTrue(response.getBody(ApplicationDTO.class).isPresent());
            ApplicationDTO application = response.getBody(ApplicationDTO.class).get();

            loginAsNonAdmin();

            // change application.group to the other group
            application.setGroup(secondaryGroup.getId());
            request = HttpRequest.POST("/applications/save", application);
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(UNAUTHORIZED, exception.getStatus());
            Optional<List> bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(group -> ResponseStatusCodes.UNAUTHORIZED.equals(group.get("code"))));
        }

        @Test
        public void canDeleteFromGroup() {
            mockSecurityService.postConstruct();

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create group
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Group primaryGroup = response.getBody(Group.class).get();

            // get user
            User justin = userRepository.findByEmail("jjones@test.test").get();

            // add user to group as an application admin
            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(primaryGroup.getId());
            dto.setEmail(justin.getEmail());
            dto.setApplicationAdmin(true);
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // add application to group
            response = createApplication("TestApplication", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            assertTrue(response.getBody(ApplicationDTO.class).isPresent());
            ApplicationDTO application = response.getBody(ApplicationDTO.class).get();

            loginAsNonAdmin();

            request = HttpRequest.POST("/applications/delete/" + application.getId(), Map.of());
            response = blockingClient.exchange(request);
            assertEquals(SEE_OTHER, response.getStatus());
        }
    }

    @Nested
    class WhenAsNonAdmin {

        @BeforeEach
        void setup() {
            dbCleanup.cleanup();
            userRepository.save(new User("montesm@test.test"));
            userRepository.save(new User("jjones@test.test"));
        }

        void loginAsNonAdmin() {
            mockSecurityService.setServerAuthentication(new ServerAuthentication(
                    "jjones@test.test",
                    Collections.emptyList(),
                    Map.of("isAdmin", false)
            ));
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());
        }

        @Test
        public void cannotCreateWithoutGroupSpecified() {
            loginAsNonAdmin();
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                createApplication("TestApplication", null);
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<List> bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(group -> ResponseStatusCodes.APPLICATION_REQUIRES_GROUP_ASSOCIATION.equals(group.get("code"))));
        }

        @Test
        public void cannotCreateApplicationWithGroupSpecified() {
            mockSecurityService.postConstruct();  // create Application as Admin
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

            loginAsNonAdmin();

            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                createApplication("TestApplication", primaryGroup.getId());
            });
            assertEquals(UNAUTHORIZED, exception.getStatus());
            Optional<List> bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(group -> ResponseStatusCodes.UNAUTHORIZED.equals(group.get("code"))));
        }

        @Test
        public void cannotCreateApplicationWithSameNameAsAnotherInSameGroup() {

            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

            // create applications
            response = createApplication("ApplicationOne", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationOptional.isPresent());
            ApplicationDTO applicationOne = applicationOptional.get();

            loginAsNonAdmin();

            request = HttpRequest.POST("/applications/save", applicationOne);
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(UNAUTHORIZED, exception.getStatus());
            Optional<List> bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(group -> ResponseStatusCodes.UNAUTHORIZED.equals(group.get("code"))));
        }

        @Test
        public void cannotViewApplicationDetails() {

            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

            // create applications
            response = createApplication("TestApplication", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationOptional.isPresent());
            ApplicationDTO applicationOne = applicationOptional.get();

            loginAsNonAdmin();

            request = HttpRequest.GET("/applications/show/"+applicationOne.getId());
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(UNAUTHORIZED, exception.getStatus());
            Optional<List> bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(group -> ResponseStatusCodes.UNAUTHORIZED.equals(group.get("code"))));
        }

        @Test
        public void cannotUpdateApplicationName() {
            mockSecurityService.postConstruct();

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

            // create applications
            response = createApplication("TestApplication", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationOptional.isPresent());
            ApplicationDTO application = applicationOptional.get();

            loginAsNonAdmin();

            application.setName("TestApplicationUpdate");
            request = HttpRequest.POST("/applications/save", application);
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(UNAUTHORIZED, exception.getStatus());
            Optional<List> bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(group -> ResponseStatusCodes.UNAUTHORIZED.equals(group.get("code"))));
        }

        @Test
        public void cannotUpdateApplicationNameIfOneAlreadyExistsInSameGroup() {
            mockSecurityService.postConstruct();

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create group
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

            // create application
            response = createApplication("ApplicationOne", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationOneOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationOneOptional.isPresent());
            ApplicationDTO applicationOne = applicationOneOptional.get();

            response = createApplication("ApplicationTwo", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationTwoOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationTwoOptional.isPresent());
            ApplicationDTO applicationTwo = applicationTwoOptional.get();

            loginAsNonAdmin();

            applicationTwo.setName(applicationOne.getName());
            request = HttpRequest.POST("/applications/save", applicationTwo);
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(UNAUTHORIZED, exception.getStatus());
            Optional<List> bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(group -> ResponseStatusCodes.UNAUTHORIZED.equals(group.get("code"))));
        }

        @Test
        public void cannotDeleteFromGroup() {
            mockSecurityService.postConstruct();

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create group
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

            // create application
            response = createApplication("ApplicationDelete", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationOptional.isPresent());
            ApplicationDTO application = applicationOptional.get();

            loginAsNonAdmin();

            request = HttpRequest.POST("/applications/delete/" + application.getId(), Map.of());
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(UNAUTHORIZED, exception.getStatus());
            Optional<List> bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(group -> ResponseStatusCodes.UNAUTHORIZED.equals(group.get("code"))));
        }
    }

    @Nested
    class WhenAsAnApplication {
        @BeforeEach
        void setup() {
            dbCleanup.cleanup();
            userRepository.save(new User("montesm@test.test", true));
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());
            setETagPropsForMockApplicationSecretsClient("abc", false);
        }

        void setETagPropsForMockApplicationSecretsClient(String etag, boolean hasCachedFileBeenUpdated) {
            mockApplicationSecretsClient.setEtag(etag);
            mockApplicationSecretsClient.setHasCachedFileBeenUpdated(hasCachedFileBeenUpdated);
        }

        void loginAsApplication(Long applicationId) {
            mockSecurityService.setServerAuthentication(new ServerAuthentication(
                    String.valueOf(applicationId),
                    List.of(UserRole.APPLICATION.toString()),
                    Map.of()
            ));
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());
        }

        @Test
        void canDownloadFiles() {
            HttpRequest request;
            HttpResponse response;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

            // create application
            response = createApplication("ApplicationOne", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationOneOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationOneOptional.isPresent());
            ApplicationDTO applicationOne = applicationOneOptional.get();

            // generate passphrase for application
            request = HttpRequest.GET("/applications/generate-passphrase/" + applicationOne.getId());
            response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            Optional<String> optional = response.getBody(String.class);
            assertTrue(optional.isPresent());

            Map credentials;

            // The micronaut client follow redirects by default. Disabling it allows us the capture the existence
            // of the JWT cookie and prevent an exception being thrown due to port 8080 connection being refused.
            // JUnit test run on a random port and the application.yml failed/success auth paths are hard-coded
            // to port 8080. In addition, the redirect response's JWT cookie is not populated.
            // failed login
            credentials = Map.of(
                    "username", applicationOne.getId().toString(),
                    "password", "FailedLogin"
            );
            request = HttpRequest.POST("/login", credentials);
            response = blockingClient.exchange(request, Map.class);
            assertEquals(SEE_OTHER, response.getStatus());
            assertTrue(response.getCookie("JWT").isEmpty());

            // successful login
            credentials = Map.of(
                    "username", applicationOne.getId().toString(),
                    "password", optional.get()
            );
            request = HttpRequest.POST("/login", credentials);
            response = blockingClient.exchange(request, Map.class);
            assertEquals(SEE_OTHER, response.getStatus());
            Optional<Cookie> jwtOptional = response.getCookie("JWT");
            assertTrue(jwtOptional.isPresent());

            loginAsApplication(applicationOne.getId());

            request = HttpRequest.GET("/applications/identity_ca.pem");
            response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            assertEquals("abc", response.header(E_TAG_HEADER_NAME));
            Optional<String> body = response.getBody(String.class);
            assertTrue(body.isPresent());
            assertEquals("-----BEGIN CERTIFICATE-----\n" +
                    "MIICgTCCAiagAwIBAgIJAKQ0JTu6DZZdMAoGCCqGSM49BAMCMIGSMQswCQYDVQQG\n" +
                    "EwJVUzELMAkGA1UECAwCTU8xFDASBgNVBAcMC1NhaW50IExvdWlzMQ4wDAYDVQQK\n" +
                    "DAVVTklUWTESMBAGA1UECwwJQ29tbVVOSVRZMRQwEgYDVQQDDAtJZGVudGl0eSBD\n" +
                    "QTEmMCQGCSqGSIb3DQEJARYXaW5mb0B1bml0eWZvdW5kYXRpb24uaW8wHhcNMjIx\n" +
                    "MDIwMTYwMjU3WhcNMjIxMTE5MTYwMjU3WjCBkjELMAkGA1UEBhMCVVMxCzAJBgNV\n" +
                    "BAgMAk1PMRQwEgYDVQQHDAtTYWludCBMb3VpczEOMAwGA1UECgwFVU5JVFkxEjAQ\n" +
                    "BgNVBAsMCUNvbW1VTklUWTEUMBIGA1UEAwwLSWRlbnRpdHkgQ0ExJjAkBgkqhkiG\n" +
                    "9w0BCQEWF2luZm9AdW5pdHlmb3VuZGF0aW9uLmlvMFkwEwYHKoZIzj0CAQYIKoZI\n" +
                    "zj0DAQcDQgAEMotkUz0ZUGv8lYvglXI5l6ArVs5PyWfz3civhogC4UuTZB9JyQOM\n" +
                    "Xcvef04VJuhlvUJ4apUB2pW/exETApYKYqNjMGEwHQYDVR0OBBYEFAADg6XL4V94\n" +
                    "czckFXsfuvBy1sJOMB8GA1UdIwQYMBaAFAADg6XL4V94czckFXsfuvBy1sJOMA8G\n" +
                    "A1UdEwEB/wQFMAMBAf8wDgYDVR0PAQH/BAQDAgGGMAoGCCqGSM49BAMCA0kAMEYC\n" +
                    "IQCtNp2FjbRmmpjAruZIu6L+zc+udvFS626HSugfxYGT0QIhAKKIZD0Q1OLbeFuf\n" +
                    "9lAUsvd7k+g3XVrE1DJ3zoUh+NfQ\n" +
                    "-----END CERTIFICATE-----",
                    body.get());

            request = HttpRequest.GET("/applications/permissions_ca.pem");
            response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            body = response.getBody(String.class);
            assertTrue(body.isPresent());
            assertEquals("-----BEGIN CERTIFICATE-----\n" +
                            "MIIChzCCAiygAwIBAgIJALs53aA9RNgXMAoGCCqGSM49BAMCMIGVMQswCQYDVQQG\n" +
                            "EwJVUzELMAkGA1UECAwCTU8xFDASBgNVBAcMC1NhaW50IExvdWlzMQ4wDAYDVQQK\n" +
                            "DAVVTklUWTESMBAGA1UECwwJQ29tbVVOSVRZMRcwFQYDVQQDDA5QZXJtaXNzaW9u\n" +
                            "cyBDQTEmMCQGCSqGSIb3DQEJARYXaW5mb0B1bml0eWZvdW5kYXRpb24uaW8wHhcN\n" +
                            "MjIxMDIwMTYwMzQ3WhcNMjIxMTE5MTYwMzQ3WjCBlTELMAkGA1UEBhMCVVMxCzAJ\n" +
                            "BgNVBAgMAk1PMRQwEgYDVQQHDAtTYWludCBMb3VpczEOMAwGA1UECgwFVU5JVFkx\n" +
                            "EjAQBgNVBAsMCUNvbW1VTklUWTEXMBUGA1UEAwwOUGVybWlzc2lvbnMgQ0ExJjAk\n" +
                            "BgkqhkiG9w0BCQEWF2luZm9AdW5pdHlmb3VuZGF0aW9uLmlvMFkwEwYHKoZIzj0C\n" +
                            "AQYIKoZIzj0DAQcDQgAETpQ3PmzztDkjocQ4jDXDmJSLKNTywfhBj+TaMRMj1llR\n" +
                            "zzyyg84CAxvOo6aXurB7DP2mOLd3e+JcCnxyIYIjzaNjMGEwHQYDVR0OBBYEFMYW\n" +
                            "gviUzxL5RbpFMYDY4TgcSVQXMB8GA1UdIwQYMBaAFMYWgviUzxL5RbpFMYDY4Tgc\n" +
                            "SVQXMA8GA1UdEwEB/wQFMAMBAf8wDgYDVR0PAQH/BAQDAgGGMAoGCCqGSM49BAMC\n" +
                            "A0kAMEYCIQCzNAsDtL2g3M91hl5i4EUmFtpL4KFnoQ6XrxuatOo63wIhALZhDjMy\n" +
                            "TsRZo3Lrd0CARNDlnrUNQwgblVJCtmjCK5V4\n" +
                            "-----END CERTIFICATE-----",
                    body.get());

            request = HttpRequest.GET("/applications/governance.xml.p7s");
            response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            body = response.getBody(String.class);
            assertTrue(body.isPresent());
            assertEquals("MIME-Version: 1.0\n" +
                    "Content-Type: multipart/signed; protocol=\"application/x-pkcs7-signature\"; micalg=\"sha1\"; boundary=\"----E7CBBA7468B3989AB3841DB52EB8FBDC\"\n" +
                    "\n" +
                    "This is an S/MIME signed message\n" +
                    "\n" +
                    "------E7CBBA7468B3989AB3841DB52EB8FBDC\n" +
                    "Content-Type: text/plain\n" +
                    "\n" +
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<!--\n" +
                    "  Illustrates DDS Security can be used to protect access to a DDS Domain.\n" +
                    "  Only applications that can authenticate and have the proper permissions can\n" +
                    "  join the Domain. Others cannot publish nor subscribe.\n" +
                    "-->\n" +
                    "<dds xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"http://www.omg.org/spec/DDS-SECURITY/20170901/omg_shared_ca_permissions.xsd\">\n" +
                    "  <domain_access_rules>\n" +
                    "    <!--\n" +
                    "      Domain 0 is a \"protected domain.\" That is, only applications that\n" +
                    "      can authenticate and have proper permissions can join it.\n" +
                    "    -->\n" +
                    "    <domain_rule>\n" +
                    "      <domains>\n" +
                    "        <id>0</id>\n" +
                    "      </domains>\n" +
                    "      <allow_unauthenticated_participants>false</allow_unauthenticated_participants>\n" +
                    "      <enable_join_access_control>true</enable_join_access_control>\n" +
                    "      <discovery_protection_kind>ENCRYPT</discovery_protection_kind>\n" +
                    "      <liveliness_protection_kind>ENCRYPT</liveliness_protection_kind>\n" +
                    "      <rtps_protection_kind>ENCRYPT</rtps_protection_kind>\n" +
                    "      <topic_access_rules>\n" +
                    "        <topic_rule>\n" +
                    "          <topic_expression>B*</topic_expression>\n" +
                    "          <enable_discovery_protection>true</enable_discovery_protection>\n" +
                    "          <enable_liveliness_protection>true</enable_liveliness_protection>\n" +
                    "          <enable_read_access_control>false</enable_read_access_control>\n" +
                    "          <enable_write_access_control>true</enable_write_access_control>\n" +
                    "          <metadata_protection_kind>ENCRYPT</metadata_protection_kind>\n" +
                    "          <data_protection_kind>NONE</data_protection_kind>\n" +
                    "        </topic_rule>\n" +
                    "        <topic_rule>\n" +
                    "          <topic_expression>C*</topic_expression>\n" +
                    "          <enable_discovery_protection>true</enable_discovery_protection>\n" +
                    "          <enable_liveliness_protection>true</enable_liveliness_protection>\n" +
                    "          <enable_read_access_control>true</enable_read_access_control>\n" +
                    "          <enable_write_access_control>true</enable_write_access_control>\n" +
                    "          <metadata_protection_kind>ENCRYPT</metadata_protection_kind>\n" +
                    "          <data_protection_kind>NONE</data_protection_kind>\n" +
                    "        </topic_rule>\n" +
                    "      </topic_access_rules>\n" +
                    "    </domain_rule>\n" +
                    "  </domain_access_rules>\n" +
                    "</dds>\n" +
                    "\n" +
                    "------E7CBBA7468B3989AB3841DB52EB8FBDC\n" +
                    "Content-Type: application/x-pkcs7-signature; name=\"smime.p7s\"\n" +
                    "Content-Transfer-Encoding: base64\n" +
                    "Content-Disposition: attachment; filename=\"smime.p7s\"\n" +
                    "\n" +
                    "MIIE2gYJKoZIhvcNAQcCoIIEyzCCBMcCAQExCzAJBgUrDgMCGgUAMAsGCSqGSIb3\n" +
                    "DQEHAaCCAoswggKHMIICLKADAgECAgkAuzndoD1E2BcwCgYIKoZIzj0EAwIwgZUx\n" +
                    "CzAJBgNVBAYTAlVTMQswCQYDVQQIDAJNTzEUMBIGA1UEBwwLU2FpbnQgTG91aXMx\n" +
                    "DjAMBgNVBAoMBVVOSVRZMRIwEAYDVQQLDAlDb21tVU5JVFkxFzAVBgNVBAMMDlBl\n" +
                    "cm1pc3Npb25zIENBMSYwJAYJKoZIhvcNAQkBFhdpbmZvQHVuaXR5Zm91bmRhdGlv\n" +
                    "bi5pbzAeFw0yMjEwMjAxNjAzNDdaFw0yMjExMTkxNjAzNDdaMIGVMQswCQYDVQQG\n" +
                    "EwJVUzELMAkGA1UECAwCTU8xFDASBgNVBAcMC1NhaW50IExvdWlzMQ4wDAYDVQQK\n" +
                    "DAVVTklUWTESMBAGA1UECwwJQ29tbVVOSVRZMRcwFQYDVQQDDA5QZXJtaXNzaW9u\n" +
                    "cyBDQTEmMCQGCSqGSIb3DQEJARYXaW5mb0B1bml0eWZvdW5kYXRpb24uaW8wWTAT\n" +
                    "BgcqhkjOPQIBBggqhkjOPQMBBwNCAAROlDc+bPO0OSOhxDiMNcOYlIso1PLB+EGP\n" +
                    "5NoxEyPWWVHPPLKDzgIDG86jppe6sHsM/aY4t3d74lwKfHIhgiPNo2MwYTAdBgNV\n" +
                    "HQ4EFgQUxhaC+JTPEvlFukUxgNjhOBxJVBcwHwYDVR0jBBgwFoAUxhaC+JTPEvlF\n" +
                    "ukUxgNjhOBxJVBcwDwYDVR0TAQH/BAUwAwEB/zAOBgNVHQ8BAf8EBAMCAYYwCgYI\n" +
                    "KoZIzj0EAwIDSQAwRgIhALM0CwO0vaDcz3WGXmLgRSYW2kvgoWehDpevG5q06jrf\n" +
                    "AiEAtmEOMzJOxFmjcut3QIBE0OWetQ1DCBuVUkK2aMIrlXgxggIXMIICEwIBATCB\n" +
                    "ozCBlTELMAkGA1UEBhMCVVMxCzAJBgNVBAgMAk1PMRQwEgYDVQQHDAtTYWludCBM\n" +
                    "b3VpczEOMAwGA1UECgwFVU5JVFkxEjAQBgNVBAsMCUNvbW1VTklUWTEXMBUGA1UE\n" +
                    "AwwOUGVybWlzc2lvbnMgQ0ExJjAkBgkqhkiG9w0BCQEWF2luZm9AdW5pdHlmb3Vu\n" +
                    "ZGF0aW9uLmlvAgkAuzndoD1E2BcwCQYFKw4DAhoFAKCCAQcwGAYJKoZIhvcNAQkD\n" +
                    "MQsGCSqGSIb3DQEHATAcBgkqhkiG9w0BCQUxDxcNMjIxMDIwMTYwNDE5WjAjBgkq\n" +
                    "hkiG9w0BCQQxFgQUJPFYFIoOlpQ91q+BZSPGJZdoCq8wgacGCSqGSIb3DQEJDzGB\n" +
                    "mTCBljALBglghkgBZQMEASowCAYGKoUDAgIJMAoGCCqFAwcBAQICMAoGCCqFAwcB\n" +
                    "AQIDMAgGBiqFAwICFTALBglghkgBZQMEARYwCwYJYIZIAWUDBAECMAoGCCqGSIb3\n" +
                    "DQMHMA4GCCqGSIb3DQMCAgIAgDANBggqhkiG9w0DAgIBQDAHBgUrDgMCBzANBggq\n" +
                    "hkiG9w0DAgIBKDAJBgcqhkjOPQQBBEcwRQIgFusiTtqdVhanba5ezc++SFT4VQY7\n" +
                    "v1bEzsC03JCRauYCIQCKqjWLJDrJpwd7ruWSFpQ6w/iyqN13BlWdnrn2KywpEw==\n" +
                    "\n" +
                    "------E7CBBA7468B3989AB3841DB52EB8FBDC--\n" +
                    "\n",
                    body.get());
        }

        @Test
        void downloadFileRequestShouldReturnNotModifiedIfRequestEtagIsTheSameAsCachedFileETag() {
            HttpRequest request;
            HttpResponse response;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

            // create application
            response = createApplication("ApplicationOne", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationOneOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationOneOptional.isPresent());
            ApplicationDTO applicationOne = applicationOneOptional.get();

            // generate passphrase for application
            request = HttpRequest.GET("/applications/generate-passphrase/" + applicationOne.getId());
            response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            Optional<String> optional = response.getBody(String.class);
            assertTrue(optional.isPresent());

            Map credentials;

            // The micronaut client follow redirects by default. Disabling it allows us the capture the existence
            // of the JWT cookie and prevent an exception being thrown due to port 8080 connection being refused.
            // JUnit test run on a random port and the application.yml failed/success auth paths are hard-coded
            // to port 8080. In addition, the redirect response's JWT cookie is not populated.
            // failed login
            credentials = Map.of(
                    "username", applicationOne.getId().toString(),
                    "password", "FailedLogin"
            );
            request = HttpRequest.POST("/login", credentials);
            response = blockingClient.exchange(request, Map.class);
            assertEquals(SEE_OTHER, response.getStatus());
            assertTrue(response.getCookie("JWT").isEmpty());

            // successful login
            credentials = Map.of(
                    "username", applicationOne.getId().toString(),
                    "password", optional.get()
            );
            request = HttpRequest.POST("/login", credentials);
            response = blockingClient.exchange(request, Map.class);
            assertEquals(SEE_OTHER, response.getStatus());
            Optional<Cookie> jwtOptional = response.getCookie("JWT");
            assertTrue(jwtOptional.isPresent());

            loginAsApplication(applicationOne.getId());

            request = HttpRequest.GET("/applications/identity_ca.pem").header(E_TAG_HEADER_NAME, "abc");
            response = blockingClient.exchange(request);
            assertEquals(NOT_MODIFIED, response.getStatus());
        }

        @Test
        void downloadFileRequestShouldReturnUpdatedFileIfRequestEtagIsDifferentThanCachedFileETag() {
            HttpRequest request;
            HttpResponse response;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

            // create application
            response = createApplication("ApplicationOne", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationOneOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationOneOptional.isPresent());
            ApplicationDTO applicationOne = applicationOneOptional.get();

            // generate passphrase for application
            request = HttpRequest.GET("/applications/generate-passphrase/" + applicationOne.getId());
            response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            Optional<String> optional = response.getBody(String.class);
            assertTrue(optional.isPresent());

            Map credentials;

            // The micronaut client follow redirects by default. Disabling it allows us the capture the existence
            // of the JWT cookie and prevent an exception being thrown due to port 8080 connection being refused.
            // JUnit test run on a random port and the application.yml failed/success auth paths are hard-coded
            // to port 8080. In addition, the redirect response's JWT cookie is not populated.
            // failed login
            credentials = Map.of(
                    "username", applicationOne.getId().toString(),
                    "password", "FailedLogin"
            );
            request = HttpRequest.POST("/login", credentials);
            response = blockingClient.exchange(request, Map.class);
            assertEquals(SEE_OTHER, response.getStatus());
            assertTrue(response.getCookie("JWT").isEmpty());

            // successful login
            credentials = Map.of(
                    "username", applicationOne.getId().toString(),
                    "password", optional.get()
            );
            request = HttpRequest.POST("/login", credentials);
            response = blockingClient.exchange(request, Map.class);
            assertEquals(SEE_OTHER, response.getStatus());
            Optional<Cookie> jwtOptional = response.getCookie("JWT");
            assertTrue(jwtOptional.isPresent());

            loginAsApplication(applicationOne.getId());
            setETagPropsForMockApplicationSecretsClient("xyz", true);

            String originalFileEtag = "abc";
            request = HttpRequest.GET("/applications/identity_ca.pem").header(E_TAG_HEADER_NAME, originalFileEtag);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());
            assertFalse(originalFileEtag.contentEquals(response.header(E_TAG_HEADER_NAME)));
            assertEquals("xyz", response.header(E_TAG_HEADER_NAME));
        }

        @Test
        void canRetrievePermissionsJson() {
            HttpRequest request;
            HttpResponse response;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

            // create application
            response = createApplication("ApplicationOne", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationOneOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationOneOptional.isPresent());
            ApplicationDTO applicationOne = applicationOneOptional.get();

            // create topic
            response = createTopic("Topic123", TopicKind.C, primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> topicOptional = response.getBody(TopicDTO.class);
            assertTrue(topicOptional.isPresent());
            assertEquals("Topic123", topicOptional.get().getName());

            // create app permission
            response = createApplicationPermission(applicationOne.getId(), topicOptional.get().getId(), AccessType.READ);
            assertEquals(CREATED, response.getStatus());
            Optional<AccessPermissionDTO> permissionOptional = response.getBody(AccessPermissionDTO.class);
            assertTrue(permissionOptional.isPresent());

            // generate passphrase for application
            request = HttpRequest.GET("/applications/generate-passphrase/" + applicationOne.getId());
            response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            Optional<String> optional = response.getBody(String.class);
            assertTrue(optional.isPresent());

            Map credentials;
            // successful login
            credentials = Map.of(
                    "username", applicationOne.getId().toString(),
                    "password", optional.get()
            );
            request = HttpRequest.POST("/login", credentials);
            response = blockingClient.exchange(request, Map.class);
            assertEquals(SEE_OTHER, response.getStatus());
            Optional<Cookie> jwtOptional = response.getCookie("JWT");
            assertTrue(jwtOptional.isPresent());

            loginAsApplication(applicationOne.getId());

            request = HttpRequest.GET("/applications/permissions.json");
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());
            String originalEtag = response.header(E_TAG_HEADER_NAME);
            assertNotNull(originalEtag);

            // send originalEtag and expect a 304
            request = HttpRequest.GET("/applications/permissions.json").header(E_TAG_HEADER_NAME, originalEtag);
            response = blockingClient.exchange(request);
            assertEquals(NOT_MODIFIED, response.getStatus());

            // switch back to admin to add new permission...
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            // add a new permission
            response = createTopic("Topic789", TopicKind.B, primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            topicOptional = response.getBody(TopicDTO.class);
            assertTrue(topicOptional.isPresent());
            assertEquals("Topic789", topicOptional.get().getName());

            response = createApplicationPermission(applicationOne.getId(), topicOptional.get().getId(), AccessType.READ_WRITE);
            assertEquals(CREATED, response.getStatus());
            permissionOptional = response.getBody(AccessPermissionDTO.class);
            assertTrue(permissionOptional.isPresent());

            loginAsApplication(applicationOne.getId());

            // send originalEtag and expect etag not equal to original and expect defined etag
            request = HttpRequest.GET("/applications/permissions.json").header(E_TAG_HEADER_NAME, originalEtag);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());
            String updatedEtag = response.header(E_TAG_HEADER_NAME);
            assertNotEquals(originalEtag, updatedEtag);
            assertNotNull(updatedEtag);
        }

        @Test
        void canRetrieveClientCertAndPrivateKey() {
            HttpRequest request;
            HttpResponse response;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

            // create application
            response = createApplication("ApplicationOne", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationOneOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationOneOptional.isPresent());
            ApplicationDTO applicationOne = applicationOneOptional.get();

            // generate passphrase for application
            request = HttpRequest.GET("/applications/generate-passphrase/" + applicationOne.getId());
            response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            Optional<String> optional = response.getBody(String.class);
            assertTrue(optional.isPresent());

            Map credentials;
            credentials = Map.of(
                    "username", applicationOne.getId().toString(),
                    "password", optional.get()
            );
            request = HttpRequest.POST("/login", credentials);
            response = blockingClient.exchange(request, Map.class);
            assertEquals(SEE_OTHER, response.getStatus());
            Optional<Cookie> jwtOptional = response.getCookie("JWT");
            assertTrue(jwtOptional.isPresent());

            loginAsApplication(applicationOne.getId());

            // invalid nonce
            request = HttpRequest.GET("/applications/key-pair?nonce=uni_ty");
            HttpRequest finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest, Map.class);
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<List> listOptional = exception.getResponse().getBody(List.class);
            assertTrue(listOptional.isPresent());
            List<Map> list = listOptional.get();
            assertTrue(list.stream().anyMatch(group -> ResponseStatusCodes.INVALID_NONCE_FORMAT.equals(group.get("code"))));

            request = HttpRequest.GET("/applications/key-pair?nonce=unity");
            response = blockingClient.exchange(request, Map.class);
            assertEquals(OK, response.getStatus());
            Optional<Map> bodyOptional = response.getBody(Map.class);
            assertTrue(bodyOptional.isPresent());
            Map map = bodyOptional.get();
            assertTrue(map.containsKey("public"));
            assertTrue(map.containsKey("private"));
        }

        @Test
        void canRetrievePermissions() {
            HttpRequest request;
            HttpResponse response;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

            // create application
            response = createApplication("ApplicationOne", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationOneOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationOneOptional.isPresent());
            ApplicationDTO applicationOne = applicationOneOptional.get();

            // generate passphrase for application
            Long applicationOneId = applicationOne.getId();
            request = HttpRequest.GET("/applications/generate-passphrase/" + applicationOneId);
            response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            Optional<String> optional = response.getBody(String.class);
            assertTrue(optional.isPresent());

            Map credentials;
            credentials = Map.of(
                    "username", applicationOneId.toString(),
                    "password", optional.get()
            );
            request = HttpRequest.POST("/login", credentials);
            response = blockingClient.exchange(request, Map.class);
            assertEquals(SEE_OTHER, response.getStatus());
            Optional<Cookie> jwtOptional = response.getCookie("JWT");
            assertTrue(jwtOptional.isPresent());

            loginAsApplication(applicationOneId);

            // invalid nonce
            request = HttpRequest.GET("/applications/permissions.xml.p7s?nonce=uni_ty");
            HttpRequest finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest, Map.class);
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<List> listOptional = exception.getResponse().getBody(List.class);
            assertTrue(listOptional.isPresent());
            List<Map> list = listOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.INVALID_NONCE_FORMAT.equals(map.get("code"))));

            request = HttpRequest.GET("/applications/permissions.xml.p7s?nonce=unity");
            response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            Optional<String> bodyOptional = response.getBody(String.class);
            assertTrue(bodyOptional.isPresent());
            String body = bodyOptional.get();
            assertTrue(body.contains("CN="+ applicationOneId +"_unity"));
            assertTrue(body.contains("GN="+applicationOneId));
            assertTrue(body.contains("SN="+primaryGroup.getId()));
        }
    }
    @Test
    public void testApplicationFilter() {
        mockSecurityService.postConstruct();
        mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

        HttpRequest<Object> request = HttpRequest.GET("/applications/search?filter=Group");
        List<Map> results = blockingClient.retrieve(request, List.class);

        List<String> expectedApplicationNames = Arrays.asList("GoLang", "Groovy", "Micronaut", "Guitar", "Piano", "Drums", "Paint", "Clay", "Pencil", "Group Psychology");
        assertResultContainsAllExpectedApplicationNames(expectedApplicationNames, results);

        request = HttpRequest.GET("/applications/search?filter=Group&size=7");
        results = blockingClient.retrieve(request, List.class);
        assertEquals(7, results.size());
        assertResultOnlyContainsExpectedApplicationNames(results, expectedApplicationNames);

        request = HttpRequest.GET("/applications/search?filter=Art");
        results = blockingClient.retrieve(request, List.class);

        expectedApplicationNames = Arrays.asList("Paint", "Clay", "Pencil", "Art");
        assertResultContainsAllExpectedApplicationNames(expectedApplicationNames, results);

        request = HttpRequest.GET("/applications/search?filter=Art&size=2");
        results = blockingClient.retrieve(request, List.class);
        assertEquals(2, results.size());
        assertResultOnlyContainsExpectedApplicationNames(results, expectedApplicationNames);

        request = HttpRequest.GET("/applications/search?filter=Clay");
        results = blockingClient.retrieve(request, List.class);
        assertEquals(1, results.size());
        assertEquals("Clay", results.get(0).get("name"));
    }

    @Test
    public void testApplicationExistenceEndpoint() {
        mockSecurityService.postConstruct();
        mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

        HttpResponse<?> response;
        HttpRequest<?> request;

        // create group
        response = createGroup("PrimaryGroup");
        assertEquals(OK, response.getStatus());
        Optional<Group> primaryOptional = response.getBody(Group.class);
        assertTrue(primaryOptional.isPresent());
        Group primaryGroup = primaryOptional.get();

        // create application
        response = createApplication("TestApplication", primaryGroup.getId());
        assertEquals(OK, response.getStatus());
        Optional<ApplicationDTO> applicationOptional = response.getBody(ApplicationDTO.class);
        assertTrue(applicationOptional.isPresent());

        // create application
        response = createApplication("TestApplicationOne", primaryGroup.getId());
        assertEquals(OK, response.getStatus());
        Optional<ApplicationDTO> applicationOneOptional = response.getBody(ApplicationDTO.class);
        assertTrue(applicationOneOptional.isPresent());

        // found
        request = HttpRequest.GET("/applications/check_exists/TestApplication");
        response = blockingClient.exchange(request, ApplicationDTO.class);
        assertEquals(OK, response.getStatus());
        Optional<ApplicationDTO> applicationDTO = response.getBody(ApplicationDTO.class);
        assertTrue(applicationDTO.isPresent());
        assertEquals("TestApplication", applicationDTO.get().getName());

        // case insensitive
        request = HttpRequest.GET("/applications/check_exists/testapplication");
        response = blockingClient.exchange(request, ApplicationDTO.class);
        assertEquals(OK, response.getStatus());
        Optional<ApplicationDTO> applicationCaseInsensitiveDTO = response.getBody(ApplicationDTO.class);
        assertTrue(applicationCaseInsensitiveDTO.isPresent());
        assertEquals("TestApplication", applicationCaseInsensitiveDTO.get().getName());

        // not found
        request = HttpRequest.GET("/applications/check_exists/Application");
        HttpRequest<?> finalRequest = request;
        HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
            blockingClient.exchange(finalRequest, ApplicationDTO.class);
        });
        assertEquals(NOT_FOUND, exception.getStatus());
        Optional<List> bodyOptional = exception.getResponse().getBody(List.class);
        assertTrue(bodyOptional.isPresent());
        List<Map> list = bodyOptional.get();
        assertTrue(list.stream().anyMatch(group -> ResponseStatusCodes.APPLICATION_NOT_FOUND.equals(group.get("code"))));

        // application is empty string
        request = HttpRequest.GET("/applications/check_exists/%20");
        HttpRequest<?> finalRequest1 = request;
        HttpClientResponseException exception1 = assertThrowsExactly(HttpClientResponseException.class, () -> {
            blockingClient.exchange(finalRequest1, ApplicationDTO.class);
        });
        assertEquals(BAD_REQUEST, exception1.getStatus());
        bodyOptional = exception1.getResponse().getBody(List.class);
        assertTrue(bodyOptional.isPresent());
        list = bodyOptional.get();
        assertTrue(list.stream().anyMatch(group -> ResponseStatusCodes.APPLICATION_NAME_CANNOT_BE_BLANK_OR_NULL.equals(group.get("code"))));

        // application is null
        request = HttpRequest.GET("/applications/check_exists/");
        HttpRequest<?> finalRequest2 = request;
        HttpClientResponseException exception2 = assertThrowsExactly(HttpClientResponseException.class, () -> {
            blockingClient.exchange(finalRequest2, ApplicationDTO.class);
        });
        assertEquals(NOT_FOUND, exception2.getStatus()); // Invalid URL
    }

    private void assertResultOnlyContainsExpectedApplicationNames(List<Map> results, List<String> expectedApplicationNames) {
        assertTrue(results.stream().allMatch((m) -> expectedApplicationNames.contains(m.get("name"))));
    }

    private void assertResultContainsAllExpectedApplicationNames(List<String> expectedApplicationNames, List<Map> results) {
        List<String> applicationNames = results.stream().map(m -> (String)m.get("name")).collect(Collectors.toList());

        assertEquals(expectedApplicationNames.size(), applicationNames.size());
        assertTrue(expectedApplicationNames.containsAll(applicationNames));
    }

    private HttpResponse<?> createGroup(String groupName) {
        Group group = new Group(groupName);
        HttpRequest<?> request = HttpRequest.POST("/groups/save", group);
        HttpResponse<?> response;
        return blockingClient.exchange(request, Group.class);
    }

    private HttpResponse<?> createApplication(String applicationName, Long groupId) {
        ApplicationDTO applicationDTO = new ApplicationDTO();
        applicationDTO.setName(applicationName);
        applicationDTO.setGroup(groupId);

        HttpRequest<?> request = HttpRequest.POST("/applications/save", applicationDTO);
        return blockingClient.exchange(request, ApplicationDTO.class);
    }

    private HttpResponse<?> createTopic(String topicName, TopicKind topicKind, Long groupId) {
        TopicDTO topicDTO = new TopicDTO();
        topicDTO.setName(topicName);
        topicDTO.setGroup(groupId);
        topicDTO.setKind(topicKind);

        HttpRequest<?> request = HttpRequest.POST("/topics/save", topicDTO);
        return blockingClient.exchange(request, TopicDTO.class);
    }

    private HttpResponse<?> createApplicationPermission(Long applicationId, Long topicId, AccessType accessType) {
        HttpRequest<?> request = HttpRequest.POST("/application_permissions/" + applicationId + "/" + topicId + "/" + accessType.name(), Map.of());
        return blockingClient.exchange(request, AccessPermissionDTO.class);
    }
}
