/*
 * (c) Copyright 2015 Hewlett Packard Enterprise Development LP Licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the
 * License.
 */
package com.hpe.elderberry;

import org.junit.Test;
import org.mitre.taxii.messages.xml11.CollectionInformationResponse;
import org.mitre.taxii.messages.xml11.CollectionRecordType;
import org.mitre.taxii.messages.xml11.DiscoveryResponse;
import org.mitre.taxii.messages.xml11.PollResponse;
import org.mitre.taxii.messages.xml11.ServiceInstanceType;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Date;

import static java.lang.System.currentTimeMillis;
import static org.fest.assertions.Assertions.assertThat;
import static org.mitre.taxii.messages.xml11.ServiceTypeEnum.COLLECTION_MANAGEMENT;
import static org.mitre.taxii.messages.xml11.ServiceTypeEnum.DISCOVERY;
import static org.mitre.taxii.messages.xml11.ServiceTypeEnum.POLL;

public class Taxii11TemplateTest {
    private DiscoveryResponse discover(Taxii11Template taxiiTemplate) throws Exception {
        DiscoveryResponse response = taxiiTemplate.discover();
        assertThat(response.getServiceInstances())
                .hasSize(3)
                .onProperty("serviceType").contains(DISCOVERY, COLLECTION_MANAGEMENT, POLL);

        return response;
    }

    private CollectionInformationResponse collectionInformation(Taxii11Template taxiiTemplate, DiscoveryResponse discovery) throws MalformedURLException, URISyntaxException {
        ServiceInstanceType collectionManagement = taxiiTemplate.findService(discovery.getServiceInstances(), COLLECTION_MANAGEMENT);
        assertThat(collectionManagement).isNotNull();

        CollectionInformationResponse collectionInfo = taxiiTemplate.collectionInformation(collectionManagement);
        assertThat(collectionInfo).isNotNull();

        assertThat(collectionInfo.getCollections())
                .onProperty("collectionName").contains("system.Default");

        return collectionInfo;
    }

    @Test
    public void discoverAndPoll() throws Exception {
        TaxiiConnection taxiiConnection = new TaxiiConnection();
        taxiiConnection.setDiscoveryUri(new URL("http://hailataxii.com/taxii-discovery-service").toURI());
        taxiiConnection.setUseProxy(true);

        Taxii11Template taxiiTemplate = new Taxii11Template();
        taxiiTemplate.setTaxiiConnection(taxiiConnection);

        DiscoveryResponse discovery = discover(taxiiTemplate);

        CollectionInformationResponse cm = collectionInformation(taxiiTemplate, discovery);

        CollectionRecordType systemDefault = taxiiTemplate.findCollection(cm.getCollections(), "system.Default");
        assertThat(systemDefault).isNotNull();

        PollResponse poll = taxiiTemplate.poll(systemDefault, "some id", new Date(currentTimeMillis() - 86400000), new Date());
        assertThat(poll.getContentBlocks()).isNotEmpty();
    }
}
