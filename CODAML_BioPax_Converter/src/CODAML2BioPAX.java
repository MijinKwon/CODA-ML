import org.biopax.paxtools.io.BioPAXIOHandler;
import org.biopax.paxtools.io.SimpleIOHandler;
import org.biopax.paxtools.model.BioPAXElement;
import org.biopax.paxtools.model.BioPAXFactory;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.*;
import org.jetbrains.annotations.Nullable;
import org.json.simple.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;


public class CODAML2BioPAX {
    static Node getOneNode(Element element, String tagName){
        NodeList nodeList = element.getElementsByTagName(tagName);

        if (nodeList.getLength() > 1)
            throw new RuntimeException("Multiple " + tagName + "found");

        return nodeList.item(0);
    }

    static String getProperty(Element parent, String tagName){
        String property = "";

        Node tag = parent.getElementsByTagName(tagName).item(0);
        if (tag != null)
            property = tag.getTextContent();

        return property;
    }

    static String[] getArrayProperty(Element parent, String tagName) {
        NodeList tagList = parent.getElementsByTagName(tagName);
        String[] properties = new String[tagList.getLength()];

        for (int i = 0; i < tagList.getLength(); i++) {
            Node tag = tagList.item(i);
            properties[i] = tag.getTextContent();
        }

        return properties;
    }

    static void addToJson(JSONObject jsonObject, String key, String value){
        if (jsonObject.containsKey(key)){
            List<String> values = (List<String>) jsonObject.get(key);
            if (!values.contains(value))
                values.add(value);
        }

        else{
            List<String> values = new ArrayList<>();
            values.add(value);
            jsonObject.put(key, values);
        }
    }

    static void addToJson(JSONObject jsonObject, JSONObject jsonSub){
        for (Object key: jsonSub.keySet()){
            List<String> values = new ArrayList<>();
            for (String value: values){
                addToJson(jsonObject, key.toString(), value);
            }
        }
    }

    static JSONObject getJSONProperty(Node node, String itemName){
        JSONObject property = new JSONObject();

        if (node != null){
            String keyName = "Key";
            String valueName = "Score";
            if (itemName.equals("Ref_Identifier")){
                keyName = "DBName";
                valueName = "Identifier";
            }

            Element element = (Element) node;
            NodeList itemList = element.getElementsByTagName(itemName);

            for (int i=0; i<itemList.getLength(); i++){
                Element item = (Element) itemList.item(i);
                String key = getOneNode(item, keyName).getTextContent().toLowerCase();
                String value = getOneNode(item, valueName).getTextContent();
                addToJson(property, key, value);
            }
        }

        return property;
    }

    static JSONObject getJSONProperty(Element element, String tagName, String itemName){
        Node node = getOneNode(element, tagName);
        return getJSONProperty(node, itemName);
    }

    private static String getRepID(JSONObject refID){
        String repID = "";

        if (!refID.isEmpty()) {
            List<String> values = (List<String>) refID.values().toArray()[0];
            repID = values.get(0);
        }

        return repID;
    }


    static String JSON2String(JSONObject jsonObject){
        List<String> strings = new ArrayList<>();
        for (Object key: jsonObject.keySet()){
            List<String> values = (List<String>) jsonObject.get(key);
            strings.add(key.toString() + "(" + String.join(",", values) + ")");
        }

        return String.join(", ", strings);
    }

    private static KU[] readCODAML(String filePath){
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder;
        Document doc = null;

        KU[] kus;

        try {
            FileInputStream fIn = new FileInputStream(filePath);
            InputSource is = new InputSource(fIn);
            builder = factory.newDocumentBuilder();
            doc = builder.parse(is);
        }

        catch (Exception e){
            System.out.println("CODAML could not be read due to an error:" + e);
        }

        NodeList kuList = doc.getElementsByTagName("CODA_Knowledge_Unit");
        kus = new KU[kuList.getLength()];
        for (int i=0; i < kuList.getLength(); i++){
            kus[i] = new KU();
            Element kuElem = (Element) kuList.item(i);
            kus[i].setSubject(kuElem);
            kus[i].setPredicate(kuElem);
            kus[i].setObject(kuElem);
            kus[i].setenvCxts(kuElem);
            kus[i].setPrdIRefs(kuElem);
            kus[i].setSpecies(kuElem);
            kus[i].setRefs(kuElem);
            kus[i].setEvScore(kuElem);
            kus[i].setRelType();
        }

        return kus;
    }

