# Spring-TAXII

[![Project Status](http://stillmaintained.com/amirkibbar/elderberry.png)](http://stillmaintained.com/amirkibbar/elderberry)

[![Build Status](https://travis-ci.org/amirkibbar/elderberry.svg?branch=master)](https://travis-ci.org/amirkibbar/elderberry)

[ ![Download](https://api.bintray.com/packages/threatcentral/maven/spring-taxii/images/download.svg) ](https://bintray.com/threatcentral/maven/spring-taxii/_latestVersion)

# Overview

The Spring-TAXII project provides a convenient way to connect to a TAXII server from within a Spring application. Both
TAXII 1.0 and 1.1 protocols are supported.

Taxii11Template is a convenient way to connect a Spring application to a TAXII 1.1 server. This template allows you to 
easily connect with a [TAXII 1.1](http://taxii.mitre.org/specifications/version1.1) server. 

Taxii10Template is a convenient way to connect a Spring application to a TAXII 1.0 server. This template allows you to
easily connect with a [TAXII 1.0](http://taxii.mitre.org/specifications/version1.0) server.

The Spring-TAXII library uses [TAXII-java](https://github.com/TAXIIProject/java-taxii) project for its JAXB 
implementation of the XML messages.

# Setup your gradle project

```gradle
    
    repositories {
        maven { url  "http://dl.bintray.com/threatcentral/maven" }
    }
    
    dependencies {
        compile "com.hpe.elderberry:elderberry:0.8"
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

    <bean name="taxiiConnection" class="com.hpe.elderberry.TaxiiConnection"
         p:discoveryUrl="http://hailataxii.com/taxii-discovery-service"
         p:useProxy="true"
    />
 
    <bean name="taxii10Template" class="com.hpe.elderberry.Taxii10Template"
         p:taxiiConnection-ref="taxiiConnection"
    />

    <bean name="taxii11Template" class="com.hpe.elderberry.Taxii11Template"
         p:taxiiConnection-ref="taxiiConnection"
    />
```

See the [JavaDocs](http://threatcentral.github.io/elderberry/index.html) for further details.

# Resources

* [TAXII-java](https://github.com/TAXIIProject/java-taxii)
* [TAXII 1.1](http://taxii.mitre.org)

# License

&copy; Copyright 2015 Hewlett Packard Enterprise Development LP Licensed under the Apache License, Version 2.0 (the 
"License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at 
[http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0) Unless required by applicable 
law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT 
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing
permissions and limitations under the License.
