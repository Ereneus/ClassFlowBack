package com.ohiggins.classflow.auth.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EntraSecurityConfigTest {

    private static final String TENANT = "981c9c3d-2cb3-493c-8b5e-bcf013a445e3";

    @Test
    void v1IssuerAlsoAcceptsV2IssuerOfSameTenant() {
        assertThat(EntraSecurityConfig.acceptedIssuers("https://sts.windows.net/" + TENANT + "/"))
                .containsExactlyInAnyOrder(
                        "https://sts.windows.net/" + TENANT + "/",
                        "https://login.microsoftonline.com/" + TENANT + "/v2.0");
    }

    @Test
    void v2IssuerAlsoAcceptsV1IssuerOfSameTenant() {
        assertThat(EntraSecurityConfig.acceptedIssuers("https://login.microsoftonline.com/" + TENANT + "/v2.0"))
                .containsExactlyInAnyOrder(
                        "https://sts.windows.net/" + TENANT + "/",
                        "https://login.microsoftonline.com/" + TENANT + "/v2.0");
    }

    @Test
    void issuerWithoutTenantIdIsAcceptedAsIs() {
        assertThat(EntraSecurityConfig.acceptedIssuers("https://issuer.example.com"))
                .containsExactly("https://issuer.example.com");
    }
}