    private static String list2ID(List<String> array, String delimiter){
        List<String> IDToBe = new ArrayList<>();
        for(String element:array){
            if (!element.isEmpty())
                IDToBe.add(element);
        }

        return String.join(delimiter, IDToBe);
    }


    private static String list2ID(List<String> list1, List<String> list2, String delimiter){
        List<String> IDToBe = new ArrayList<>();
        IDToBe.addAll(list1);
        IDToBe.addAll(list2);
        return list2ID(IDToBe, delimiter);
    }

    private static <T extends BioPAXElement> T createBPElement(Model model, Class<T> c, String ID) {
        ID = c.getSimpleName() + "_" + ID;

        T bpElement = (T) model.getByID(ID);
        if (bpElement == null) {
            bpElement = model.addNew(c, ID);
        }

        return bpElement;
    }

    private static void createUXref(Model model, XReferrable element, JSONObject refID) {
        for (Object key: refID.keySet()){
            String DB = (String) key;
            List<String> IDs = (List<String>) refID.get(DB);
            for (String ID: IDs){
                String rdfID = DB + "_" + ID;
                UnificationXref uXref = createBPElement(model, UnificationXref.class, rdfID);
                uXref.setDb(DB);
                uXref.setId(ID);
                element.addXref(uXref);
            }
        }
    }

    private static <T extends ControlledVocabulary> T createVoca(Model model, Class<T> c, JSONObject refID) {
        String cvID = c.getSimpleName() + "_" + getRepID(refID);
        T CV = (T) model.getByID(cvID);

        if (CV == null) {
            CV = model.addNew(c, cvID);
            createUXref(model, CV, refID);
            for (Object ID: refID.values()){
                CV.addTerm(ID.toString());
            }
        }

        return CV;
    }

    private static <T extends ControlledVocabulary> T createVoca(Model model, Class<T> c, String term) {
        String cvID = c.getSimpleName() + "_" + term.replace(" ", "%20");
        T CV = (T) model.getByID(cvID);

        if (CV == null) {
            CV = model.addNew(c, cvID);
            CV.addTerm(term);
        }

        return CV;
    }

    @Nullable
    private static BioSource getBioSource(Model model, ATMContext atmContext, JSONObject species){  // Cell line context might be added
        if (atmContext.organ.isEmpty() && atmContext.tissue.isEmpty() && atmContext.cell.isEmpty() && species.isEmpty())
            return null;

        JSONObject[] atmArray = {species, atmContext.organ, atmContext.tissue, atmContext.cell};
        String[] atmIDs = new String[4];

        for (int i=0; i<4; i++){
            atmIDs[i] = getRepID(atmArray[i]);
        }

        String bioSourceID = "BioSource_" + list2ID(Arrays.asList(atmIDs), ".");
        BioSource bioSource = (BioSource) model.getByID(bioSourceID);

        if (bioSource == null){
            bioSource = model.addNew(BioSource.class, bioSourceID);
            bioSource.setDisplayName(bioSourceID.split("_")[1]);

            if (!species.isEmpty()) {
                createUXref(model, bioSource, species);
            }

            for (int i=1; i<3; i++) {  // organ and tissue
                if (!atmArray[i].isEmpty()) {
                    TissueVocabulary tissue = createVoca(model, TissueVocabulary.class, atmArray[i]);
                    bioSource.setTissue(tissue);
                }
            }

            if (!atmContext.cell.isEmpty()){
                CellVocabulary cell = createVoca(model, CellVocabulary.class, atmContext.cell);
                bioSource.setCellType(cell);
            }
        }

        return bioSource;
    }

