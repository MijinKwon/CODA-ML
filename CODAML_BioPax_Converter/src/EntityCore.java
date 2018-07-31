import org.json.simple.JSONObject;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class EntityCore {
    JSONObject phen;
    JSONObject bp;
    JSONObject mf;
    JSONObject compound;
    CODAGene[] genes;

    void setPhen(Element entityCoreElem) {
        this.phen = CODAML2BioPAX.getJSONProperty(entityCoreElem, "Phenotype", "Ref_Identifier");
    }

    void setBp(Element entityCoreElem) {
        this.bp = CODAML2BioPAX.getJSONProperty(entityCoreElem, "Biological_Process", "Ref_Identifier");
    }

    void setMf(Element entityCoreElem) {
        this.mf = CODAML2BioPAX.getJSONProperty(entityCoreElem, "Molecular_Function", "Ref_Identifier");
    }

    void setCompound(Element entityCoreElem) {
        this.compound = CODAML2BioPAX.getJSONProperty(entityCoreElem, "Compound", "Ref_Identifier");
    }

    void setGenes(Element entityCoreElem) {
        NodeList geneList = entityCoreElem.getElementsByTagName("Gene");

        if (geneList != null) {
            this.genes = new CODAGene[geneList.getLength()];
            for (int i=0; i < geneList.getLength(); i++){
                Element gene = (Element) geneList.item(i);
                this.genes[i] = new CODAGene();
                this.genes[i].setRefIdentifier(gene);
                this.genes[i].setMoleSpec(gene);
            }
        }

        else
            this.genes = new CODAGene[0];
    }
}
