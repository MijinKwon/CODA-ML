import org.w3c.dom.Element;

public class Reference {
    String refType;
    String name;
    String desc;
    String recordID;
    String version;
    String acqDate;

    void setRefType(Element refElem){
        this.refType = CODAML2BioPAX.getProperty(refElem, "Reference_Type");
    }

    void setName(Element refElem){
        this.name = CODAML2BioPAX.getProperty(refElem, "Name");
    }

    void setDesc(Element refElem){
        this.desc = CODAML2BioPAX.getProperty(refElem, "Description");
    }

    void setRecordID(Element refElem){
        this.recordID = CODAML2BioPAX.getProperty(refElem, "Record_ID");
    }

    void setVersion(Element refElem){
        this.version = CODAML2BioPAX.getProperty(refElem, "Version");
    }

    void setAcqDate(Element refElem){
        this.acqDate = CODAML2BioPAX.getProperty(refElem, "Acquisition_Date");
    }
}
