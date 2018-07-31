# CODAML-BioPAX converter
CODAML-BioPAX converter a java library for converting CODAML to BioPAX or vice versa.
CODAML2BioPAX.java converts CODAML to BioPAX.
BioPAX2CODAML.java converts BioPAX to CODAML.

## Installation

### Requirements
* Java 1.8 or up

## Usage
In the main function of CODAML2BioPAX.java and BioPAX2CODAML.java, they take 2 String variables: inputFilePath and outputFilePath.
For example, CODAML2BioPAX.java takes path to the CODAML file as inputFilePath, read it and convert it into BioPAX, and then save the BioPAX to outputFilePath.

## Notes

### CODAML2BioPAX.java
* Since BioPAX does not provide a way to represent organ information, we convert 'Organ' of CODAML to 'tissue' of BioPAX.
* Since BioPAX does not provide a way to represent relations between biological processes or phenotypes, they are not converted to BioPAX.

### BioPAX2CODAML.java
* Since BioPAX does not differentiate gene IDs and isoform IDs, all IDs are converted to gene IDs even if it is isoform ID.
