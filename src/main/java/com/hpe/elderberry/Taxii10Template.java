/*
 * (c) Copyright 2015 Hewlett Packard Enterprise Development LP Licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the
 * License.
 */
package com.hpe.elderberry;

import org.apache.commons.logging.Log;
import org.mitre.taxii.messages.xml10.DiscoveryRequest;
import org.mitre.taxii.messages.xml10.DiscoveryResponse;
import org.mitre.taxii.messages.xml10.FeedInformationRequest;
import org.mitre.taxii.messages.xml10.FeedInformationResponse;
import org.mitre.taxii.messages.xml10.FeedRecordType;
import org.mitre.taxii.messages.xml10.PollRequest;
import org.mitre.taxii.messages.xml10.PollResponse;
import org.mitre.taxii.messages.xml10.ServiceInstanceType;
import org.mitre.taxii.messages.xml10.ServiceTypeEnum;
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
import java.util.List;

import static java.lang.System.currentTimeMillis;
import static java.util.Collections.singletonList;
import static javax.xml.datatype.DatatypeFactory.newInstance;
import static org.apache.commons.logging.LogFactory.getLog;
import static org.mitre.taxii.Versions.VID_TAXII_HTTPS_10;
import static org.mitre.taxii.Versions.VID_TAXII_HTTP_10;
import static org.mitre.taxii.Versions.VID_TAXII_SERVICES_10;
import static org.mitre.taxii.Versions.VID_TAXII_XML_10;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.APPLICATION_XML;

/**
 * <p>Taxii11Template is a convenient way to connect spring to a TAXII 1.0 server. This template allows you to easily
 * connect with a <a href="http://taxii.mitre.org/specifications/version1.0/">TAXII 1.0</a> server. This template uses
 * the <a href="https://github.com/TAXIIProject/java-taxii">TAXII-java</a> project for its JAXB implementation of the
 * XML messages.</p>
 * <br>
 * example:<br>
 * <pre>
 * {@code
 *
 *     <bean name="taxiiConnection" class="TaxiiConnection"
 *          p:discoveryUrl="http://hailataxii.com/taxii-discovery-service"
 *          p:useProxy="true"
 *     />
 *
 *     <bean name="taxiiTemplate" class="Taxii10Template"
 *          p:taxiiConnection-ref="taxiiConnection"
 *     />
 * }
 *    </pre>
 */
@SuppressWarnings("unused")
public class Taxii10Template {
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
     * runs a TAXII 1.0 discovery
     *
     * @return the <code>DiscoveryResponse</code>, or null if there was an error connecting to the discovery service
     */
    public DiscoveryResponse discover() {
        ResponseEntity<DiscoveryResponse> response = conn.getRestTemplate().postForEntity(conn.getDiscoveryUrl(),
                wrapRequest(new DiscoveryRequest().withMessageId(generateMessageId())), DiscoveryResponse.class);

        return respond(response);
    }

    /**
     * runs a TAXII 1.0 collection management request (a.k.a feed information)
     *
     * @param service the Collection Management (information) <code>ServiceInstanceType</code> as returned from {@link #discover()}
     * @return a <code>FeedInformationResponse</code> or null when there was an error retrieving the information
     * @throws MalformedURLException when the service URL is incorrect
     * @throws URISyntaxException    when the service URL cannot be converted into a URI
     */
    public FeedInformationResponse feedInformation(ServiceInstanceType service) throws MalformedURLException, URISyntaxException {
        return feedInformation(service.getAddress());
    }

    /**
     * runs a TAXII 1.0 collection management request (a.k.a feed information)
     *
     * @param url the collection management service URL
     * @return a <code>FeedInformationResponse</code> or null when there was an error retrieving the information
     * @throws MalformedURLException when the service URL is incorrect
     * @throws URISyntaxException    when the service URL cannot be converted into a URI
     */
    public FeedInformationResponse feedInformation(String url) throws MalformedURLException, URISyntaxException {
        return feedInformation(new URL(url));
    }

