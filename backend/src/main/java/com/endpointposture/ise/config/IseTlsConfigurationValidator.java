package com.endpointposture.ise.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Rejects disabled ISE certificate verification in every application profile. */
@Component
public class IseTlsConfigurationValidator {

    public IseTlsConfigurationValidator(IseProperties properties, Environment environment) {
        validate(properties, environment.getActiveProfiles());
    }

    static void validate(IseProperties properties, String... activeProfiles) {
        if (!properties.isVerifyTls()) {
            throw new IllegalStateException(
                    "ISE TLS certificate verification must be enabled; remove app.ise.verify-tls=false");
        }
        if (properties.isConfigured()
                && (properties.getUsername() == null || properties.getUsername().isBlank()
                || properties.getPassword() == null || properties.getPassword().isBlank())) {
            throw new IllegalStateException(
                    "ISE username and password must be configured when app.ise.base-url is set");
        }
    }
}
