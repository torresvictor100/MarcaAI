package com.marcaai.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class AcessoMetricasTest {

    private static final Authentication ANONIMO = new AnonymousAuthenticationToken("k", "anon", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

    private static boolean pode(AcessoMetricas acesso, String header, Authentication autenticacao) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        if (header != null) {
            request.addHeader("Authorization", header);
        }
        return acesso.check(() -> autenticacao, new RequestAuthorizationContext(request)).isGranted();
    }

    private static String basic(String usuario, String senha) {
        return "Basic " + Base64.getEncoder().encodeToString((usuario + ":" + senha).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void prometheusEntraSoComACredencialCerta() {
        AcessoMetricas acesso = new AcessoMetricas("segredo");

        assertThat(pode(acesso, basic("prometheus", "segredo"), ANONIMO)).isTrue();
        assertThat(pode(acesso, basic("prometheus", "outro"), ANONIMO)).isFalse();
        assertThat(pode(acesso, basic("admin", "segredo"), ANONIMO)).isFalse();
        assertThat(pode(acesso, null, ANONIMO)).isFalse();
        assertThat(pode(acesso, null, null)).isFalse();
    }

    @Test
    void semTokenConfiguradoSoOAdminEntra() {
        AcessoMetricas semToken = new AcessoMetricas("");
        Authentication admin = new UsernamePasswordAuthenticationToken("admin", null, AuthorityUtils.createAuthorityList("ROLE_ADMIN"));
        Authentication secretaria = new UsernamePasswordAuthenticationToken("s", null, AuthorityUtils.createAuthorityList("ROLE_SECRETARIA"));

        assertThat(pode(semToken, basic("prometheus", ""), ANONIMO)).isFalse();
        assertThat(pode(semToken, null, admin)).isTrue();
        assertThat(pode(semToken, null, secretaria)).isFalse();
        assertThat(pode(new AcessoMetricas(null), null, admin)).isTrue();
    }
}
