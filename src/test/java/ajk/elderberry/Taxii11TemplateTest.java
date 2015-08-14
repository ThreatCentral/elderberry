package ajk.elderberry;

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

        assertThat(collectionInfo.getCollections())
                .onProperty("collectionName").contains("system.Default");

        return collectionInfo;
    }

    @Test
    public void discoverAndPoll() throws Exception {
        Taxii11Template taxiiTemplate = new Taxii11Template();

        taxiiTemplate.setDiscoveryUrl(new URL("http://hailataxii.com/taxii-discovery-service"));
        taxiiTemplate.setUseProxy(true);

        taxiiTemplate.afterPropertiesSet();

        DiscoveryResponse discovery = discover(taxiiTemplate);

        CollectionInformationResponse cm = collectionInformation(taxiiTemplate, discovery);

        CollectionRecordType systemDefault = taxiiTemplate.findCollection(cm.getCollections(), "system.Default");
        assertThat(systemDefault).isNotNull();

        PollResponse poll = taxiiTemplate.poll(systemDefault, "some id", new Date(currentTimeMillis() - 86400000), new Date());
        assertThat(poll.getContentBlocks()).isNotEmpty();
    }
}
