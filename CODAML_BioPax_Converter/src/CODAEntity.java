import org.json.simple.JSONObject;
import org.w3c.dom.Element;

public class CODAEntity {
    EntityCore entityCore;
    ATMContext atmContext;

    private String setEntityCore(Element entityElem){

        String type;
        Element entityCore = (Element) CODAML2BioPAX.getOneNode(entityElem, "Entity_Core");

        this.entityCore = new EntityCore();
        this.entityCore = new EntityCore();
        this.entityCore.setPhen(entityCore);
        this.entityCore.setBp(entityCore);
        this.entityCore.setMf(entityCore);
        this.entityCore.setCompound(entityCore);
        this.entityCore.setGenes(entityCore);

        if (!this.entityCore.phen.isEmpty())
            type = "phenotype";
        else if (!this.entityCore.bp.isEmpty())
            type = "biological_process";
        else if (!this.entityCore.mf.isEmpty())
            type = "molecular_function";
        else if (!this.entityCore.compound.isEmpty() || this.entityCore.genes.length > 0)
            type = "molecule";
        else
            throw new RuntimeException("No valid entity type found");

        return type;
    }

    private void setAtmContext(Element entityElem){
        Element atmContext = (Element) CODAML2BioPAX.getOneNode(entityElem, "Anatomical_Context");
        this.atmContext = new ATMContext();

        if (atmContext != null){
            this.atmContext.setOrgan(atmContext);
            this.atmContext.setTissue(atmContext);
            this.atmContext.setCell(atmContext);
        }

        else{
            this.atmContext.organ = new JSONObject();
            this.atmContext.tissue = new JSONObject();
            this.atmContext.cell = new JSONObject();
        }
    }

    String setEntity(Element entityElem){
        String type = this.setEntityCore(entityElem);
        this.setAtmContext(entityElem);

        return type;
    }
}
