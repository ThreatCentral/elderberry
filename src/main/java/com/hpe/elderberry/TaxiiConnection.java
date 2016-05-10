/*
 * (c) Copyright 2015 Hewlett Packard Enterprise Development LP Licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the
 * License.
 */
package com.hpe.elderberry;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.logging.Log;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContexts;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.xml.MarshallingHttpMessageConverter;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.net.URI;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.*;
import static java.nio.file.Files.newInputStream;
import static java.security.KeyStore.getInstance;
import static java.util.Collections.singletonList;
import static java.util.UUID.randomUUID;
import static javax.xml.bind.DatatypeConverter.parseBase64Binary;
import static org.apache.commons.io.IOUtils.toInputStream;
import static org.apache.commons.logging.LogFactory.getLog;
import static org.apache.http.impl.client.HttpClients.custom;
import static org.springframework.http.MediaType.APPLICATION_XML;
import static org.springframework.util.StringUtils.isEmpty;

/**
 * The {@code TaxiiConnection} supports the following features:<br>
 * <ul>
 * <li>HTTP Proxy</li>
 * <li>Basic authentication</li>
 * <li>SSL certificate authentication</li>
 * <li>custom JAXB marshaller</li>
 * <li>custom RestTemplate</li>
 * </ul>
 * <p>
 * The SSL certificate authentication can be used by supplying the trust store and key store, or by supplying the
 * certificate in PEM format.
 * <br>
 * The following is an example of a private key in PEM PKCS#8 format:<br>
 * <pre>
 * {@code -----BEGIN PRIVATE KEY-----
 *   c29tZSB0ZXh0IHRvIG1pbWljIGEgYmFzZSA2NCBlbmNvZGVkIGtleQo
 *   ...encoded text....
 *   YW5kIGV2ZW4gbW9yZSB0ZXh0IHRvIHNpbXVsYXRlIHRoZSBzYW1lIHR
 *   oaW5nCg==
 *   -----END PRIVATE KEY-----}
 * </pre>
 * <p>
 * And the following is an example of a certificate in PEM format:<br>
 * <pre>
 * {@code -----BEGIN CERTIFICATE-----
 *   c29tZSB0ZXh0IHRvIG1pbWljIGEgYmFzZSA2NCBlbmNvZGVkIGtleQo
 *   ...encoded text....
 *   YW5kIGV2ZW4gbW9yZSB0ZXh0IHRvIHNpbXVsYXRlIHRoZSBzYW1lIHR
 *   oaW5nCg==
 *   -----END CERTIFICATE-----}
 * </pre>
 * <p>
 * There are several options to configure 2-way SSL authentication when the TAXII server requires SSL authentication:
 * <ul>
 * <li>Directly set the key store and trust store as {@link KeyStore} objects. Using this option you can construct the key
 * and trust store in any way you want and provide it to the connection. The key store is expected to contain the client
 * certificate, the client certificate private key and the client certificate chain. The trust store is expected to
 * contain the server trusted certificates. To use this option use {@link #setKeyStore(KeyStore, String)} and
 * {@link #setTrustStore(KeyStore)}</li>
 * <li>Provide the key store file and password and trust store file and password. This is very similar to the previous
 * option, except that the {@code }TaxiiConnection} takes care of loading the key and trust stores from the files
 * provided. To use this option use {@link #setKeyStoreFile(File)}, {@link #setKeyStorePassword(String)} to set the
 * key store and {@link #setTrustStoreFile(File)} and {@link #setTrustStorePassword(String)} to set the trust store.</li>
 * <li>Provide the client and server certificates using PEM strings. This is probably the most convenient way to set up
 * the SSL connection because you're likely to have PEM certificates and private key rather than JKS stores. To set the
 * client certificate provide the private key as PEM with {@link #setPrivateKeyPem(String)} and provide the client
 * certificate chain as PEM with {@link #setClientCertificatePemChain(List)}. Provide the trusted certificates with
 * {@link #setTrustedPemCertificates(List)}.<br>When constructing the key store and trust store from PEMs you can
 * retrieve the constructed stores back from the connection. This could be useful when caching the store locally.</li>
 * </ul>
 * <br>
 * The TaxiiConnection can be used with both {@link Taxii11Template} and {@link Taxii10Template}.
 */
@SuppressWarnings("unused")
@JsonAutoDetect(fieldVisibility = ANY)
public class TaxiiConnection {
    private URI discoveryUrl;
    private String username = "";
    private String password = "";
    private boolean useProxy = false;
    private String proxyHost = "";
    private int proxyPort;
    private File keyStoreFile;
    private File trustStoreFile;
    private String keyStorePassword;
    private String trustStorePassword;
    private String privateKeyPem;
    private List<String> clientCertificatePemChain;
    private List<String> trustedPemCertificates;

