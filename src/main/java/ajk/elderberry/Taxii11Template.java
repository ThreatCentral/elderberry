package ajk.elderberry;

import org.apache.commons.logging.Log;
import org.mitre.taxii.messages.xml11.CollectionInformationRequest;
import org.mitre.taxii.messages.xml11.CollectionInformationResponse;
import org.mitre.taxii.messages.xml11.CollectionRecordType;
import org.mitre.taxii.messages.xml11.DiscoveryRequest;
import org.mitre.taxii.messages.xml11.DiscoveryResponse;
import org.mitre.taxii.messages.xml11.PollRequest;
import org.mitre.taxii.messages.xml11.PollResponse;
import org.mitre.taxii.messages.xml11.ServiceInstanceType;
import org.mitre.taxii.messages.xml11.ServiceTypeEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.XMLGregorianCalendar;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;

import static java.lang.System.currentTimeMillis;
import static java.util.Collections.singletonList;
import static javax.xml.datatype.DatatypeFactory.newInstance;
import static org.apache.commons.logging.LogFactory.getLog;
import static org.mitre.taxii.Versions.VID_TAXII_HTTPS_10;
import static org.mitre.taxii.Versions.VID_TAXII_HTTP_10;
import static org.mitre.taxii.Versions.VID_TAXII_XML_11;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.APPLICATION_XML;

/**
 * <p>Taxii11Template is a convenient way to connect spring to a TAXII 1.1 server. This template allows you to easily
 * connect with a <a href="http://taxii.mitre.org/specifications/version1.1/">TAXII 1.1</a> server. This template uses
 * the <a href="https://github.com/TAXIIProject/java-taxii">TAXII-java</a> project for its JAXB implementation of the
 * XML messages.</p>
 * <br>
 * example:<br>
 * <pre>
 * {@code
 *
 *     <bean name="taxiiConnection" class="ajk.elderberry.TaxiiConnection"
 *          p:discoveryUrl="http://hailataxii.com/taxii-discovery-service"
 *          p:useProxy="true"
 *     />
 *
 *     <bean name="taxiiTemplate" class="ajk.elderberry.Taxii11Template"
 *          p:taxiiConnection-ref="taxiiConnection"
 *     />
 * }
 *    </pre>
 */
@SuppressWarnings("unused")
public class Taxii11Template {
    private Log log = getLog(getClass());

    private TaxiiConnection conn;

    /**
     * the {@link TaxiiConnection} to use
     *
     * @param conn a valid, non-null {@link TaxiiConnection}
     */
    @Autowired
    @Required
    public void setTaxiiConnection(TaxiiConnection conn) {
        this.conn = conn;
    }

    /**
     * runs a TAXII 1.1 discovery
     *
     * @return the <code>DiscoveryResponse</code>, or null if there was an error connecting to the discovery service
     */
    public DiscoveryResponse discover() {
        ResponseEntity<DiscoveryResponse> response = conn.getRestTemplate().postForEntity(conn.getDiscoveryUrl(),
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
     * @throws URISyntaxException when the service URL cannot be converted into a URI
     */
    public CollectionInformationResponse collectionInformation(URL url) throws URISyntaxException {
        ResponseEntity<CollectionInformationResponse> response = conn.getRestTemplate().postForEntity(url.toURI(),
                wrapRequest(new CollectionInformationRequest().withMessageId(generateMessageId())), CollectionInformationResponse.class);

        return respond(response);
    }

    /**
     * polls a TAXII 1.1 service
     *
     * @param collection the collection record to poll
     * @return a poll response
     * @throws URISyntaxException when the service URL cannot be converted into a URI
     * @throws MalformedURLException when the collection record has an incorrect address
     */
    public PollResponse poll(CollectionRecordType collection) throws URISyntaxException, MalformedURLException {
        return poll(collection, "", yesterday(), new Date());
    }

    /**
     * polls a TAXII 1.1 poll service
     *
     * @param collection     the collection record to poll
     * @param subscriptionId an optional subscription ID. Some services require it, even if they ignore it (like hail a
     *                       taxii)
     * @param exclusiveBegin begin time to poll
     * @param inclusiveEnd   end time to poll
     * @return a poll response
     * @throws URISyntaxException when the collection record URL cannot be converted to a URI
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
     * @throws URISyntaxException when the collection record URL cannot be converted to a URI
     */
    public PollResponse poll(URL pollUrl, String collectionName, String subscriptionId, Date exclusiveBegin, Date inclusiveEnd) throws URISyntaxException {
        try {
            PollRequest pollRequest = new PollRequest()
                    .withMessageId(generateMessageId())
                    .withCollectionName(collectionName)
                    .withExclusiveBeginTimestamp(toXmlGregorianCalendar(exclusiveBegin))
                    .withInclusiveEndTimestamp(toXmlGregorianCalendar(inclusiveEnd))
                    .withSubscriptionID(subscriptionId);

            ResponseEntity<PollResponse> response = conn.getRestTemplate().postForEntity(pollUrl.toURI(),
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

    private <T> HttpEntity<T> wrapRequest(T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(APPLICATION_XML);
        headers.setAccept(singletonList(APPLICATION_XML));
        headers.add("X-TAXII-Content-Type", VID_TAXII_XML_11);
        String binding = conn.getDiscoveryUrl().getScheme().endsWith("s") ? VID_TAXII_HTTPS_10 : VID_TAXII_HTTP_10;
        headers.add("X-TAXII-Protocol", binding);
        return new HttpEntity<>(body, headers);
    }

    private String generateMessageId() {
        return String.valueOf(currentTimeMillis() / 100000);
    }
}