    private static <T extends EntityReference> T createRef(Model model, Class<T> c, String rdfID, JSONObject geneRefID, JSONObject isoRefID) {
        rdfID = c.getSimpleName() + "_" + rdfID;
        T ref = (T) model.getByID(rdfID);

        if (ref == null){
            ref = model.addNew(c, rdfID);
            ref.setDisplayName(rdfID);
            createUXref(model, ref, geneRefID);

            if (isoRefID.size()>0){
                createUXref(model, ref, isoRefID);
            }
        }

        return ref;
    }

    private static <T extends SequenceEntityReference> T createSeqRef(Model model, Class<T> c, JSONObject geneRefID, JSONObject isoRefID, String bioSourceID, BioSource bioSource) {
        String rdfID = getRepID(geneRefID) + "_" + bioSourceID;
        if (isoRefID.size()>0){
            rdfID = getRepID(isoRefID)+ "_" + bioSourceID;
        }

        T seqRef = createRef(model, c, rdfID, geneRefID, isoRefID);
        seqRef.setOrganism(bioSource);

        return seqRef;
    }

    private static <TE extends SimplePhysicalEntity> String createRefInst(Model model, Class<TE> c, EntityReference ref, JSONObject geneRefID, JSONObject isoRefID, String bioSourceID, String modName, Set<ModificationFeature> modSet) {
        String rdfID = c.getSimpleName() + "_" + getRepID(geneRefID);
        if (isoRefID.size()>0)
            rdfID = c.getSimpleName() + "_" + getRepID(isoRefID);

        if (!bioSourceID.isEmpty())
            rdfID += "_" + bioSourceID;

        if (!modName.isEmpty())
            rdfID += "_" + modName;

        TE pEntity = (TE) model.getByID(rdfID);

        if (pEntity == null){
            pEntity = model.addNew(c, rdfID);
            pEntity.setEntityReference(ref);
            pEntity.setDisplayName(rdfID);
            createUXref(model, pEntity, geneRefID);

            if (isoRefID.size()>0)
                createUXref(model, pEntity, isoRefID);

            if (modSet != null) {
                for (EntityFeature feature : modSet)
                    pEntity.addFeature(feature);
            }
        }

        return rdfID;
    }

    private static <T extends Gene> String createGene(Model model, Class<T> c, JSONObject geneRefID, JSONObject isoRefID, String bioSourceID, BioSource bioSource) {
        String rdfID = c.getSimpleName() + "_" + getRepID(geneRefID);
        if (isoRefID.size()>0)
            rdfID = c.getSimpleName() + "_" + getRepID(isoRefID);
        if (bioSourceID != "")
            rdfID += "_" + bioSourceID;

        T gene = (T) model.getByID(rdfID);
        if (gene == null){
            gene = model.addNew(c, rdfID);
            gene.setOrganism(bioSource);
            gene.setDisplayName(rdfID);
            createUXref(model, gene, geneRefID);

            if (isoRefID.size()>0)
                createUXref(model, gene, isoRefID);
        }

        return rdfID;
    }

    private static String createSeqEntity(Model model, String type, JSONObject geneRefID, JSONObject isoRefID, String modName, Set<ModificationFeature> modSet, BioSource bioSource) {
        String seqEntityID;
        String bioSourceID = "";
        if (bioSource != null)
            bioSourceID = bioSource.getRDFId().split("_")[1];

        switch (type) {
            case "Protein":
                ProteinReference pr = createSeqRef(model, ProteinReference.class, geneRefID, isoRefID, bioSourceID, bioSource);
                seqEntityID = createRefInst(model, Protein.class, pr, geneRefID, isoRefID, bioSourceID, modName, modSet);
                break;


            case "Rna":
                RnaReference rr = createSeqRef(model, RnaReference.class, geneRefID, isoRefID, bioSourceID, bioSource);
                seqEntityID = createRefInst(model, Rna.class, rr, geneRefID, isoRefID, bioSourceID, modName, modSet);
                break;

            case "Dna":
                DnaReference dr = createSeqRef(model, DnaReference.class, geneRefID, isoRefID, bioSourceID, bioSource);
                seqEntityID = createRefInst(model, Dna.class, dr, geneRefID, isoRefID, bioSourceID, modName, modSet);
                break;

            default:
                seqEntityID = createGene(model, Gene.class, geneRefID, isoRefID, bioSourceID, bioSource);
        }

        return seqEntityID;
    }


