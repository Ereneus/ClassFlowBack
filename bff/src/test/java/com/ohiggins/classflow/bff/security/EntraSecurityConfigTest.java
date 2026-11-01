package com.ohiggins.classflow.bff.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EntraSecurityConfigTest {

    private static final String TENANT = "981c9c3d-2cb3-493c-8b5e-bcf013a445e3";

    @Test
    void acceptsV1AndV2IssuersForTheConfiguredTenant() {
        assertThat(EntraSecurityConfig.acceptedIssuers(
                "https://sts.windows.net/" + TENANT + "/"))
                .containsExactlyInAnyOrder(
                        "https://sts.windows.net/" + TENANT + "/",
                        "https://login.microsoftonline.com/" + TENANT + "/v2.0");
    }
}