    /**
     * runs a TAXII 1.0 collection management request (a.k.a feed information)
     *
     * @param url the collection management service URL
     * @return a <code>FeedInformationResponse</code> or null when there was an error retrieving the information
     * @throws URISyntaxException when the service URL cannot be converted into a URI
     */
    public FeedInformationResponse feedInformation(URL url) throws URISyntaxException {
        ResponseEntity<FeedInformationResponse> response = conn.getRestTemplate().postForEntity(url.toURI(),
                wrapRequest(new FeedInformationRequest().withMessageId(generateMessageId())), FeedInformationResponse.class);

        return respond(response);
    }

    /**
     * polls a TAXII 1.0 poll service
     *
     * @param feed           the feed record to poll
     * @param subscriptionId an optional subscription ID. Some services require it, even if they ignore it (like hail a
     *                       taxii)
     * @param exclusiveBegin begin time to poll
     * @param inclusiveEnd   end time to poll
     * @return a poll response
     * @throws URISyntaxException    when the feed record URL cannot be converted to a URI
     * @throws MalformedURLException when the collection record has an incorrect address
     */
    public PollResponse poll(FeedRecordType feed, String subscriptionId, Date exclusiveBegin, Date inclusiveEnd) throws MalformedURLException, URISyntaxException {
        return poll(new URL(feed.getPollingServices().get(0).getAddress()), feed.getFeedName(), subscriptionId,
                exclusiveBegin, inclusiveEnd);
    }

    /**
     * polls a TAXII 1.0 service
     *
     * @param pollUrl        poll service URL
     * @param feedName       feed name to poll
     * @param subscriptionId an optional subscription ID. Some service require it, even if they ignore it (like hail a taxii)
     * @param exclusiveBegin begin time to poll
     * @param inclusiveEnd   end time to poll
     * @return a poll response
     * @throws URISyntaxException when the feed record URL cannot be converted to a URI
     */
    public PollResponse poll(URL pollUrl, String feedName, String subscriptionId, Date exclusiveBegin, Date inclusiveEnd) throws URISyntaxException {
        try {
            PollRequest pollRequest = new PollRequest()
                    .withMessageId(generateMessageId())
                    .withFeedName(feedName)
                    .withExclusiveBeginTimestamp(toXmlGregorianCalendar(exclusiveBegin))
                    .withInclusiveEndTimestamp(toXmlGregorianCalendar(inclusiveEnd))
                    .withSubscriptionId(subscriptionId);

            ResponseEntity<PollResponse> response = conn.getRestTemplate().postForEntity(pollUrl.toURI(),
                    wrapRequest(pollRequest), PollResponse.class);

            return respond(response);
        } catch (DatatypeConfigurationException e) {
            log.error("error converting dates: " + e.getMessage(), e);
            return null;
        }
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
     * a convenience method to locate a feed by name
     *
     * @param feeds a collection of <code>FeedRecordType</code>, likely to be in <code>feedInfo.getFeeds()</code>
     * @param name  the name of the feed to locate
     * @return the <code>FeedRecordType</code> or null when not found
     */
    public FeedRecordType findFeed(List<FeedRecordType> feeds, String name) {
        for (FeedRecordType feed : feeds) {
            if (feed.getFeedName().equals(name)) {
                return feed;
            }
        }
        return null;
    }

    private <T> T respond(ResponseEntity<T> response) {
        if (response.getStatusCode() == OK) {
            return response.getBody();
        }

        log.error("error in TAXII request: " + response.getStatusCode());

        return null;
    }

    private String generateMessageId() {
        return String.valueOf(currentTimeMillis() / 100000);
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
        headers.add("X-TAXII-Services", VID_TAXII_SERVICES_10);
        headers.add("X-TAXII-Content-Type", VID_TAXII_XML_10);
        String binding = conn.getDiscoveryUrl().getScheme().endsWith("s") ? VID_TAXII_HTTPS_10 : VID_TAXII_HTTP_10;
        headers.add("X-TAXII-Protocol", binding);
        return new HttpEntity<>(body, headers);
    }
}