    @JsonIgnore
    private Log log = getLog(getClass());

    @JsonIgnore
    private char[] keyPassword;

    @JsonIgnore
    private KeyStore keyStore;

    @JsonIgnore
    private KeyStore trustStore;

    @JsonIgnore
    private Jaxb2Marshaller marshaller;

    @JsonIgnore
    private RestTemplate restTemplate;

    /**
     * optional key store that contains your private key to be sent when the server is using SSL certificate-based
     * authentication. It's expected that the key store holds a single private key and its supporting certificates
     *
     * @param keyStore    a standard Java key store
     * @param keyPassword the private key password
     */
    public void setKeyStore(KeyStore keyStore, String keyPassword) {
        this.keyStore = keyStore;
        this.keyPassword = keyPassword == null ? "".toCharArray() : keyPassword.toCharArray();
    }

    /**
     * optional trust store that contains the TAXII server's trusted certificates
     *
     * @param trustStore a trust store (which is a standard Java key store)
     */
    public void setTrustStore(KeyStore trustStore) {
        this.trustStore = trustStore;
    }

    /**
     * optional key store password, used when the key store is set as a file using {@link #setKeyStoreFile(File)}
     *
     * @param keyStorePassword the key store password
     */
    public void setKeyStorePassword(String keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
    }

    /**
     * optional key store file. If set, the key store is loaded from this file using the password set by
     * {@link #setKeyStorePassword(String)}
     *
     * @param keyStoreFile the key store file
     */
    public void setKeyStoreFile(File keyStoreFile) {
        this.keyStoreFile = keyStoreFile;
    }

    /**
     * optional trust store file. If set, the trust store is loaded from the file using the password set by
     * {@link #setTrustStorePassword(String)}
     *
     * @param trustStoreFile the trust store file
     */
    public void setTrustStoreFile(File trustStoreFile) {
        this.trustStoreFile = trustStoreFile;
    }

    /**
     * optional trust store password, used when the trust store is set as a file using {@link #setTrustStoreFile(File)}
     *
     * @param trustStorePassword the trust store password
     */
    public void setTrustStorePassword(String trustStorePassword) {
        this.trustStorePassword = trustStorePassword;
    }

    /**
     * sets the private key of the client certificate chain. Use this together with
     * {@link #setClientCertificatePemChain(List)}
     *
     * @param keyPem the private key PEM text
     */
    public void setPrivateKeyPem(String keyPem) {
        this.privateKeyPem = keyPem;
    }

    /**
     * sets the client certificate chain
     *
     * @param clientCertificatePemChain the client certificate chain in PEM format. The client certificate itself is
     *                                  expected to be the first item. Note that this is an ordered list of certificates
     *                                  in PEM format, according to their order in the chain bottom to top
     */
    public void setClientCertificatePemChain(List<String> clientCertificatePemChain) {
        this.clientCertificatePemChain = clientCertificatePemChain;
    }

    /**
     * sets the trusted certificates. These certificates represent the server-side trusted certificates
     *
     * @param trustedPemCertificates the trusted certificates in PEM format
     */
    public void setTrustedPemCertificates(List<String> trustedPemCertificates) {
        this.trustedPemCertificates = trustedPemCertificates;
    }

    private List<Certificate> addPemsToStore(KeyStore store, List<String> pems) throws CertificateException {
        List<Certificate> result = new ArrayList<>(pems.size());

        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        pems.forEach(pem -> {
            try {
                X509Certificate cert = (X509Certificate) factory.generateCertificate(toInputStream(pem));
                store.setCertificateEntry(randomUUID().toString(), cert);
                result.add(cert);
            } catch (Exception e) {
                throw new RuntimeException("unable to load PEM: " + pem + ", " + e.getMessage(), e);
            }
        });

        return result;
    }

