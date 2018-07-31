import org.json.simple.JSONObject;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.List;

public class KU {
    CODAEntity[] subject;
    String predicate;
    CODAEntity[] object;
    Condition[] envCxts;
    String[] prdIRefs;
    JSONObject species;
    Reference[] refs;
    JSONObject evScore;
    String lType;
    String rType;
    String relType;

    void setSubject(Element kuElem){
        Element LHS = (Element) CODAML2BioPAX.getOneNode(kuElem, "Subject");
        NodeList entityList = LHS.getElementsByTagName("Entity");
        this.subject = new CODAEntity[entityList.getLength()];

        for (int i=0; i < entityList.getLength(); i++) {
            Element entity = (Element) entityList.item(i);
            this.subject[i] = new CODAEntity();
            this.lType = this.subject[i].setEntity(entity);
        }
    }

    void setPredicate(Element kuElem){
        Node asso = CODAML2BioPAX.getOneNode(kuElem, "Predicate");
        this.predicate = asso.getTextContent();
    }

    void setObject(Element kuElem){
        Element RHS = (Element) CODAML2BioPAX.getOneNode(kuElem, "Object");
        NodeList entityList = RHS.getElementsByTagName("Entity");
        this.object = new CODAEntity[entityList.getLength()];

        for (int i=0; i < entityList.getLength(); i++) {
            Element entity = (Element) entityList.item(i);
            this.object[i] = new CODAEntity();
            this.rType = this.object[i].setEntity(entity);
        }
    }

    void setenvCxts(Element kuElem) {
        Element envElem = (Element) CODAML2BioPAX.getOneNode(kuElem, "Environmental_Context");
        if (envElem != null){
            NodeList conditionList = envElem.getElementsByTagName("Condition");
            this.envCxts = new Condition[conditionList.getLength()];

            for (int i=0; i<conditionList.getLength(); i++){
                Element condition = (Element) conditionList.item(i);
                this.envCxts[i] = new Condition();
                this.envCxts[i].setKey(condition);
                this.envCxts[i].setValue(condition);
            }
        }

        else{
            this.envCxts = new Condition[0];
        }
    }

    void setPrdIRefs(Element kuElem){
        this.prdIRefs = CODAML2BioPAX.getArrayProperty(kuElem, "Predicate_In_Reference");
    }

    void setSpecies(Element kuElem){
        this.species = CODAML2BioPAX.getJSONProperty(kuElem, "Species", "Ref_Identifier");
    }

    void setRefs(Element kuElem){
        NodeList referenceList = kuElem.getElementsByTagName("Reference");
        this.refs = new Reference[referenceList.getLength()];

        for (int i=0; i < referenceList.getLength(); i++) {
            Element reference = (Element) referenceList.item(i);
            this.refs[i] = new Reference();
            this.refs[i].setRefType(reference);
            this.refs[i].setName(reference);
            this.refs[i].setDesc(reference);
            this.refs[i].setRecordID(reference);
            this.refs[i].setVersion(reference);
            this.refs[i].setAcqDate(reference);
        }
    }

    void setEvScore(Element kuElem){
        this.evScore = CODAML2BioPAX.getJSONProperty(kuElem, "Evidence_Score", "Item");
    }

    void setRelType(){
        List prdIRefsList = Arrays.asList(this.prdIRefs);
        if (prdIRefsList.contains("React"))
            this.relType = "BiochemicalReaction";
        else if (prdIRefsList.contains("Phosphorylation"))
            this.relType = "Modification";
        else if (prdIRefsList.contains("Expression"))
            this.relType = "TemplateReaction";
        else if (prdIRefsList.contains("Protein-protein interaction") || prdIRefsList.contains("Protein-DNA interaction"))
            this.relType = "MolecularInteraction";

        else if (!lType.equals("molecule") || !rType.equals("molecule"))
            this.relType = "RelationshipXref";
        else
            this.relType = "Interaction";
    }
}