    private static String createGeneFromEntityCore(Model model, CODAGene codaGene, BioSource bioSource){
        String modName = "";
        HashSet<ModificationFeature> modSet = new HashSet<>();

        if (codaGene.moleSpec.mods.length>0) {
            modName = String.join(".", codaGene.moleSpec.mods);
            String[] mods = codaGene.moleSpec.mods;

            for (String mod: mods){
                ModificationFeature modFeature = createBPElement(model, ModificationFeature.class, mod);
                SequenceModificationVocabulary modVoca = createVoca(model, SequenceModificationVocabulary.class, mod);
                modFeature.setModificationType(modVoca);
                modSet.add(modFeature);
            }
        }

        return createSeqEntity(model, codaGene.moleSpec.type, codaGene.refIdentifier, codaGene.moleSpec.isoform, modName, modSet, bioSource);
    }

    private static List<String> createEntity(Model model, CODAEntity[] hs, KU ku){
        List<String> entityIDs = new ArrayList<>();

        for (CODAEntity entity: hs){
            BioSource bioSource = getBioSource(model, entity.atmContext, ku.species);
            EntityCore ec = entity.entityCore;

            if (ec.genes.length == 1){
                String geneID = createGeneFromEntityCore(model, ec.genes[0], bioSource);
                entityIDs.add(geneID);
            }

            else if (ec.genes.length > 1){
                String[] geneIDs = new String[ec.genes.length];
                for (int k=0; k<ec.genes.length; k++){
                    String geneID = createGeneFromEntityCore(model, ec.genes[k], bioSource);
                    geneIDs[k] = geneID;
                }

                Complex complex = createBPElement(model, Complex.class, String.join("+", geneIDs));
                complex.setDisplayName("Complex of " + String.join(", ", geneIDs));
                for (int k=0; k<ec.genes.length; k++){
                    PhysicalEntity component = (PhysicalEntity) model.getByID(geneIDs[k]);
                    complex.addComponent(component);
                }
                entityIDs.add(complex.getRDFId());
            }

            else if (!ec.compound.isEmpty()){
                JSONObject emptyObject = new JSONObject();
                String rdfID = getRepID(ec.compound);
                SmallMoleculeReference cr = createRef(model, SmallMoleculeReference.class, rdfID, ec.compound, emptyObject);
                String smID = createRefInst(model, SmallMolecule.class, cr, ec.compound, emptyObject, "","", null);
                entityIDs.add(smID);
            }

        }
        return entityIDs;
    }

    private static String getComment(String[] commentNames, String[] commentToBes){
        String comment = "";

        for (int j=0; j<commentToBes.length; j++){
            if (!commentToBes[j].isEmpty()){
                comment += (", " + commentNames[j] + ": " + commentToBes[j]);
            }
        }
        return comment.substring(2);
    }

    private static Provenance[] getProvenance(Model model, Reference[] references){
        Provenance[] srcs = new Provenance[references.length];

        for (int i=0; i<references.length; i++){
            Reference reference = references[i];
            String rdfID = "Provenance_" + reference.name;
            Provenance source = (Provenance) model.getByID(rdfID);
            if (source == null){
                source = model.addNew(Provenance.class, rdfID);
                source.setDisplayName(reference.name);

                PublicationXref xref = model.addNew(PublicationXref.class, "PublicationXref_" + reference.recordID);
                xref.addUrl(reference.recordID);
                source.addXref(xref);

                String[] commentNames = {"Type", "Description", "Version", "Acquisition date"};
                String[] commentToBes = {reference.refType, reference.desc, reference.version, reference.acqDate};
                source.addComment(getComment(commentNames, commentToBes));
            }
            srcs[i] = source;
        }

        return srcs;
    }

