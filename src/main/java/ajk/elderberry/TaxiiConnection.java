package ajk.elderberry;

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

import static java.security.KeyStore.getInstance;
import static java.util.Collections.singletonList;
import static java.util.UUID.randomUUID;
import static java.util.stream.Stream.of;
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
 * <br/>
 * The following is an example of a private key in PEM PKCS#8 format:<br/>
 * <pre>
 * {@code -----BEGIN PRIVATE KEY-----
 *   c29tZSB0ZXh0IHRvIG1pbWljIGEgYmFzZSA2NCBlbmNvZGVkIGtleQo
 *   ...encoded text....
 *   YW5kIGV2ZW4gbW9yZSB0ZXh0IHRvIHNpbXVsYXRlIHRoZSBzYW1lIHR
 *   oaW5nCg==
 *   -----END PRIVATE KEY-----}
 * </pre>
 * <p>
 * And the following is an example of a certificate in PEM format:<br/>
 * <pre>
 * {@code -----BEGIN CERTIFICATE-----
 *   c29tZSB0ZXh0IHRvIG1pbWljIGEgYmFzZSA2NCBlbmNvZGVkIGtleQo
 *   ...encoded text....
 *   YW5kIGV2ZW4gbW9yZSB0ZXh0IHRvIHNpbXVsYXRlIHRoZSBzYW1lIHR
 *   oaW5nCg==
 *   -----END CERTIFICATE-----}
 * </pre>
 * <p>
 * <br>
 * The TaxiiConnection can be used with both {@link Taxii11Template} and {@link Taxii10Template}.
 */
@SuppressWarnings("unused")
public class TaxiiConnection {
    private Log log = getLog(getClass());

    private URI discoveryUrl;
    private String username = "";
    private String password = "";
    private boolean useProxy = false;
    private String proxyHost = "";
    private int proxyPort;
    private Jaxb2Marshaller marshaller;
    private RestTemplate restTemplate;
    private KeyStore keyStore;
    private KeyStore trustStore;
    private char[] keyPassword;

    /**
     * optional key store that contains your private key to be sent when the server is using SSL certificate-based
     * authentication. It's expected that the key store would hold a single private key and its supporting certificates
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
     * optional trust material in PEM format. Use this method instead of the {@link #setTrustStore(KeyStore)} when you
     * have a set of trusted certificates in PEM format instead of a Java trust store (key store)
     *
     * @param pems the trusted certificates in PEM format
     */
    public void setTrustedCertificates(String... pems) {
        if (pems == null || pems.length == 0) {
            return;
        }

        try {
            // initialize an empty trust store
            trustStore = getInstance("JKS");
            trustStore.load(null);

            // add all PEMs as trusted certificates to the in-memory trust store
            addPemsToStore(trustStore, pems);
        } catch (Exception e) {
            throw new RuntimeException("unable to create trust store, " + e.getMessage(), e);
        }
    }

    /**
     * optional private SSL key in PEM format. Use this method instead of {@link #setKeyStore(KeyStore, String)} when
     * you have a private key in PEM format instead of a Java key store with the private key material in it
     *
     * @param keyPem    the private key in PEM format
     * @param chainPems the optional client certificate chain in PEM format
     */
    public void setPrivateKey(String keyPem, String... chainPems) {
        if (isEmpty(keyPem)) {
            return;
        }

        try {
            // initialize an empty key store
            keyStore = getInstance("JKS");
            keyStore.load(null);

            // generate a random password for the private key
            keyPassword = randomUUID().toString().toCharArray();
            keyPassword = "pass".toCharArray();

            // load the private key
            byte[] key = parseBase64Binary(keyPem.replaceAll("-+.*-+", ""));
            PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(key));
            if (chainPems != null) {
                List<Certificate> chain = addPemsToStore(keyStore, chainPems);
                keyStore.setKeyEntry(randomUUID().toString(), privateKey, keyPassword, chain.toArray(new Certificate[chain.size()]));
            } else {
                keyStore.setKeyEntry(randomUUID().toString(), privateKey, keyPassword, new Certificate[]{});
            }
        } catch (Exception e) {
            throw new RuntimeException("unable to create key store, " + e.getMessage(), e);
        }
    }

    private List<Certificate> addPemsToStore(KeyStore store, String[] pems) throws CertificateException {
        List<Certificate> result = new ArrayList<>(pems.length);

        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        of(pems).forEach(pem -> {
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
     * a convenient way to externally cache the key store would be to create it from PEMs using
     * {@link #setPrivateKey(String, String...)}, then obtaining it with this method and storing it for future use
     *
     * @return the key store
     */
    public KeyStore getKeyStore() {
        return keyStore;
    }

    /**
     * a convenient way to externally cache the trust store would be to create it from PEMs using
     * {@link #setTrustedCertificates(String...)}, then obtaining it with this method and storing it for future use
     *
     * @return the trust store
     */
    public KeyStore getTrustStore() {
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
     * @param username if left blank preemptive basic authentication is not setup
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
     * @param useProxy when true the connection will attempt to setup an http/s proxy, depending on the
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
     * @param proxyPort if useProxy is true and this property is not specified, then the template will
     *                  attempt to obtain the port from the system property http.proxyPort or https.proxyPort, depending
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

            if (trustStore != null || keyStore != null) {
                SSLContext sslContext;
                try {
                    sslContext = SSLContexts.custom()
                            .loadTrustMaterial(trustStore, new TrustSelfSignedStrategy())
                            .loadKeyMaterial(keyStore, keyPassword)
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
