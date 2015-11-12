/*
 * (c) Copyright 2015 Hewlett Packard Enterprise Development LP Licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the
 * License.
 */
package com.hpe.elderberry;

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