    private static Evidence[] getEvidence(Model model, JSONObject jEvScore){
        Evidence[] evds = new Evidence[jEvScore.size()];

        int i = 0;
        for (Object keyObject: jEvScore.keySet()) {
            String key = (String) keyObject;
            List<String> values = (List<String>) jEvScore.get(key);
            String name = key.replace(" ", "%20") + "." + values.get(0);

            Evidence evidence = (Evidence) model.getByID("Evidence_" + name);
            if (evidence == null) {
                evidence = model.addNew(Evidence.class, "Evidence_" + name);
                Score score = model.addNew(Score.class, "Score_" + name);
                score.setValue(values.get(0));
                evidence.addConfidence(score);
            }

            evds[i] = evidence;
            i += 1;
        }

        return evds;
    }

    private static <T extends Interaction> T createInteraction(Model model, Class<T> c, String ID, Provenance[] srcs, Evidence[] evds, Condition[] envCxts, String[] prdIRefs){
        String rdfID =  c.getSimpleName() + "_" + ID;
        T interaction = model.addNew(c, rdfID);
        interaction.setDisplayName(c.getSimpleName() + " among " + ID.replace("-", ", "));

        for (Provenance src: srcs)
            interaction.addDataSource(src);

        for (Evidence evd: evds)
            interaction.addEvidence(evd);

        if (envCxts.length > 0)
            interaction.addComment("Environmental context: " + Arrays.toString(envCxts));

        for (String prdIRef :prdIRefs){  // InteractionVocabulary should have 1 xref value
            InteractionVocabulary intType = createVoca(model, InteractionVocabulary.class, prdIRef);
            interaction.addInteractionType(intType);
        }

        return interaction;
    }

    private static <T extends Interaction> void createUndirectedInt(Model model, Class<T> c, List<String> entityIDs, Provenance[] srcs, Evidence[] evds, Condition[] envCxts, String[] prdIRefs){
        String ID = String.join("-", entityIDs);
        T interaction = createInteraction(model, c, ID, srcs, evds, envCxts, prdIRefs);
        for(String entityID: entityIDs) {
            Entity entity = (Entity) model.getByID(entityID);
            interaction.addParticipant(entity);
        }
    }

    private static <T extends Control> void createControl(Model model, Class<T> c, List<String> enzymes, Interaction controlled, Provenance[] srcs, Evidence[] evds, Condition[] envCxts, String[] prdIRefs){
        for (String enzymeID: enzymes){
            PhysicalEntity enzyme = (PhysicalEntity) model.getByID(enzymeID);
            T control = createInteraction(model, c, enzymeID, srcs, evds, envCxts, prdIRefs);
            control.addController(enzyme);
            control.addControlled(controlled);
            if (prdIRefs.toString().toLowerCase().contains("activation"))
                control.setControlType(ControlType.ACTIVATION);
            if (prdIRefs.toString().toLowerCase().contains("inhibition"))
                control.setControlType(ControlType.INHIBITION);

            if (control instanceof Catalysis){
                CatalysisDirectionType cDirection = CatalysisDirectionType.LEFT_TO_RIGHT;
                ((Catalysis) control).setCatalysisDirection(cDirection);
            }
        }
    }

    private static void createBCReaction(Model model, List<String> substrates, List<String> products, List<String> enzymes, Provenance[] srcs, Evidence[] evds, Condition[] envCxts, String[] prdIRefs){
        String ID = list2ID(substrates, products, "-");

        BiochemicalReaction bcr = createInteraction(model, BiochemicalReaction.class, ID, srcs, evds, envCxts, prdIRefs);

        ConversionDirectionType cdType = ConversionDirectionType.LEFT_TO_RIGHT;
        bcr.setConversionDirection(cdType);

        for (String substrate: substrates){
            PhysicalEntity pe = (PhysicalEntity) model.getByID(substrate);
            bcr.addLeft(pe);
        }

        for (String product: products){
            PhysicalEntity pe = (PhysicalEntity) model.getByID(product);
            bcr.addRight(pe);
        }

        createControl(model, Catalysis.class, enzymes, bcr, srcs, evds, envCxts, prdIRefs);
    }

