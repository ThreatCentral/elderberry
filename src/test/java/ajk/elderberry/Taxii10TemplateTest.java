package ajk.elderberry;

import org.junit.Test;
import org.mitre.taxii.messages.xml10.DiscoveryResponse;
import org.mitre.taxii.messages.xml10.FeedInformationResponse;
import org.mitre.taxii.messages.xml10.FeedRecordType;
import org.mitre.taxii.messages.xml10.PollResponse;
import org.mitre.taxii.messages.xml10.ServiceInstanceType;

import java.net.URL;
import java.util.Date;

import static java.lang.System.currentTimeMillis;
import static org.fest.assertions.Assertions.assertThat;
import static org.mitre.taxii.messages.xml10.ServiceTypeEnum.DISCOVERY;
import static org.mitre.taxii.messages.xml10.ServiceTypeEnum.FEED_MANAGEMENT;
import static org.mitre.taxii.messages.xml10.ServiceTypeEnum.POLL;

public class Taxii10TemplateTest {
    private DiscoveryResponse discover(Taxii10Template taxiiTemplate) {
        DiscoveryResponse response = taxiiTemplate.discover();
        assertThat(response.getServiceInstances())
                .hasSize(3)
                .onProperty("serviceType").contains(DISCOVERY, FEED_MANAGEMENT, POLL);

        return response;
    }

    private FeedInformationResponse feedInformation(Taxii10Template taxiiTemplate, DiscoveryResponse discovery) throws Exception {
        ServiceInstanceType feedManagement = taxiiTemplate.findService(discovery.getServiceInstances(), FEED_MANAGEMENT);
        assertThat(feedManagement).isNotNull();

        FeedInformationResponse feedInfo = taxiiTemplate.feedInformation(feedManagement);
        assertThat(feedInfo).isNotNull();
        assertThat(feedInfo.getFeeds())
                .onProperty("feedName").contains("system.Default");

        return feedInfo;
    }

    @Test
    public void discoverAndPoll() throws Exception {
        TaxiiConnection taxiiConnection = new TaxiiConnection();
        taxiiConnection.setDiscoveryUri(new URL("http://hailataxii.com/taxii-discovery-service").toURI());
        taxiiConnection.setUseProxy(true);

        Taxii10Template taxiiTemplate = new Taxii10Template();
        taxiiTemplate.setTaxiiConnection(taxiiConnection);

        DiscoveryResponse discovery = discover(taxiiTemplate);

        FeedInformationResponse feedInfo = feedInformation(taxiiTemplate, discovery);

        FeedRecordType systemDefault = taxiiTemplate.findFeed(feedInfo.getFeeds(), "system.Default");
        assertThat(systemDefault).isNotNull();

        PollResponse poll = taxiiTemplate.poll(systemDefault, "some id", new Date(currentTimeMillis() - 86400000), new Date());
        assertThat(poll.getContentBlocks()).isNotEmpty();
    }
}
