<h1 align="center"> Kids First Extractor </h1> <br>

<p align="center">
  Solution to extract all data currently available on Kids First gen3, and compile into single files for each data type.
</p>


## Table of Contents

- [Introduction](#introduction)
- [Requirements](#requirements)
- [Quick Start](#quick-start)
- [Reference](#reference)


## Introduction

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/7afa4461328e4292b9967aedef235d99)](https://www.codacy.com/app/joneubank/kids-first-extractor?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=joneubank/kids-first-extractor&amp;utm_campaign=Badge_Grade)

Command line application written with Scala and using Spark.


## Requirements
The following software is required to run the application:

* [Java 1.8](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)
* [Scala SBT](https://www.scala-lang.org/download/)

## Quick Start

### Config
Copy `resources/gen3.template.yml` to `resources/gen3.yml` and update all values as required. 

To generate an ID and Secret combination, go to your [Profile](https://gen3.kids-first.io/identity) page and click __Create access key__ to get a new ID/Key pair.

### Run
```$bash
sbt run
```

Look for output in `data/` directory.

## Script Details
The Extractor script will first request from Gen3 Submission portal the total number of each document type available on the server.

These counts are passed in to a spark script which will:
* Request ID and ProjectID for each document from Gen3 for every node type (uses available count to ensure we get all of them)
* Parse responses responses into list of ID, ProjectID tuples
* Request each document from Gen3 Export service using these properties
* Combine every document into a single file for each Node type

Files are saved in the `data/` directory, in a new directory named with the start time of the script


## Reference
Kids First:
 * [Submission Portal](https://gen3.kids-first.io/) - Requires Approved Access
 * [Data Dictionary](https://gen3.kids-first.io/dd)

For Reverse Engineering the Auth Signing:
 * [UC PyUtils - HMAC](https://github.com/uc-cdis/cdis-python-utils/tree/master/cdispyutils/hmac4)


