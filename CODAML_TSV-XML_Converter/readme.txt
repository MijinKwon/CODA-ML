"codaml_converter.py" converts an input file ("codaml_converter_input.tsv") to an xml file ("codaml_examples.xml") compliant to codaml.
The input file is in tab-separated values (tsv), in which each columns are delimited by tab.
The first row of the input file is column headers, and will be ignored by "codaml_converter.py".
Each line after the first line represents one knowledge unit.
Followings are explanations for each columns of the input file.

1. Subject
The first column represents subject of a knowledge unit.
Subject can be composed of one or more entity, separated by comma.
Each entity is organized as "entity core@organ ID@tissue ID@cell ID".
If any of organ, tissue, cell is not available, it can be referred as "NA" (not available).
Entity core is represented as "entity type:entity ID#molecule type#molecule modification#isoform ID".
Entity types are simplified to "GP", "CP", "BP", "MF", and "PH" for "gene product", "compound product", "biological process", "molecular function", and "phenotype", respectively.
The following ontologies are used as reference identifiers for entities: Ensembl for genes, transcripts and proteins, STITCH for compounds, Gene Ontology for biological processes and molecular functions, UMLS for (patho)phenotypes, and Medical Subject Headings (MeSH) for organs, tissues, cells
More than one entity core is can be separated by "&".

2. Predicate
Predicate can be one of the eleven controlled vocabularies: Undirected link, Positive correlation, Negative correlation, Directed link, Positive cause, Negative cause, Positive increase, Negative decrease, Positive decrease, Negative increase, and Missing interaction.

3. Object
Object is structured the same as Subject.

4. Environmental context
Environmental context is represented as a key-value pair, separated by colon ":".
If any kinds of environmental context is not applicable, then it has "NA" as its value.

5. Predicatre in reference
'Predicatre in reference' can be plaintexts that describe the original predicate.
If not applicable, it can be referred as "NA".

6. Species
Medical Subject Headings (MeSH) is used as reference identifier for species.

7. Reference
Reference represents reference type, name, description, record id, version, and acquisition date, delimited by two consecutive underscores, "__".
The order should be consistent with the order mentioned above.

8. Evidence score
Evidence score should be structured as a key-value pair, separated by colon ":".
