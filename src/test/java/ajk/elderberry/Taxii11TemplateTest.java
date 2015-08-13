package ajk.elderberry;

import org.junit.Test;
import org.mitre.taxii.messages.xml11.DiscoveryResponse;

import java.net.URL;

import static org.fest.assertions.Assertions.assertThat;
import static org.mitre.taxii.messages.xml11.ServiceTypeEnum.COLLECTION_MANAGEMENT;
import static org.mitre.taxii.messages.xml11.ServiceTypeEnum.DISCOVERY;
import static org.mitre.taxii.messages.xml11.ServiceTypeEnum.POLL;

public class Taxii11TemplateTest {
    @Test
    public void discover() throws Exception {
        Taxii11Template taxiTemplate = new Taxii11Template();

        taxiTemplate.setDiscoveryUrl(new URL("http://hailataxii.com/taxii-discovery-service"));
        taxiTemplate.setUseProxy(true);

        taxiTemplate.afterPropertiesSet();

        DiscoveryResponse response = taxiTemplate.discover();
        assertThat(response.getServiceInstances())
                .hasSize(3)
                .onProperty("serviceType").contains(DISCOVERY, COLLECTION_MANAGEMENT, POLL);
    }
}
