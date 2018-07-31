import org.json.simple.JSONObject;
import org.w3c.dom.Element;

public class Condition {
    String key;
    JSONObject value;

    void setKey(Element conditionElem) {
        this.key = CODAML2BioPAX.getProperty(conditionElem, "Key");
    }

    void setValue(Element conditionElem) {
        this.value = CODAML2BioPAX.getJSONProperty(conditionElem, "Value", "Ref_Identifier");
    }

    @Override
    public String toString() {
        return this.key + ": " + CODAML2BioPAX.JSON2String(this.value);
    }
}
