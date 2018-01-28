[Synopsis]
CODA-ML: XML-based markup language format for modeling personalized qualitative systems

[Environment]
python-3.6.1

[How to use]
"codaml_converter.py" converts an input file ("codaml_converter_input.tsv") to an xml file ("codaml_examples.xml") compliant to codaml.
The input file is in tab-separated values (tsv), in which each columns are delimited by tab.
The first row of the input file is column headers, and will be ignored by "codaml_converter.py".
Each line after the first line represents one knowledge unit.
Followings are explanations for each columns of the input file.

1. LHS

The first column represents left-hand side (LHS) of a knowledge unit.
LHS can be composed of one or more entity, separated by comma.
Each entity is organized as "entity core@organ@tissue@cell".
If any of organ, tissue, cell is not available, it can be referred as "NA" (not available).
Entity core is represented as "entity type:entity name#type#modification#isoform".
Entity types are simplified to "GP", "CP", "BP", "MF", and "PH" for "gene product", "compound product", "biological process", "molecular function",and "phenotype", respectively. More than one entity core is can be separated by "&".

2. Association

Association can be one of the ten controlled vocabulary: Undirected link, Positive correlation, Negative correlation, Directed link, Positive cause, Negative cause, Positive increase, Negative decrease, Positive decrease, and Negative increase.

3. RHS

RHS is structured the same as LHS

4. Association context

Association context is represented as a key-value pair, separated by colon ":".
If any kinds of association context is not applicable, then it has "NA" as its value.

5. Association in source

Association in source can be free text that describes the original association.
If not applicable, it can be referred as "NA".

6. Species

Free text that describes species in which the knowledge unit is observed.

7. Reference

Reference represents reference type, name, description, record id, version, and acquisition date, delimited by two consecutive underscores, "__".
The order should be consistent with the order mentioned above.

8. Evidence score

Evidence score should be structured as a key-value pair, separated by colon ":".
