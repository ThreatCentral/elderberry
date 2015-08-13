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
import org.mitre.taxii.messages.xml11.CollectionInformationRequest;
import org.mitre.taxii.messages.xml11.CollectionInformationResponse;
import org.mitre.taxii.messages.xml11.CollectionRecordType;
import org.mitre.taxii.messages.xml11.DiscoveryRequest;
import org.mitre.taxii.messages.xml11.DiscoveryResponse;
import org.mitre.taxii.messages.xml11.ServiceInstanceType;
import org.mitre.taxii.messages.xml11.ServiceTypeEnum;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.xml.MarshallingHttpMessageConverter;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.web.client.RestTemplate;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;

import static java.lang.System.currentTimeMillis;
import static java.util.Collections.singletonList;
import static org.apache.commons.logging.LogFactory.getLog;
import static org.apache.http.impl.client.HttpClientBuilder.create;
import static org.mitre.taxii.Versions.VID_TAXII_HTTPS_10;
import static org.mitre.taxii.Versions.VID_TAXII_HTTP_10;
import static org.mitre.taxii.Versions.VID_TAXII_XML_11;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.APPLICATION_XML;

public class Taxii11Template implements InitializingBean {
    private Log log = getLog(getClass());
    private URL discoveryUrl;
    private String username = "";
    private String password = "";
    private boolean useProxy = false;
    private String proxyHost = "";
    private int proxyPort;
    private Jaxb2Marshaller marshaller;
    private RestTemplate restTemplate;

    /**
     * sets up the internal RestTemplate and Jaxb2Marshaller, unless these were provided externally
     *
     * @throws Exception
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        if (restTemplate == null) {
            HttpClientBuilder builder = create();

            if (useProxy) {
                if ("".equals(proxyHost)) {
                    proxyHost = System.getProperty(discoveryUrl.getProtocol() + ".proxyHost");
                }

                if (proxyPort == 0) {
                    proxyPort = Integer.parseInt(System.getProperty(discoveryUrl.getProtocol() + ".proxyPort", "0"));
                }

                if ("".equals(proxyHost) || proxyHost == null || proxyPort == 0) {
                    log.warn("proxy requested, but not setup, not using a proxy");
                } else {
                    log.info("using " + discoveryUrl.getProtocol() + " proxy: " + proxyHost + ":" + proxyPort);
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
                marshaller.afterPropertiesSet();
            }

            MarshallingHttpMessageConverter converter = new MarshallingHttpMessageConverter(marshaller);
            converter.setSupportedMediaTypes(singletonList(APPLICATION_XML));
            //noinspection unchecked
            restTemplate.setMessageConverters(Collections.<HttpMessageConverter<?>>singletonList(converter));
        }
    }

    /**
     * runs a TAXII 1.1 discovery
     *
     * @return the <code>DiscoveryResponse</code>, or null if there was an error connecting to the discovery service
     * @throws URISyntaxException when the {@link #discoveryUrl} is incorrect
     */
    public DiscoveryResponse discover() throws URISyntaxException {
        ResponseEntity<DiscoveryResponse> response = restTemplate.postForEntity(discoveryUrl.toURI(),
                wrapRequest(new DiscoveryRequest().withMessageId(generateMessageId())), DiscoveryResponse.class);

        if (response.getStatusCode() == OK) {
            return response.getBody();
        }

        log.error("error during discovery: " + response.getStatusCode());

        return null;
    }

    /**
     * a convenience method to locate a service by type
     *
     * @param services a collection of <code>ServiceInstanceType</code>, likely to be in <code>discovery.getServiceInstances()</code>
     * @param type     the service type to locate in the collection
     * @return the <code>ServiceInstanceType</code> or null when not found
     */
    public ServiceInstanceType findService(Collection<ServiceInstanceType> services, ServiceTypeEnum type) {
        for (ServiceInstanceType service : services) {
            if (service.getServiceType().equals(type)) {
                return service;
            }
        }

        return null;
    }

    /**
     * a convenience method to locate a collection by name
     *
     * @param collections a collection of <code>CollectionRecordType</code>, likely to be in <code>cm.getCollections()</code>
     * @param name        the name of the collection to locate
     * @return the <code>CollectionRecordType</code> or null when not found
     */
    public CollectionRecordType findCollection(Collection<CollectionRecordType> collections, String name) {
        for (CollectionRecordType collection : collections) {
            if (collection.getCollectionName().equals(name)) {
                return collection;
            }
        }

        return null;
    }

    /**
     * runs a TAXII 1.1 collection management request (a.k.a collection information)
     *
     * @param service the Collection Management (information) <code>ServiceInstanceType</code> as returned from {@link #discover()}
     * @return a <code>CollectionInformationResponse</code> or null when there was an error retrieving the information
     * @throws MalformedURLException when the service URL is incorrect
     * @throws URISyntaxException    when the service URL is incorrect
     */
    public CollectionInformationResponse collectionInformation(ServiceInstanceType service) throws MalformedURLException, URISyntaxException {
        return collectionInformation(service.getAddress());
    }

    /**
     * runs a TAXII 1.1 collection management request (a.k.a collection information)
     *
     * @param url the collection management service URL
     * @return a <code>CollectionInformationResponse</code> or null when there was an error retrieving the information
     * @throws MalformedURLException when the service URL is incorrect
     * @throws URISyntaxException    when the service URL is incorrect
     */
    public CollectionInformationResponse collectionInformation(String url) throws MalformedURLException, URISyntaxException {
        return collectionInformation(new URL(url));
    }

    /**
     * runs a TAXII 1.1 collection management request (a.k.a collection information)
     *
     * @param url the collection management service URL
     * @return a <code>CollectionInformationResponse</code> or null when there was an error retrieving the information
     * @throws URISyntaxException when the service URL is incorrect
     */
    public CollectionInformationResponse collectionInformation(URL url) throws URISyntaxException {
        ResponseEntity<CollectionInformationResponse> response = restTemplate.postForEntity(url.toURI(),
                wrapRequest(new CollectionInformationRequest().withMessageId(generateMessageId())), CollectionInformationResponse.class);

        if (response.getStatusCode() == OK) {
            return response.getBody();
        }

        log.error("error in collection information: " + response.getStatusCode());

        return null;
    }

    @Required
    public void setDiscoveryUrl(URL discoveryUrl) {
        this.discoveryUrl = discoveryUrl;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setUseProxy(boolean useProxy) {
        this.useProxy = useProxy;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public void setMarshaller(Jaxb2Marshaller marshaller) {
        this.marshaller = marshaller;
    }

    public void setRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    private <T> HttpEntity<T> wrapRequest(T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(APPLICATION_XML);
        headers.setAccept(singletonList(APPLICATION_XML));
        headers.add("X-TAXII-Content-Type", VID_TAXII_XML_11);
        String binding = discoveryUrl.getProtocol().endsWith("s") ? VID_TAXII_HTTPS_10 : VID_TAXII_HTTP_10;
        headers.add("X-TAXII-Protocol", binding);
        return new HttpEntity<>(body, headers);
    }

    private String generateMessageId() {
        return String.valueOf(currentTimeMillis() / 100000);
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
