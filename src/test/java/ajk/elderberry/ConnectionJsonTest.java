package ajk.elderberry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

import static java.util.Collections.singletonList;
import static org.fest.assertions.Assertions.assertThat;

public class ConnectionJsonTest {
    @Test
    public void serialize() throws URISyntaxException, JsonProcessingException {
        TaxiiConnection c = new TaxiiConnection();
        c.setDiscoveryUri(new URI("http://www.google.com"));
        c.setTrustedPemCertificates(singletonList("trusted certificate"));
        c.setClientCertificatePemChain(singletonList("client certificate"));
        c.setPrivateKeyPem("private key");
        c.setKeyStoreFile(new File("/dev/null"));
        c.setKeyStorePassword("key store password");
        c.setUsername("user");
        c.setPassword("password");
        c.setProxyHost("web-proxy");
        c.setProxyPort(8888);

        ObjectMapper mapper = new ObjectMapper();

        assertThat(mapper.writeValueAsString(c))
                .isNotEmpty()
                .contains("key store password")
                .contains("private key")
                .contains("web-proxy");
    }
}
