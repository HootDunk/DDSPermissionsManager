package io.unityfoundation.dds.permissions.manager.exception;

import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.unityfoundation.dds.permissions.manager.ResponseStatusCodes;
import io.unityfoundation.dds.permissions.manager.security.PassphraseGenerator;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.constraints.*;
import javax.validation.metadata.ConstraintDescriptor;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Produces
@Singleton
@Primary
@Requires(classes = {ConstraintViolationException.class, ExceptionHandler.class})
public class DTOConstraintViolationExceptionHandler implements ExceptionHandler<ConstraintViolationException, HttpResponse> {

    private static final Logger LOG = LoggerFactory.getLogger(DTOConstraintViolationExceptionHandler.class);

    private final PassphraseGenerator passphraseGenerator;

    public DTOConstraintViolationExceptionHandler(PassphraseGenerator passphraseGenerator) {
        this.passphraseGenerator = passphraseGenerator;
    }

    @Override
    public HttpResponse<?> handle(HttpRequest request, ConstraintViolationException exception) {
        Set<ConstraintViolation<?>> constraintViolations = exception.getConstraintViolations();
        MutableHttpResponse<?> response = HttpResponse.badRequest();
        response.body(buildBody(constraintViolations));
        exception.printStackTrace();
        return response;
    }

    private List<DPMErrorResponse> buildBody(Set<ConstraintViolation<?>> errorContext) {
        return errorContext.stream().map(this::buildMessageBody).collect(Collectors.toList());
    }

    private DPMErrorResponse buildMessageBody(ConstraintViolation violation) {
        String code = violation.getPropertyPath().toString();
        ConstraintDescriptor descriptor = violation.getConstraintDescriptor();

        if (code.endsWith(".email")) {
            if (descriptor.getAnnotation() instanceof Email) {
                code = ResponseStatusCodes.INVALID_EMAIL_FORMAT;
            } else if (descriptor.getAnnotation() instanceof NotBlank) {
                code = ResponseStatusCodes.EMAIL_CANNOT_BE_BLANK_OR_NULL;
            }
        } else if (code.contains(".group.")) {
            // group
            if (code.endsWith(".name")) {
                if (descriptor.getAnnotation() instanceof NotBlank) {
                    code = ResponseStatusCodes.GROUP_NAME_CANNOT_BE_BLANK_OR_NULL;
                } else if (descriptor.getAnnotation() instanceof Size) {
                    code = ResponseStatusCodes.GROUP_NAME_CANNOT_BE_LESS_THAN_THREE_CHARACTERS;
                }
            }
        } else if (code.contains(".application.") || code.contains("checkApplicationExistence.application")) {
            if (code.endsWith(".name")) {
                if (descriptor.getAnnotation() instanceof NotBlank) {
                    code = ResponseStatusCodes.APPLICATION_NAME_CANNOT_BE_BLANK_OR_NULL;
                } else if (descriptor.getAnnotation() instanceof Size) {
                    code = ResponseStatusCodes.APPLICATION_NAME_CANNOT_BE_LESS_THAN_THREE_CHARACTERS;
                }
            } else if (code.endsWith(".group")) {
                if (descriptor.getAnnotation() instanceof NotNull) {
                    code = ResponseStatusCodes.APPLICATION_REQUIRES_GROUP_ASSOCIATION;
                }
            } else if (code.contains("checkApplicationExistence.application")) {
                if (descriptor.getAnnotation() instanceof NotBlank) {
                    code = ResponseStatusCodes.APPLICATION_NAME_CANNOT_BE_BLANK_OR_NULL;
                } else if (descriptor.getAnnotation() instanceof Size) {
                    code = ResponseStatusCodes.APPLICATION_NAME_CANNOT_BE_LESS_THAN_THREE_CHARACTERS;
                }
            }
        } else if (code.contains(".topic.")) {
            if (code.endsWith(".id")) {
                if (descriptor.getAnnotation() instanceof Null) {
                    code = ResponseStatusCodes.TOPIC_UPDATE_NOT_ALLOWED;
                }
            } else if (code.endsWith(".name")) {
                if (descriptor.getAnnotation() instanceof NotBlank) {
                    code = ResponseStatusCodes.TOPIC_NAME_CANNOT_BE_BLANK_OR_NULL;
                } else if (descriptor.getAnnotation() instanceof Size) {
                    code = ResponseStatusCodes.TOPIC_NAME_CANNOT_BE_LESS_THAN_THREE_CHARACTERS;
                }
            } else if (code.endsWith(".group")) {
                if (descriptor.getAnnotation() instanceof NotNull) {
                    code = ResponseStatusCodes.TOPIC_REQUIRES_GROUP_ASSOCIATION;
                }
            }
        } else if (code.endsWith("dto.permissionsGroup")) {
            if (descriptor.getAnnotation() instanceof NotNull) {
                code = ResponseStatusCodes.GROUP_MEMBERSHIP_REQUIRES_GROUP_ASSOCIATION;
            }
        }

        String errorId = passphraseGenerator.generatePassphrase();
        LOG.error("Id: " + errorId +
                " Code: " + code +
                " Violation: " + violation.getMessage() +
                ". See exception below:");

        return new DPMErrorResponse(errorId, code);
    }

}
