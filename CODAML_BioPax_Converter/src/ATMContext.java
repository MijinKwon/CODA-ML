import org.json.simple.JSONObject;
import org.w3c.dom.Element;

public class ATMContext {
    JSONObject organ;
    JSONObject tissue;
    JSONObject cell;

    void setOrgan(Element atmContextElem){
        this.organ = CODAML2BioPAX.getJSONProperty(atmContextElem, "Organ", "Ref_Identifier");
    }

    void setTissue(Element atmContextElem){
        this.tissue = CODAML2BioPAX.getJSONProperty(atmContextElem, "Tissue", "Ref_Identifier");
    }

    void setCell(Element atmContextElem){
        this.cell = CODAML2BioPAX.getJSONProperty(atmContextElem, "Cell", "Ref_Identifier");
    }

}