    /**
     * a convenient way to externally cache the key store is to create it from PEMs using
     * {@link #setPrivateKeyPem(String)} and {@link #setClientCertificatePemChain(List)} then obtaining it with this method and
     * storing it for future use.<p>
     * This method attempts to retrieve the key store in the following algorithm:
     * <ul>
     * <li>When it was already loaded or constructed it's returned</li>
     * <li>When it was directly set by {@link #setKeyStore(KeyStore, String)} then this key store is returned</li>
     * <li>When a key store file and password was set by {@link #setKeyStoreFile(File)} and
     * {@link #setKeyStorePassword(String)}, then the key store is loaded from the file and returned</li>
     * <li>When a private key was set by {@link #setPrivateKeyPem(String)} and its certificate chain was set by
     * {@link #setClientCertificatePemChain(List)} then a new key store is created, the private key material and the
     * client certificates are loaded into it, then this new key store is returned</li>
     * </ul>
     *
     * @return the key store
     */
    public KeyStore getKeyStore() {
        if (keyStore != null) {
            return keyStore;
        }

        if (keyStoreFile != null) {
            try {
                keyStore = getInstance("JKS");
                keyStore.load(newInputStream(keyStoreFile.toPath()),
                        keyStorePassword == null ? "".toCharArray() : keyStorePassword.toCharArray());
            } catch (Exception e) {
                throw new RuntimeException("a key store file was set, but it could not be read, " + e.getMessage(), e);
            }
        } else if (!isEmpty(privateKeyPem)) {
            try {
                // initialize an empty key store
                keyStore = getInstance("JKS");
                keyStore.load(null);

                // generate a random password for the private key
                keyPassword = randomUUID().toString().toCharArray();

                // load the private key
                byte[] key = parseBase64Binary(privateKeyPem.replaceAll("-+.*-+", ""));
                PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(key));
                if (clientCertificatePemChain != null) {
                    List<Certificate> chain = addPemsToStore(keyStore, clientCertificatePemChain);
                    keyStore.setKeyEntry(randomUUID().toString(),
                            privateKey,
                            keyPassword,
                            chain.toArray(new Certificate[chain.size()]));
                } else {
                    keyStore.setKeyEntry(randomUUID().toString(),
                            privateKey,
                            keyPassword,
                            new Certificate[]{});
                }
            } catch (Exception e) {
                throw new RuntimeException("unable to create key store, " + e.getMessage(), e);
            }
        }

