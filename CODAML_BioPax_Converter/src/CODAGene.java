import org.json.simple.JSONObject;
import org.w3c.dom.Element;

public class CODAGene {
    JSONObject refIdentifier;
    MoleSpec moleSpec;

    void setRefIdentifier(Element geneElem){
        this.refIdentifier = CODAML2BioPAX.getJSONProperty(geneElem, "Ref_Identifier");
    }

    void setMoleSpec(Element geneElem){
        this.moleSpec = new MoleSpec();
        this.moleSpec.setMoleSpec(geneElem);
    }
}
