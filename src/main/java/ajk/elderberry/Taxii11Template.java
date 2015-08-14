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
import org.mitre.taxii.messages.xml11.PollRequest;
import org.mitre.taxii.messages.xml11.PollResponse;
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

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.XMLGregorianCalendar;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;

import static java.lang.System.currentTimeMillis;
import static java.util.Collections.singletonList;
import static javax.xml.datatype.DatatypeFactory.newInstance;
import static org.apache.commons.logging.LogFactory.getLog;
import static org.apache.http.impl.client.HttpClientBuilder.create;
import static org.mitre.taxii.Versions.VID_TAXII_HTTPS_10;
import static org.mitre.taxii.Versions.VID_TAXII_HTTP_10;
import static org.mitre.taxii.Versions.VID_TAXII_XML_11;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.APPLICATION_XML;

/**
 * <p>Taxii11Template is a convenient way to connect spring to a TAXII server. This template allows you to easily connect
 * with a <a href="http://taxii.mitre.org/">TAXII 1.1</a> server. This template uses the
 * <a href="https://github.com/TAXIIProject/java-taxii">TAXII-java</a> project for its JAXB implementation of the XML
 * messages.</p>
 * Configuration options:<br>
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
 * marshaller. This marshaller is expected to be able to marshal and unmarshal TAXII 1.1 XMLs into and from objects</li>
 * <li>restTemplate - an optional <code>RestTemplate</code>. If not provided then the template will create its own rest
 * template configured with the marshaller</li>
 * </ul>
 * <br>
 * example:<br>
 * <pre>
 * {@code
 *     <bean name="taxiiTemplate" class="ajk.elderberry.Taxii11Template"
 *          p:discoveryUrl="http://hailataxii.com/taxii-discovery-service"
 *          p:useProxy="true"
 *        />
 * }
 *    </pre>
 */
@SuppressWarnings("unused")
public class Taxii11Template implements InitializingBean {
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
     * sets up the internal RestTemplate and Jaxb2Marshaller, unless these were provided externally
     *
     * @throws Exception required by interface
     */
    @Override
    public void afterPropertiesSet() throws Exception {
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
        ResponseEntity<DiscoveryResponse> response = restTemplate.postForEntity(discoveryUrl,
                wrapRequest(new DiscoveryRequest().withMessageId(generateMessageId())), DiscoveryResponse.class);

        return respond(response);
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

        return respond(response);
    }

    /**
     * polls a TAXII 1.1 service
     *
     * @param collection the collection record to poll
     * @return a poll response
     * @throws URISyntaxException    when the collection record has an incorrect address
     * @throws MalformedURLException when the collection record has an incorrect address
     */
    public PollResponse poll(CollectionRecordType collection) throws URISyntaxException, MalformedURLException {
        return poll(collection, "", yesterday(), new Date());
    }

    /**
     * polls a TAXII 1.1 service
     *
     * @param collection     the collection record to poll
     * @param subscriptionId an optional subscription ID. Some service require it, even if they ignore it (like hail a taxii)
     * @param exclusiveBegin begin time to poll
     * @param inclusiveEnd   end time to poll
     * @return a poll response
     * @throws URISyntaxException    when the collection record has an incorrect address
     * @throws MalformedURLException when the collection record has an incorrect address
     */
    public PollResponse poll(CollectionRecordType collection, String subscriptionId, Date exclusiveBegin, Date inclusiveEnd) throws URISyntaxException, MalformedURLException {
        return poll(new URL(collection.getPollingServices().get(0).getAddress()), collection.getCollectionName(),
                subscriptionId, exclusiveBegin, inclusiveEnd);
    }

    /**
     * polls a TAXII 1.1 service
     *
     * @param pollUrl        poll service URL
     * @param collectionName collection name to poll
     * @param subscriptionId an optional subscription ID. Some service require it, even if they ignore it (like hail a taxii)
     * @param exclusiveBegin begin time to poll
     * @param inclusiveEnd   end time to poll
     * @return a poll response
     * @throws URISyntaxException when the collection record has an incorrect address
     */
    public PollResponse poll(URL pollUrl, String collectionName, String subscriptionId, Date exclusiveBegin, Date inclusiveEnd) throws URISyntaxException {
        try {
            PollRequest pollRequest = new PollRequest()
                    .withMessageId(generateMessageId())
                    .withCollectionName(collectionName)
                    .withExclusiveBeginTimestamp(toXmlGregorianCalendar(exclusiveBegin))
                    .withInclusiveEndTimestamp(toXmlGregorianCalendar(inclusiveEnd))
                    .withSubscriptionID(subscriptionId);

            ResponseEntity<PollResponse> response = restTemplate.postForEntity(pollUrl.toURI(),
                    wrapRequest(pollRequest), PollResponse.class);

            return respond(response);
        } catch (DatatypeConfigurationException e) {
            log.error("error converting dates: " + e.getMessage(), e);
            return null;
        }
    }

    private <T> T respond(ResponseEntity<T> response) {
        if (response.getStatusCode() == OK) {
            return response.getBody();
        }

        log.error("error in TAXII request: " + response.getStatusCode());

        return null;
    }

    private Date yesterday() {
        return new Date(currentTimeMillis() - 86400000);
    }

    private XMLGregorianCalendar toXmlGregorianCalendar(Date date) throws DatatypeConfigurationException {
        GregorianCalendar c = new GregorianCalendar();
        c.setTime(date);
        return newInstance().newXMLGregorianCalendar(c);
    }

    /**
     * the only required property. Set this URL to point to the discovery URL of your TAXII server.
     *
     * @param discoveryUrl For example: https://threatcentral.io/tc/taxii/discovery
     * @throws URISyntaxException when the discoverUrl cannot be converted into a URI
     */
    @Required
    public void setDiscoveryUrl(URL discoveryUrl) throws URISyntaxException {
        this.discoveryUrl = discoveryUrl.toURI();
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
     *                 {@link #setDiscoveryUrl(URL)}. This is considered a request because it depends on the
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

    private <T> HttpEntity<T> wrapRequest(T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(APPLICATION_XML);
        headers.setAccept(singletonList(APPLICATION_XML));
        headers.add("X-TAXII-Content-Type", VID_TAXII_XML_11);
        String binding = discoveryUrl.getScheme().endsWith("s") ? VID_TAXII_HTTPS_10 : VID_TAXII_HTTP_10;
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