        return keyStore;
    }

    /**
     * a convenient way to externally cache the trust store is to create it from PEMs using
     * {@link #setTrustedPemCertificates(List)} then obtaining it with this method and storing it for future use.<p>
     * This method attempts to retrieve the trust store using the following algorithm:
     * <ul>
     * <li>When it was already loaded or constructed it's returned</li>
     * <li>When it was directly set by {@link #setTrustStore(KeyStore)} then this trust store is returned</li>
     * <li>When a trust store file and password was set by {@link #setTrustStoreFile(File)} and
     * {@link #setTrustStorePassword(String)}, then the trust store is loaded from the file and returned</li>
     * <li>When a trusted list of certificates was set by {@link #setTrustedPemCertificates(List)} then a new trust store
     * is created and the trusted certificates are loaded into it and then this new trust store is returned</li>
     * </ul>
     *
     * @return the trust store
     */
    public KeyStore getTrustStore() {
        if (trustStore != null) {
            return trustStore;
        }

        if (trustStoreFile != null) {
            try {
                trustStore = getInstance("JKS");
                trustStore.load(newInputStream(trustStoreFile.toPath()),
                        trustStorePassword == null ? "".toCharArray() : trustStorePassword.toCharArray());
            } catch (Exception e) {
                throw new RuntimeException("a trust store file was set, but it could not be read, " + e.getMessage(), e);
            }
        } else if (!isEmpty(trustedPemCertificates)) {

            try {
                // initialize an empty trust store
                trustStore = getInstance("JKS");
                trustStore.load(null);

                // add all PEMs as trusted certificates to the in-memory trust store
                addPemsToStore(trustStore, trustedPemCertificates);
            } catch (Exception e) {
                throw new RuntimeException("unable to create trust store, " + e.getMessage(), e);
            }
        }

        return trustStore;
    }

    /**
     * when creating the key store from PEMs the key is stored with a randomly generated password. Use this method to
     * obtain this random password if you want to externally cache and re-use the key store
     *
     * @return the private key password
     */
    public String getKeyPassword() {
        return new String(keyPassword);
    }

    /**
     * the only required property. Set this URL to point to the discovery URL of your TAXII server.
     *
     * @param discoveryUrl For example: https://threatcentral.io/tc/taxii/discovery
     */
    @Required
    public void setDiscoveryUri(URI discoveryUrl) {
        this.discoveryUrl = discoveryUrl;
    }

    /**
     * optional username for servers that require basic authentication
     *
     * @param username if left blank preemptive basic authentication is not set up
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * optional password for servers that require basic authentication
     *
     * @param password can be left blank, some services don't require a password, only a username
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * a flag to request the use of an http/s proxy to access the TAXII server
     *
     * @param useProxy when true the connection will attempt to set up an http/s proxy, depending on the
     *                 {@link #setDiscoveryUri(URI)}. This is considered a request because it depends on the
     *                 {@link #setProxyHost(String)}, see details there.
     */
    public void setUseProxy(boolean useProxy) {
        this.useProxy = useProxy;
    }

    /**
     * optional proxy hostname
     *
     * @param proxyHost if useProxy is true and this property is not specified, then the template
     *                  will attempt to obtain the hostname from the system property http.proxyHost or https.proxyHost,
     *                  depending on the discoveryUrl scheme
     */
    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    /**
     * optional proxy port
     *
     * @param proxyPort if useProxy is true and this property is not specified, then the template 
     *                  attempts to obtain the port from the system property http.proxyPort or https.proxyPort, depending
     *                  on the discoveryUrl scheme
     */
    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    /**
     * an optional <code>Jaxb2Marshaller</code>
     *
     * @param marshaller if not provided then the template will create its own marshaller. This marshaller is expected
     *                   to be able to marshal and unmarshal TAXII 1.1 XMLs into and from objects
     */
    public void setMarshaller(Jaxb2Marshaller marshaller) {
        this.marshaller = marshaller;
    }

    /**
     * an optional <code>RestTemplate</code>
     *
     * @param restTemplate if not provided then the template will create its own rest template configured with the
     *                     marshaller
     */
    public void setRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public URI getDiscoveryUrl() {
        return discoveryUrl;
    }

    public RestTemplate getRestTemplate() {
        if (restTemplate == null) {
            HttpClientBuilder builder = custom();

            if (useProxy) {
                if ("".equals(proxyHost)) {
                    proxyHost = System.getProperty(discoveryUrl.getScheme() + ".proxyHost");
                }

                if (proxyPort == 0) {
                    proxyPort = Integer.parseInt(System.getProperty(discoveryUrl.getScheme() + ".proxyPort", "0"));
                }

                if ("".equals(proxyHost) || proxyHost == null || proxyPort == 0) {
                    log.warn("proxy requested, but not setup, not using a proxy");
                } else {
                    log.info("using " + discoveryUrl.getScheme() + " proxy: " + proxyHost + ":" + proxyPort);
                    HttpHost proxy = new HttpHost(proxyHost, proxyPort);
                    DefaultProxyRoutePlanner routePlanner = new DefaultProxyRoutePlanner(proxy);
                    builder.setRoutePlanner(routePlanner);
                }
            }

            if (getTrustStore() != null || getKeyStore() != null) {
                SSLContext sslContext;
                try {
                    sslContext = SSLContexts.custom()
                            .loadTrustMaterial(getTrustStore(), new TrustSelfSignedStrategy())
                            .loadKeyMaterial(getKeyStore(), keyPassword)
                            .build();
                } catch (Exception e) {
                    log.error("unable to create SSL context, " + e.getMessage(), e);
                    throw new RuntimeException(e);
                }
                SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext);
                builder.setSSLSocketFactory(sslsf);
            }

            if (!"".equals(username)) {
                restTemplate = new RestTemplate(new PreemptiveAuthHttpRequestFactor(username, password, builder.build()));
            } else {
                restTemplate = new RestTemplate(new HttpComponentsClientHttpRequestFactory(builder.build()));
            }

            if (marshaller == null) {
                marshaller = new Jaxb2Marshaller();
                marshaller.setPackagesToScan("org.mitre");
                try {
                    marshaller.afterPropertiesSet();
                } catch (Exception e) {
                    log.error("unable to create Jaxb2 Marshaller: " + e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            }

            MarshallingHttpMessageConverter converter = new MarshallingHttpMessageConverter(marshaller);
            converter.setSupportedMediaTypes(singletonList(APPLICATION_XML));
            //noinspection unchecked
            restTemplate.setMessageConverters(Collections.<HttpMessageConverter<?>>singletonList(converter));
        }

        return restTemplate;
    }

    private static class PreemptiveAuthHttpRequestFactor extends HttpComponentsClientHttpRequestFactory {
        private String username;
        private String password;

        public PreemptiveAuthHttpRequestFactor(String username, String password, HttpClient httpClient) {
            super(httpClient);
            this.username = username;
            this.password = password == null ? "" : password;
        }

        @Override
        protected HttpContext createHttpContext(HttpMethod httpMethod, URI uri) {
            AuthCache authCache = new BasicAuthCache();
            BasicScheme basicAuth = new BasicScheme();
            HttpHost targetHost = new HttpHost(uri.getHost(), uri.getPort());
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                    new AuthScope(targetHost.getHostName(), targetHost.getPort()),
                    new UsernamePasswordCredentials(username, password));
            authCache.put(targetHost, basicAuth);
            HttpClientContext context = HttpClientContext.create();
            context.setCredentialsProvider(credentialsProvider);
            context.setAuthCache(authCache);

            return context;
        }
    }
}