    private static <TNew extends SimplePhysicalEntity> TNew createEntityFromExistingOne(Model model,  Class<TNew> c, String entityID, SimplePhysicalEntity entity){
        SequenceEntityReference entityRef = (SequenceEntityReference) entity.getEntityReference();
        BioSource bioSource = entityRef.getOrganism();

        String ID = entityID.split("_")[1];
        JSONObject geneRefID = new JSONObject();
        JSONObject isoRefID = new JSONObject();

        for (Xref xRef: entity.getXref()){
            if (xRef.getId().equals(ID))
                addToJson(isoRefID, xRef.getDb(), xRef.getId());

            else
                addToJson(geneRefID, xRef.getDb(), xRef.getId());
        }

        if (geneRefID.isEmpty())
            geneRefID = isoRefID;

        String type = c.getSimpleName().replace("Impl", "");
        String newEntityID = createSeqEntity(model, type, geneRefID, isoRefID, "", null, bioSource);
        TNew newEntity = (TNew) model.getByID(newEntityID);

        return newEntity;
    }

    private static void createModification(Model model, List<String> lEntityIDs, List<String> rEntityIDs, Provenance[] srcs, Evidence[] evds, Condition[] envCxts, String[] prdIRefs){
        List<String> entitiesNoMod = new ArrayList<>();

        for (String entityID: rEntityIDs){
            SimplePhysicalEntity rEntity = (SimplePhysicalEntity) model.getByID(entityID);
            SimplePhysicalEntity entityNoMod = createEntityFromExistingOne(model, rEntity.getClass(), entityID, rEntity);
            entitiesNoMod.add(entityNoMod.getRDFId());
        }

        createBCReaction(model, entitiesNoMod, rEntityIDs, lEntityIDs, srcs, evds, envCxts, prdIRefs);
    }

    private static void createTemplateReaction(Model model, List<String> lEntityIDs, List<String> rEntityIDs, Provenance[] srcs, Evidence[] evds, Condition[] envCxts, String[] prdIRefs){
        String name = list2ID(rEntityIDs, "-");
        TemplateReaction tr = createInteraction(model, TemplateReaction.class, name, srcs, evds, envCxts, prdIRefs);

        for(String entityID: rEntityIDs) {
            SimplePhysicalEntity entity = (SimplePhysicalEntity) model.getByID(entityID);
            tr.addProduct(entity);

            Dna dna = createEntityFromExistingOne(model, Dna.class, entityID, entity);
            tr.setTemplate(dna);
        }
        createControl(model, TemplateReactionRegulation.class, lEntityIDs, tr, srcs, evds, envCxts, prdIRefs);
    }

    private static void createRelXref(Model model, String geneID, CODAEntity phenomeEntity, String entityType, Reference[] refs, JSONObject evScore, Condition[] envCxts, String[] prdIRefs){
        JSONObject phenomeRefID;
        if (entityType.equals("biological_process"))
            phenomeRefID = phenomeEntity.entityCore.bp;
        else if (entityType.equals("molecular_function"))
            phenomeRefID = phenomeEntity.entityCore.mf;
        else
            phenomeRefID = phenomeEntity.entityCore.phen;

        String phenomeID = getRepID(phenomeRefID);
        RelationshipXref relXref = model.addNew(RelationshipXref.class, "RelationshipXref_" + geneID + "-" + phenomeID);
        relXref.setDb(refs[0].name);  // need to be modified
        relXref.setDbVersion(refs[0].version);
        relXref.setId(phenomeID);

        for (Reference reference: refs){
            String[] commentNames = {"Reference Type", "Description", "Acquisition date"};
            String[] commentToBes = {reference.refType, reference.desc, reference.acqDate};
            relXref.addComment(getComment(commentNames, commentToBes));
        }

        if (evScore.size() > 0)
            relXref.addComment("Evidence score: " + JSON2String(evScore));

        if (envCxts.length > 0)
            relXref.addComment("Environmental context: " + Arrays.toString(envCxts));

        for (String prdIRef: prdIRefs){
            RelationshipTypeVocabulary relType = createVoca(model, RelationshipTypeVocabulary.class, prdIRef);
            relXref.setRelationshipType(relType);
        }

        XReferrable gene = (XReferrable) model.getByID(geneID);
        gene.addXref(relXref);
    }

