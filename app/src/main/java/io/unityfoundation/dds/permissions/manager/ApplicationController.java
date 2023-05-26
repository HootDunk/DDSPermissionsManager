package io.unityfoundation.dds.permissions.manager;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.unityfoundation.dds.permissions.manager.exception.DPMErrorResponse;
import io.unityfoundation.dds.permissions.manager.exception.DPMException;
import io.unityfoundation.dds.permissions.manager.model.application.ApplicationDTO;
import io.unityfoundation.dds.permissions.manager.model.application.ApplicationService;
import org.bouncycastle.mail.smime.SMIMEException;
import org.bouncycastle.operator.OperatorCreationException;

import javax.mail.MessagingException;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;

import static io.unityfoundation.dds.permissions.manager.model.application.ApplicationService.E_TAG_HEADER_NAME;

@Controller("/api/applications")
@Secured(SecurityRule.IS_AUTHENTICATED)
@Tag(name = "application")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @Get("{?filter,group}")
    public Page<ApplicationDTO> index(@Valid Pageable pageable, @Nullable String filter, @Nullable Long group) {
        return applicationService.findAll(pageable, filter, group);
    }

    @Get("/show/{id}")
    @ApiResponse(
            responseCode = "200",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApplicationDTO.class))
    )
    @ExecuteOn(TaskExecutors.IO)
    public HttpResponse show(Long id) {
        return applicationService.show(id);
    }

    @Post("/save")
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiResponse(
            responseCode = "200",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApplicationDTO.class))
    )
    @ApiResponse(responseCode = "4xx", description = "Bad Request.",
            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = DPMErrorResponse.class)))
    )
    @ExecuteOn(TaskExecutors.IO)
    HttpResponse<?> save(@Body @Valid ApplicationDTO application) {
        return applicationService.save(application);
    }

    @Post("/delete/{id}")
    @ApiResponse(responseCode = "303", description = "Returns result of /applications")
    @ApiResponse(responseCode = "4xx", description = "Bad Request.",
            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = DPMErrorResponse.class)))
    )
    @ExecuteOn(TaskExecutors.IO)
    HttpResponse<?> delete(Long id) {
        return applicationService.deleteById(id);
    }

    @Get("/generate_bind_token/{applicationId}")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "text/plain"))
    @ApiResponse(responseCode = "401", description = "Not authorized.",
            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = DPMErrorResponse.class)))
    )
    @ApiResponse(responseCode = "404", description = "Application not found.",
            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = DPMErrorResponse.class)))
    )
    @Produces(MediaType.TEXT_PLAIN)
    @ExecuteOn(TaskExecutors.IO)
    public HttpResponse generateBindToken(Long applicationId) {
        return applicationService.generateBindToken(applicationId);
    }

    @Get("/generate_passphrase/{application}")
    @ExecuteOn(TaskExecutors.IO)
    public HttpResponse<?> generatePassphrase(@NonNull Long application) {
        return applicationService.generateCleartextPassphrase(application);
    }

    @Get("/identity_ca.pem")
    @Secured("APPLICATION")
    @ExecuteOn(TaskExecutors.IO)
    public HttpResponse<?> getIdentityCACertificate(@Nullable @Header(E_TAG_HEADER_NAME) String etag) {
        return applicationService.getIdentityCACertificate(etag);
    }

    @Get("/permissions_ca.pem")
    @Secured("APPLICATION")
    @ExecuteOn(TaskExecutors.IO)
    public HttpResponse<?> getPermissionsCACertificate(@Nullable @Header(E_TAG_HEADER_NAME) String etag) {
        return applicationService.getPermissionsCACertificate(etag);
    }

    @Get("/governance.xml.p7s")
    @Secured("APPLICATION")
    @ExecuteOn(TaskExecutors.IO)
    public HttpResponse<?> getGovernanceFile(@Nullable @Header(E_TAG_HEADER_NAME) String etag) {
        return applicationService.getGovernanceFile(etag);
    }

    @Get("/key_pair{?nonce}")
    @Secured("APPLICATION")
    @ExecuteOn(TaskExecutors.IO)
    public HttpResponse<?> getPrivateKeyAndClientCertificate(@Nullable String nonce) throws IOException, OperatorCreationException, GeneralSecurityException {
        checkNonceValidFormat(nonce);
        return applicationService.getApplicationPrivateKeyAndClientCertificate(nonce);
    }

    @Get("/permissions.xml.p7s{?nonce}")
    @Secured("APPLICATION")
    @ExecuteOn(TaskExecutors.IO)
    public HttpResponse<?> getPermissionsFile(@Nullable String nonce) throws IOException, OperatorCreationException, GeneralSecurityException, MessagingException, SMIMEException {
        checkNonceValidFormat(nonce);
        return applicationService.getPermissionsFile(nonce);
    }

    @Get("/permissions.json")
    @Secured("APPLICATION")
    @ExecuteOn(TaskExecutors.IO)
    public HttpResponse<?> getPermissionsJson(@Nullable @Header(E_TAG_HEADER_NAME) String etag) throws NoSuchAlgorithmException {
        return applicationService.getPermissionJson(etag);
    }

    private void checkNonceValidFormat(String nonce) {
        if (nonce == null || !nonce.matches("^[a-zA-Z0-9]*$")) {
            throw new DPMException(ResponseStatusCodes.INVALID_NONCE_FORMAT);
        }
    }
}
