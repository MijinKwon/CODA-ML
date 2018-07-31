import org.json.simple.JSONObject;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class MoleSpec {
    String type;
    String[] mods;
    JSONObject isoform;

    private void setType(Element moleSpecElem){
        String rawType = CODAML2BioPAX.getProperty(moleSpecElem, "Molecule_Type");
        this.type = rawType.substring(0, 1).toUpperCase() + rawType.substring(1).toLowerCase();
    }

    private void setMods(Element moleSpecElem){
        NodeList modList = moleSpecElem.getElementsByTagName("Molecule_Modification");

        if (modList != null){
            this.mods = new String[modList.getLength()];
            for (int i=0; i < modList.getLength(); i++){
                Node mod = modList.item(i);
                this.mods[i] = mod.getTextContent();
            }
        }

        else
            this.mods = new String[0];
    }

    private void setIsoform(Element moleSpecElem){
        this.isoform = CODAML2BioPAX.getJSONProperty(moleSpecElem, "Molecule_Isoform", "Ref_Identifier");
    }

    void setMoleSpec(Element productElem){
        Element moleSpec = (Element) CODAML2BioPAX.getOneNode(productElem, "Molecule_Specification");
        if (moleSpec != null){
            this.setType(moleSpec);
            this.setMods(moleSpec);
            this.setIsoform(moleSpec);
        }

        else{
            this.type = "";
            this.mods = new String[0];
            this.isoform = new JSONObject();
        }
    }
}
