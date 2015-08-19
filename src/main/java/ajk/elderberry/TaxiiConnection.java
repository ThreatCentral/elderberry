package ajk.elderberry;

import org.apache.commons.logging.Log;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.protocol.HttpContext;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.xml.MarshallingHttpMessageConverter;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Collections;

import static java.util.Collections.singletonList;
import static org.apache.commons.logging.LogFactory.getLog;
import static org.apache.http.impl.client.HttpClientBuilder.create;
import static org.springframework.http.MediaType.APPLICATION_XML;

/**
 * The {@code TaxiiConnection} Configuration options are:<br>
 * <ul>
 * <li>discoveryUrl - the only required property. Set this URL to point to the discovery URL of your TAXII server</li>
 * <li>username - optional username for servers that require basic authentication</li>
 * <li>password - optional password for servers that require basic authentication</li>
 * <li>useProxy - a flag to request the use of an http/s proxy to access the TAXII server</li>
 * <li>proxyHost - optional proxy hostname. If useProxy is true and this property is not specified, then the template
 * will attempt to obtain the hostname from the system property http.proxyHost or https.proxyHost,
 * depending on the discoveryUrl scheme</li>
 * <li>proxyPort - optional proxy port. If useProxy is true and this property is not specified, then the template will
 * attempt to obtain the port from the system property http.proxyPort or https.proxyPort, depending on the discoveryUrl
 * scheme</li>
 * <li>marshaller - an optional <code>Jaxb2Marshaller</code>. If not provided then the template will create its own
 * marshaller. This marshaller is expected to be able to marshal and unmarshal TAXII 1.0 XMLs into and from objects</li>
 * <li>restTemplate - an optional <code>RestTemplate</code>. If not provided then the template will create its own rest
 * template configured with the marshaller</li>
 * </ul>
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
            HttpClientBuilder builder = create();

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
