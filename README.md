# spring-taxii

[![Project Status](http://stillmaintained.com/amirkibbar/elderberry.png)](http://stillmaintained.com/amirkibbar/elderberry)

[![Build Status](https://travis-ci.org/amirkibbar/elderberry.svg?branch=master)](https://travis-ci.org/amirkibbar/elderberry)

[ ![Download](https://api.bintray.com/packages/amirk/maven/spring-taxii/images/download.svg) ](https://bintray.com/amirk/maven/spring-taxii/_latestVersion)

# Overview

The spring-taxii project provides a convenient way to connect to a TAXII server from within an Spring application. Both
TAXII 1.0 and 1.1 protocols are supported.

Taxii11Template is a convenient way to connect a spring application to a TAXII 1.1 server. This template allows you to 
easily connect with a [TAXII 1.1](http://taxii.mitre.org/specifications/version1.1) server. 

Taxii10Template is a convenient way to connect a spring application to a TAXII 1.0 server. This template allows you to
easily connect with a [TAXII 1.0](http://taxii.mitre.org/specifications/version1.0) server.

The spring-taxii library uses [TAXII-java](https://github.com/TAXIIProject/java-taxii) project for its JAXB 
implementation of the XML messages.

# Setup your gradle project

```gradle
    
    repositories {
        maven { url  "http://dl.bintray.com/amirk/maven" }
    }
    
    dependencies {
        compile "ajk.elderberry:elderberry:0.3"
    }
```

# Connection configuration options

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

    <bean name="taxiiConnection" class="ajk.elderberry.TaxiiConnection"
         p:discoveryUrl="http://hailataxii.com/taxii-discovery-service"
         p:useProxy="true"
    />
 
    <bean name="taxii10Template" class="ajk.elderberry.Taxii10Template"
         p:taxiiConnection-ref="taxiiConnection"
    />

    <bean name="taxii11Template" class="ajk.elderberry.Taxii11Template"
         p:taxiiConnection-ref="taxiiConnection"
    />
```

# Resources

* [JavaDocs](http://amirkibbar.github.io/elderberry/index.html)
* [TAXII-java](https://github.com/TAXIIProject/java-taxii)
* [TAXII 1.1](http://taxii.mitre.org)