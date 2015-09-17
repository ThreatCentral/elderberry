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
        compile "ajk.elderberry:elderberry:0.5"
    }
```

# Features

* TAXII 1.1 support
* TAXII 1.0 support
* Basic authentication
* HTTP/s proxy support
* SSL client certificates

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

See the [JavaDocs](http://amirkibbar.github.io/elderberry/index.html) for further details.

# Resources

* [TAXII-java](https://github.com/TAXIIProject/java-taxii)
* [TAXII 1.1](http://taxii.mitre.org)