    private static void writeOWL(KU[] kus, String filePath){
        BioPAXFactory factory = BioPAXLevel.L3.getDefaultFactory();
        Model model = factory.createModel();
        String xmlBase = "http://coda.kaist.edu/codaml-biopax-examples/";
        model.setXmlBase(xmlBase);

        for(KU ku: kus){
            List<String> lEntityIDs = createEntity(model, ku.subject, ku);
            List<String> rEntityIDs = createEntity(model, ku.object, ku);

            Provenance[] srcs = null;
            Evidence[] evds = null;
            if (!ku.relType.equals("RelationshipXref")) {
                srcs = getProvenance(model, ku.refs);
                evds = getEvidence(model, ku.evScore);
            }

            if (ku.relType.equals("Interaction")) {
                lEntityIDs.addAll(rEntityIDs);
                createUndirectedInt(model, Interaction.class, lEntityIDs, srcs, evds, ku.envCxts, ku.prdIRefs);
            }

            else if (ku.relType.equals("MolecularInteraction")) {
                lEntityIDs.addAll(rEntityIDs);
                createUndirectedInt(model, MolecularInteraction.class, lEntityIDs, srcs, evds, ku.envCxts, ku.prdIRefs);
            }

            else if (ku.relType.equals("BiochemicalReaction")) {
                List<String> enzymes = new ArrayList<>();
                List<String> substrates = new ArrayList<>();

                for (String entityID: lEntityIDs){
                    PhysicalEntity entity = (PhysicalEntity) model.getByID(entityID);
                    if (entity instanceof SmallMolecule)
                        substrates.add(entityID);
                    else
                        enzymes.add(entityID);
                }

                createBCReaction(model, substrates, rEntityIDs, enzymes, srcs, evds, ku.envCxts, ku.prdIRefs);
            }

            else if (ku.relType.equals("Modification"))
                createModification(model, lEntityIDs, rEntityIDs, srcs, evds, ku.envCxts, ku.prdIRefs);

            else if (ku.relType.equals("TemplateReaction"))
                createTemplateReaction(model, lEntityIDs, rEntityIDs, srcs, evds, ku.envCxts, ku.prdIRefs);

            else if (ku.relType.equals("RelationshipXref")){
                if (ku.lType.equals("molecule"))
                    createRelXref(model, lEntityIDs.get(0), ku.object[0], ku.rType, ku.refs, ku.evScore, ku.envCxts, ku.prdIRefs);  // need to be modified
                else if (ku.rType.equals("molecule"))
                    createRelXref(model, rEntityIDs.get(0), ku.subject[0], ku.lType, ku.refs, ku.evScore, ku.envCxts, ku.prdIRefs);  // need to be modified
            }
        }

        BioPAXIOHandler handler = new SimpleIOHandler();
        try{
            FileOutputStream output = new FileOutputStream(filePath);
            handler.convertToOWL(model, output);
            output.close();
        }
        catch (IOException e){
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args){
        String inputFilePath = "src/examples/codaml_examples.xml";
        String outputFilePath = "codaml_examples.owl";

        KU[] kus = readCODAML(inputFilePath);
        System.out.println(kus.length + " knowledge unit(s) found");
        writeOWL(kus, outputFilePath);
    }
}
