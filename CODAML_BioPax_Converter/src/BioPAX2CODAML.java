import org.biopax.paxtools.impl.level3.PhysicalEntityImpl;
import org.biopax.paxtools.impl.level3.PublicationXrefImpl;
import org.biopax.paxtools.impl.level3.SequenceEntityReferenceImpl;
import org.biopax.paxtools.impl.level3.UnificationXrefImpl;
import org.biopax.paxtools.io.BioPAXIOHandler;
import org.biopax.paxtools.io.SimpleIOHandler;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.*;
import org.json.simple.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class BioPAX2CODAML {

    private static <T extends Xref> String getXref(Class<T> c, Set<Xref> xrefs){
        List<String> strings = new ArrayList<>();

        for (Xref xref: xrefs){
            if (xref.getClass().equals(c)){
                strings.add(xref.toString());
            }
        }

        return strings.toString();
    }

    private static JSONObject getRefIdentifier(Set<Xref> xrefs) {
        JSONObject refIdentifier = new JSONObject();

        for (Xref xref : xrefs) {
            if (xref.getClass().equals(UnificationXrefImpl.class))
                CODAML2BioPAX.addToJson(refIdentifier, xref.getDb(), xref.getId());
        }

        return refIdentifier;
    }

    private static JSONObject getRefIdentifier(XReferrable bpEntity){
        JSONObject refIdentifier = getRefIdentifier(bpEntity.getXref());

        if (refIdentifier.isEmpty() && (bpEntity instanceof SimplePhysicalEntity)) {
            EntityReference er = ((SimplePhysicalEntity) bpEntity).getEntityReference();
            refIdentifier = getRefIdentifier(er.getXref());
        }

        return refIdentifier;
    }

    private static JSONObject getSpecies(Set<Xref> xrefs){
        JSONObject refIdentifier = new JSONObject();

        for (Xref xref: xrefs){
            if (xref.getClass().equals(UnificationXrefImpl.class))
                if (xref.getDb().toLowerCase().contains("taxon"))
                    CODAML2BioPAX.addToJson(refIdentifier, xref.getDb(), xref.getId());
        }

        return refIdentifier;
    }

    private static String getVoca(ControlledVocabulary voca){
        List<String> terms = new ArrayList<>();
        if (voca != null){
            terms.addAll(voca.getTerm());
        }

        return String.join(", ", terms);
    }

    private static JSONObject getVocaID(ControlledVocabulary voca){
        JSONObject refIdentifier = new JSONObject();

        if (voca != null)
            refIdentifier = getRefIdentifier(voca.getXref());

        return refIdentifier;
    }

    private static CODAGene getCODAGene(XReferrable bpEntity, CODAEntity entity, JSONObject species, ATMContext atmContext) {
        CODAGene gene = null;
        JSONObject refIdentifier = getRefIdentifier(bpEntity);

        if (!refIdentifier.isEmpty()){
            gene = new CODAGene();
            gene.refIdentifier = refIdentifier;
            gene.moleSpec = new MoleSpec();
            gene.moleSpec.type = bpEntity.getClass().getSimpleName().replace("Reference", "").replace("Impl", "");

            if (bpEntity instanceof PhysicalEntityImpl) {
                Set<EntityFeature> features = ((PhysicalEntityImpl) bpEntity).getFeature();
                for (EntityFeature feature : features) {
                    if (feature instanceof ModificationFeature) {
                        SequenceModificationVocabulary modVoca = ((ModificationFeature) feature).getModificationType();
                        String modType = getVoca(modVoca);
                        if (!modType.equals("[]")) {
                            gene.moleSpec.mods = new String[1];
                            gene.moleSpec.mods[0] = modType;
                        }
                    }
                }
            }

            if (bpEntity instanceof SequenceEntityReferenceImpl) {
                SequenceEntityReferenceImpl seqRef = (SequenceEntityReferenceImpl) bpEntity;
                BioSource bioSource = seqRef.getOrganism();
                if (bioSource != null) {
                    atmContext.tissue = getVocaID(bioSource.getTissue());
                    atmContext.cell = getVocaID(bioSource.getCellType());
                    if (!atmContext.tissue.isEmpty() || !atmContext.cell.isEmpty())
                        entity.atmContext = atmContext;

                    species = getSpecies(bioSource.getXref());
                }
            }
        }

        return gene;
    }

    private static Object[] getCODAEntity(XReferrable bpEntity){
        CODAEntity entity = null;
        JSONObject species = new JSONObject();
        ATMContext atmContext = new ATMContext();

        if (bpEntity instanceof Complex){
            Complex complex = (Complex) bpEntity;
            Set<PhysicalEntity> components = complex.getComponent();

            entity = new CODAEntity();
            entity.entityCore = new EntityCore();
            entity.entityCore.genes = new CODAGene[components.size()];

            int i = 0;
            for (PhysicalEntity component: components){
                entity.entityCore.genes[i++] = getCODAGene(component, entity, species, atmContext);
            }

            if (Arrays.asList(entity.entityCore.genes).contains(null))
                entity = null;
        }

        else {
            JSONObject refIdentifier = getRefIdentifier(bpEntity);

            if (!refIdentifier.isEmpty()) {
                entity = new CODAEntity();
                entity.entityCore = new EntityCore();

                if (bpEntity instanceof SmallMolecule) {
                    entity.entityCore.compound = refIdentifier;
                }

                else if (bpEntity instanceof PhysicalEntity || bpEntity instanceof Gene) {
                    CODAGene gene = getCODAGene(bpEntity, entity, species, atmContext);
                    if (gene != null) {
                        entity.entityCore.genes = new CODAGene[1];
                        entity.entityCore.genes[0] = gene;
                    }
                }
            }
        }

        Object[] result = new Object[2];
        result[0] = entity;
        result[1] = species;

        return result;
    }

    private static List<KU> getControl(List<CODAEntity> subjects, List<CODAEntity> objects, Interaction interaction){
        List<KU> kus = new ArrayList<>();
        JSONObject species = new JSONObject();

        if ((!subjects.contains(null)) && (!objects.contains(null))){
            Set<Control> controls = interaction.getControlledOf();
            for (Control control : controls) {
                List<CODAEntity> subjectsPerControl = new ArrayList<>(subjects);

                String predicate = "Positive Increase";
                if (control.getControlType() != null){
                    if (control.getControlType().toString().contains("INHIBITION"))
                        predicate = "Positive Decrease";
                }

                List<CODAEntity> controllers = new ArrayList<>();
                for (Controller controller : control.getController()) {
                    Object[] entityNSpecies = getCODAEntity(controller);

                    controllers.add((CODAEntity) entityNSpecies[0]);
                    CODAML2BioPAX.addToJson(species, (JSONObject) entityNSpecies[1]);
                }

                if (!controllers.contains(null)){
                    KU ku = new KU();
                    ku.predicate = predicate;

                    List<CODAEntity> subjectTmp = new ArrayList<>();
                    subjectTmp.addAll(subjects);
                    subjectTmp.addAll(controllers);

                    ku.subject = subjectTmp.toArray(new CODAEntity[0]);
                    ku.object = objects.toArray(new CODAEntity[0]);

                    if (control instanceof Catalysis){
                        CatalysisDirectionType cDirection = ((Catalysis) control).getCatalysisDirection();
                        if (cDirection != null){
                            if (cDirection.toString().contains("RIGHT-TO-LEFT")){
                                List<CODAEntity> subjectTmp2 = new ArrayList<>();
                                subjectTmp2.addAll(objects);
                                subjectTmp2.addAll(controllers);

                                ku.subject = subjectTmp2.toArray(new CODAEntity[0]);
                                ku.object = subjectsPerControl.toArray(new CODAEntity[0]);
                            }
                        }
                    }

                    if (!species.isEmpty())
                        ku.species = species;
                    kus.add(ku);
                }
            }

            if (controls.isEmpty()) {
                KU ku = new KU();
                ku.subject = subjects.toArray(new CODAEntity[0]);
                ku.predicate = "Positive Increase";
                ku.object = objects.toArray(new CODAEntity[0]);
                kus.add(ku);
            }
        }

        return kus;
    }

    private static List<KU> getBCReaction(BiochemicalReaction bcReaction) {
        List<CODAEntity> subjects = new ArrayList<>();
        List<CODAEntity> objects = new ArrayList<>();

        for (PhysicalEntity entity : bcReaction.getLeft())
            subjects.add((CODAEntity) getCODAEntity(entity)[0]);

        for (PhysicalEntity entity : bcReaction.getRight())
            objects.add((CODAEntity) getCODAEntity(entity)[0]);

        return getControl(subjects, objects, bcReaction);
    }

    private static List<KU> getTemplateReaction(TemplateReaction tReaction) {
        List<CODAEntity> subjects = new ArrayList<>();
        List<CODAEntity> objects = new ArrayList<>();

        subjects.add((CODAEntity) getCODAEntity(tReaction.getTemplate())[0]);

        for (PhysicalEntity entity : tReaction.getProduct())
            objects.add((CODAEntity) getCODAEntity(entity)[0]);

        return getControl(subjects, objects, tReaction);
    }

    private static void setAdditionalInfo(KU ku, List<String> prdIRefs, List<Reference> refs, JSONObject evScore){
        if (!prdIRefs.isEmpty())
            ku.prdIRefs = prdIRefs.toArray(new String[0]);

        if (!refs.isEmpty())
            ku.refs = refs.toArray(new Reference[0]);

        if (!evScore.isEmpty())
            ku.evScore = evScore;
    }

    private static List<KU> getInteraction(Interaction interaction){
        List<KU> kus = new ArrayList<>();

        List<String> prdIRefs = new ArrayList<>();
        for (InteractionVocabulary intType: interaction.getInteractionType())
            prdIRefs.add(getVoca(intType));

        List<Reference> refs = new ArrayList<>();
        for (Provenance provenance: interaction.getDataSource()){
            Reference reference = new Reference();
            reference.name = provenance.getStandardName();
            String xref = getXref(PublicationXrefImpl.class, provenance.getXref());
            if (xref.equals("[]"))
                reference.recordID = xref;
            refs.add(reference);
        }

        for (Xref xref: interaction.getXref()){
            if (!(xref instanceof RelationshipXref)) {
                Reference reference = new Reference();
                reference.name = xref.getDb();
                reference.recordID = xref.getId();
                Set<String> comments = xref.getComment();
                if (!comments.isEmpty())
                    reference.desc = comments.toString();
                refs.add(reference);
            }
        }

        JSONObject evScore = new JSONObject();
        for (Evidence evidence: interaction.getEvidence()){
            for (Score score: evidence.getConfidence()){
                Provenance scoreSource = score.getScoreSource();
                String key = "";
                if (scoreSource != null)
                    key = score.getScoreSource().getRDFId();
                String value = score.getValue();
                CODAML2BioPAX.addToJson(evScore, key, value);
            }
        }

        if (interaction instanceof BiochemicalReaction){
            kus.addAll(getBCReaction((BiochemicalReaction) interaction));
            for (KU ku: kus)
                setAdditionalInfo(ku, prdIRefs, refs, evScore);
        }

        else if (interaction instanceof TemplateReaction){
            kus.addAll(getTemplateReaction((TemplateReaction) interaction));
            for (KU ku: kus)
                setAdditionalInfo(ku, prdIRefs, refs, evScore);
        }

        else{
            KU ku = new KU();
            JSONObject species = new JSONObject();
            setAdditionalInfo(ku, prdIRefs, refs, evScore);

            List<CODAEntity> entities = new ArrayList<>();
            Object[]entityNSpecies;
            for (Entity entity: interaction.getParticipant()){
                entityNSpecies = getCODAEntity(entity);
                entities.add((CODAEntity) entityNSpecies[0]);
                CODAML2BioPAX.addToJson(species, (JSONObject) entityNSpecies[1]);

            }

            if (entities.size() > 1 && (!entities.contains(null))) {
                ku.subject = entities.subList(0, 1).toArray(new CODAEntity[0]);
                if (!species.isEmpty())
                    ku.species = species;
                ku.predicate = "Undirected Link";
                ku.object = entities.subList(1, entities.size()).toArray(new CODAEntity[0]);
                kus.add(ku);
            }
        }

        return kus;
    }

    private static CODAEntity[] getGO(String relType, String goID){
        CODAEntity[] entities = new CODAEntity[1];
        entities[0] = new CODAEntity();
        entities[0].entityCore = new EntityCore();

        JSONObject refIdentifier = new JSONObject();
        CODAML2BioPAX.addToJson(refIdentifier, "GENE ONTOLOGY", goID);

        if (relType.contains("MI:0359"))
            entities[0].entityCore.bp = refIdentifier;

        else
            entities[0].entityCore.mf = refIdentifier;

        return entities;
    }

    private static List<KU> getRelationshipXref(RelationshipXref relXref){
        List<KU> kus = new ArrayList<>();

        String relType = relXref.getRelationshipType().getXref().toString();
        if (relType.contains("MI:0359") || relType.contains("MI:0355")) {

            CODAEntity[] object = getGO(relType, relXref.getId());

            String prdIRef = getVoca(relXref.getRelationshipType());
            String[] prdIRefs;
            if (!prdIRef.equals("[]")) {
                prdIRefs = new String[1];
                prdIRefs[0] = prdIRef;
            }

            else
                prdIRefs = new String[0];

            Reference reference = new Reference();
            reference.name = relXref.getDb();
            reference.recordID = relXref.getId();
            reference.version = relXref.getDbVersion();
            reference.desc = relXref.getComment().toString();
            Reference[] references = new Reference[1];
            references[0] = reference;

            Set<XReferrable> molecules = relXref.getXrefOf();

            for (XReferrable molecule : molecules) {
                KU ku = new KU();

                ku.subject = new CODAEntity[1];
                Object[] entityNSpecies = getCODAEntity(molecule);
                ku.subject[0] = (CODAEntity) entityNSpecies[0];
                if (ku.subject[0] != null) {
                    ku.predicate = "Undirected Link";
                    ku.object = object;
                    ku.prdIRefs = prdIRefs;
                    JSONObject species = (JSONObject) entityNSpecies[1];
                    if (!species.isEmpty())
                        ku.species = species;
                    ku.refs = references;
                    kus.add(ku);
                }
            }
        }

        return kus;
    }

    private static HashSet<KU> readBioPAX(String filePath){
        HashSet<KU> kus = new HashSet<>();

        BioPAXIOHandler handler = new SimpleIOHandler();
        try{
            FileInputStream fIn = new FileInputStream(filePath);
            Model model = handler.convertFromOWL(fIn);
            fIn.close();

            for (Interaction interaction: model.getObjects(Interaction.class))
                kus.addAll(getInteraction(interaction));

            for (RelationshipXref relXref: model.getObjects(RelationshipXref.class))
                kus.addAll(getRelationshipXref(relXref));
        }

        catch (IOException e){
            throw new RuntimeException(e);
        }

        return kus;
    }

    private static Element createElement(Document doc, Element parent, String tagName, String textContent){
        Element element = null;

        if (textContent != null){
            element = doc.createElement(tagName);
            parent.appendChild(element);

            if (textContent.length()>0)
                element.setTextContent(textContent);
        }

        return element;
    }

    private static void createMultipleElements(Document doc, Element parent, String tagName, String[] textContents){
        if (textContents != null) {
            for (String textContent : textContents)
                createElement(doc, parent, tagName, textContent);
        }
    }

    private static Element createRefIdentifierElem(Document doc, Element parent, String tagName, JSONObject refIdentifier){
        return createJsonElem(doc, parent, tagName, "Ref_Identifier", "DBName", "Identifier", refIdentifier);
    }

    private static Element createJsonElem(Document doc, Element grandParent, String parentName, String jsonName, String keyName, String valueName, JSONObject jsonObject){
        Element element = null;

        if (jsonObject != null){
            element = createElement(doc, grandParent, parentName,"");

            for (Object key: jsonObject.keySet()){
                List<String> values = (List<String>) jsonObject.get(key);
                for (String value: values){
                    Element refIdElem = createElement(doc, element, jsonName, "");
                    createElement(doc, refIdElem, keyName, key.toString());
                    createElement(doc, refIdElem, valueName, value);
                }
            }
        }

        return element;
    }

    private static Element createEntitiesElem(Document doc, Element parent, String tagName, CODAEntity[] entities){
        Element entitiesElem = createElement(doc, parent, tagName, "");

        for (CODAEntity entity: entities) {
            Element entityElem = createElement(doc, entitiesElem, "Entity", "");
            Element ecElem = createElement(doc, entityElem, "Entity_Core", "");

            EntityCore ec = entity.entityCore;
            createRefIdentifierElem(doc, ecElem, "Biological_Process", ec.bp);
            createRefIdentifierElem(doc, ecElem, "Molecular_Function", ec.mf);
            createRefIdentifierElem(doc, ecElem, "Phenotype", ec.phen);
            createRefIdentifierElem(doc, ecElem, "Compound", ec.compound);

            if (ec.genes != null) {
                for (CODAGene gene : ec.genes) {
                    Element geneElem = createRefIdentifierElem(doc, ecElem, "Gene", gene.refIdentifier);

                    if (gene.moleSpec != null) {
                        Element moleSpecElem = createElement(doc, geneElem, "Molecule_Specification", "");
                        createElement(doc, moleSpecElem, "Molecule_Type", gene.moleSpec.type);
                        createRefIdentifierElem(doc, moleSpecElem, "Molecule_Isoform", gene.moleSpec.isoform);
                        createMultipleElements(doc, moleSpecElem, "Molecule_Modification", gene.moleSpec.mods);
                    }
                }

                if (entity.atmContext != null) {
                    Element atmElem = createElement(doc, entityElem, "Anatomical_Context", "");
                    // createRefIdentifierElem(doc, atmElem, "Organ", entity.atmContext.organ);  // would not be used since BioPAX cannot specify organ
                    createRefIdentifierElem(doc, atmElem, "Tissue", entity.atmContext.tissue);
                    createRefIdentifierElem(doc, atmElem, "Cell", entity.atmContext.cell);
                }
            }
        }

        return entitiesElem;
    }

    private static void writeCODAML(HashSet<KU> kus, String filePath){
        try {

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // root elements
            Document doc = docBuilder.newDocument();
            Element kusElem = doc.createElement("CODA_Knowledge_Units");
            doc.appendChild(kusElem);

            for (KU ku: kus){
                Element kuElem = createElement(doc, kusElem,"CODA_Knowledge_Unit", "");
                createEntitiesElem(doc, kuElem,"Subject", ku.subject);
                createElement(doc, kuElem, "Predicate", ku.predicate);
                createEntitiesElem(doc, kuElem,"Object", ku.object);
                createMultipleElements(doc, kuElem, "Predicate_In_Reference", ku.prdIRefs);
                createRefIdentifierElem(doc, kuElem, "Species", ku.species);


                for (Reference ref: ku.refs){
                    Element refElem = createElement(doc, kuElem, "Reference", "");
                    createElement(doc, refElem, "Reference_Type", ref.refType);
                    createElement(doc, refElem, "Name", ref.name);
                    createElement(doc, refElem, "Description", ref.desc);
                    createElement(doc, refElem, "Record_ID", ref.recordID);
                    createElement(doc, refElem, "Version", ref.version);
                    createElement(doc, refElem, "Acquisition_Date", ref.acqDate);
                }
                
                createJsonElem(doc, kuElem, "Evidence_Score", "Item", "Key", "Value", ku.evScore);
            }

            // write the content into xml file
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(new File(filePath));
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.transform(source, result);
        }

        catch (ParserConfigurationException pce) {
            pce.printStackTrace();
        }

        catch (TransformerException tfe) {
            throw new RuntimeException(tfe);
        }
    }

    public static void main(String[] args){
        String inputFilePath = "src/examples/biopax3-protein-interaction.owl";
        String outputFilePath = "biopax3-to-codaml.xml";

        HashSet<KU> kus = readBioPAX(inputFilePath);
        System.out.println(kus.size() + " Knowledge unit(s) found");
        writeCODAML(kus, outputFilePath);
    }
}
