# spring-taxii

[![Project Status](http://stillmaintained.com/amirkibbar/elderberry.png)](http://stillmaintained.com/amirkibbar/elderberry)

[![Build Status](https://travis-ci.org/amirkibbar/elderberry.svg?branch=master)](https://travis-ci.org/amirkibbar/elderberry)

# Overview

The spring-taxii project provides a convenient way to connect to a TAXII server from within an Spring application. The
key (and at the moment only) class it provides is the Taxii11Template.

Taxii11Template is a convenient way to connect spring to a TAXII server. This template allows you to easily connect
with a [TAXII 1.1](http://taxii.mitre.org) server. This template uses the [TAXII-java](https://github.com/TAXIIProject/java-taxii)
project for its JAXB implementation of the XML messages.

# Configuration options

* discoveryUrl - the only required property. Set this URL to point to the discovery URL of your TAXII server
* username - optional username for servers that require basic authentication
* password - optional password for servers that require basic authentication
* useProxy - a flag to request the use of an http/s proxy to access the TAXII server
* proxyHost - optional proxy hostname. If useProxy is true and this property is not specified, then the template will 
    attempt to obtain the hostname from the system property http.proxyHost or https.proxyHost, depending on the 
    discoveryUrl scheme
* proxyPort - optional proxy port. If useProxy is true and this property is not specified, then the template will 
    attempt to obtain the port from the system property http.proxyPort or https.proxyPort, depending on the discoveryUrl
    scheme
* marshaller - an optional `Jaxb2Marshaller`. If not provided then the template will create its own marshaller. This 
    marshaller is expected to be able to marshal and unmarshal TAXII 1.1 XMLs into and from objects 
* restTemplate - an optional `RestTemplate`. If not provided then the template will create its own rest template 
    configured with the marshaller

# Example

```xml

    <bean name="taxiiTemplate" class="ajk.elderberry.Taxii11Template"
         p:discoveryUrl="http://hailataxii.com/taxii-discovery-service"
         p:useProxy="true"
         />
```

# Resources

* [JavaDocs](http://amirkibbar.github.io/elderberry/index.html)
* [TAXII-java](https://github.com/TAXIIProject/java-taxii)
* [TAXII 1.1](http://taxii.mitre.org)