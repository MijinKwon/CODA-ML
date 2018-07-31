import re
from datetime import datetime
import xml.etree.ElementTree as ET
from xml.dom import minidom


def natural_sort(l):
    convert = lambda text: int(text) if text.isdigit() else text.lower()
    alphanum_key = lambda key: [convert(c) for c in re.split('([0-9]+)', key)]
    return sorted(l, key=alphanum_key)


class phenomenon(object):
    __slots__ = ["entitycore", "geneType", "geneMod", "geneIsoform", "organ", "tissue", "cell"]

    def __init__(self, pheno, mode):
        if mode == "w":
            pheno_list = pheno.split(",")
            try:
                self.entitycore = pheno_list[0]
                self.organ = pheno_list[1]
                self.tissue = pheno_list[2]
                self.cell = pheno_list[3]
            except IndexError:
                raise ValueError("#### A phenomenon should include 6 entries, i.e. entity, geneType, geneMod, organ, tissue, cell. (NA or proper value).")

        if mode == "r":
            self.reader(pheno)

    def __str__(self):
        event = [self.entitycore,  self.organ, self.tissue, self.cell]
        event = [x if x != "NA" else "" for x in event]
        return "@".join(event)

    def reader(self, pheno):
        self.entitycore, self.organ, self.tissue, self.cell = pheno.split("@")


class association(object):
    __slots__ = ["asso"]

    def __init__(self, asso_str):
        self.asso = asso_str

    def __str__(self):
        return self.asso


class associationContext(object):
    __slots__ = ["assoCntx"]

    def __init__(self, assoCntx_dict):
        self.assoCntx = {}
        if ((len(assoCntx_dict) >= 3) and (type(assoCntx_dict) == dict)) == False:
            raise ValueError("Incorrect type of association context. (phenotype, compound, herb keys are necessary)")
        else:
            self.assoCntx = assoCntx_dict

    def __str__(self):
        assoCntx_str = ""
        for assoCntxKey, assoCntxValue in self.assoCntx.items():
            if type(assoCntxValue)==list:
                assoCntx_str += assoCntxKey+":"+"|".join(assoCntxValue)+"&"
            else:
                assoCntx_str += assoCntxKey + ":" + assoCntxValue + "&"
        return assoCntx_str[:-1]


class associationInSource(object):
    __slots__ = ["assoInSource"]

    def __init__(self, assoInSource_str):
        self.assoInSource = assoInSource_str

    def __str__(self):
        return self.assoInSource


class species(phenomenon, object):
    __slots__ = ["spec"]

    def __init__(self, spec_str):
        self.spec = spec_str

    def __str__(self):
        return self.spec


class reference(object):
    __slots__ = ["ref_list"]

    def __init__(self, ref_input):

        self.ref_list = []
        for ref in ref_input:
            if len(ref) == 6:
                self.ref_list.append(ref)
            else:
                raise ValueError("Incorrect number of reference attribute")

    def __str__(self):
        ref_str = ""
        for ref in self.ref_list:
            sorted_key = natural_sort(ref.keys())
            for skey in sorted_key:
                ref_str += ref[skey] + "__"
            ref_str = ref_str[:-2]
            ref_str += "&&"

        return ref_str[:-2]


class evidence(object):
    __slots__ = ["evid"]

    def __init__(self, evid_str):
        self.evid = evid_str

    def __str__(self):
        return self.evid


class codaml(phenomenon, object):
    __slots__ = ["lt", "asso", "rt", "assoCntx", "assoInSource", "spec", "ref", "evid"]

    def __init__(self, lt, asso, rt, assoCntx, assoInSource, spec, ref, evid):
        self.lt = []
        for pheno in lt:
            self.lt.append(phenomenon(pheno, "w"))
        self.rt = []
        for pheno in rt:
            self.rt.append(phenomenon(pheno, "w"))
        self.asso = association(asso)
        self.assoCntx = associationContext(assoCntx)
        self.assoInSource = associationInSource(assoInSource)
        self.spec = species(spec)
        self.ref = reference(ref)
        self.evid = evidence(evid)

        self.isValid()

    def reader(self, coda_rule):
        terms = coda_rule.strip().split("\t")
        self.lt = []
        for pheno in terms[0].split(','):
            self.lt.append(phenomenon(pheno, "r"))
        self.asso = association(terms[1])
        self.rt = []
        for pheno in terms[2].split(','):
            self.rt.append(phenomenon(pheno, "r"))

        self.assoInSource = associationInSource(terms[4])
        self.spec = species(terms[5])
        self.evid = evidence(terms[7])

        assoCntx_dic = {}
        if terms[3] != 'NA':
            for assoCntx_str in terms[3].split("&"):
                actxKey, actxValue = assoCntx_str.split(":")
                if actxKey == "Cell Line":
                    assoCntx_dic[actxKey] = actxValue
                else:
                    assoCntx_dic[actxKey] = actxValue.split("|")
        self.assoCntx = assoCntx_dic

        ref_dic_list = []
        for ref_str in terms[6].split("&&"):
            ref_list = ref_str.split("__")
            if ref_list[4].startswith('_'):
                ref_list[4] = ref_list[4][1:]
                ref_list[3] = ref_list[3]+'_'

            ref_dic = {"1.refType": ref_list[0],
                       "2.name": ref_list[1],
                       "3.description": ref_list[2],
                       "4.recordID": ref_list[3],
                       "5.version": ref_list[4],
                       "6.acqDate": ref_list[5]}

            ref_dic_list.append(ref_dic)

        self.ref = reference(ref_dic_list)

    def __str__(self):
        lt_str = ",".join([str(x) for x in self.lt])
        rt_str = ",".join([str(x) for x in self.rt])
        return "\t".join([lt_str, str(self.asso), rt_str, str(self.assoCntx), str(self.assoInSource), str(self.spec), str(self.ref), str(self.evid)])


class kuReader(codaml, object):
    __slots__ = ["lt", "asso", "rt", "assoCntx", "assoInSource", "spec", "ref", "evid"]

    def __init__(self, coda_rule):
        self.reader(coda_rule)


def format_handside(ku, hs_name, ob_hs):
    dict_entity_type = {'PH': 'Phenotype', 'BP': 'Biological_Process', 'MF': 'Molecular_Function', 'CP': 'Compound', 'GP': 'Gene'}
    hs = ET.Element(hs_name)
    ku.append(hs)

    for ob_entity in ob_hs:
        entity = ET.Element('Entity')
        hs.append(entity)

        entity_core = ET.Element('Entity_Core')
        entity.append(entity_core)

        for one_entity in ob_entity.entitycore.split('&'):
            str_entity, molecule_type, mods, variant = one_entity.split('#')
            entity_type = str_entity[:str_entity.index(':')]
            entity_name = str_entity[str_entity.index(':')+1:]
            entity_type = dict_entity_type[entity_type]

            sub_entity = ET.Element(entity_type)
            entity_core.append(sub_entity)
            append_ref_identifier(sub_entity,entity_name)

            if molecule_type != 'NA' or mods != 'NA' or variant != 'NA':
                mole_spec = ET.Element('Molecule_Specification')
                sub_entity.append(mole_spec)

                if molecule_type != 'NA':
                    ET.SubElement(mole_spec, 'Molecule_Type').text = molecule_type

                if mods != 'NA':
                    for mod in mods.split('|'):
                        ET.SubElement(mole_spec, 'Molecule_Modification').text = mod

                if variant != 'NA':
                    isoform = ET.Element('Molecule_Isoform')
                    mole_spec.append(isoform)
                    append_ref_identifier(isoform, variant)

        list_antm = [ob_entity.organ, ob_entity.tissue, ob_entity.cell]
        if any([antm_text != '' for antm_text in list_antm]):
            antm = ET.Element('Anatomical_Context')
            entity.append(antm)

            for antm_name, antm_text in zip(['Organ', 'Tissue', 'Cell'], list_antm):
                if antm_text != '':
                    antm_type=ET.Element(antm_name)
                    antm.append(antm_type)
                    append_ref_identifier(antm_type, antm_text)


def format_asso_cnxt(ku, dict_asso_cntx):
    if dict_asso_cntx != {}:
        asso_cntx = ET.Element('Environmental_Context')
        ku.append(asso_cntx)
        for key, list_value in dict_asso_cntx.items():
            condition = ET.Element('Condition')
            asso_cntx.append(condition)
            ET.SubElement(condition, 'Key').text=key
            if key=='Cell Line':
                value = ET.Element('Value')
                condition.append(value)
                append_ref_identifier(value, str(list_value))
            else:
                for v in list_value:
                    value=ET.Element('Value')
                    condition.append(value)
                    append_ref_identifier(value,v)


def format_reference(ku, list_ref):
    dict_key_conversion = {"1.refType": "Reference_Type",
                           "2.name": "Name",
                           "3.description": "Description",
                           "4.recordID": "Record_ID",
                           "5.version": "Version",
                           "6.acqDate": "Acquisition_Date"}

    for dict_ref in list_ref:
        ref = ET.Element('Reference')
        ku.append(ref)

        for key, value in sorted(dict_ref.items(), key=lambda item: int(item[0].split('.')[0])):
            if (int(key.split('.')[0]) < 7) and (value != 'NA'):
                ET.SubElement(ref, dict_key_conversion[key]).text = value


def format_score(ku, str_ev_score):
    ev_score = ET.Element('Evidence_Score')
    ku.append(ev_score)
    key, score = str_ev_score.split(':')

    item = ET.Element('Item')
    ev_score.append(item)

    ET.SubElement(item, 'Key').text = key
    ET.SubElement(item, 'Score').text = score


def append_ref_identifier(parent,Identifier):
    DB_ID=Identifier.rstrip('0123456789')
    dict_db_type = {'C': 'UMLS', 'GO:': 'GO','CIDs': 'STITCH', 'ENSG': 'Ensembl',
                    'ENSP':'Ensembl','ENST':'Ensembl','D':'MeSH', 'CLO': 'CLO'}

    if DB_ID in dict_db_type.keys():
        DBName=dict_db_type[DB_ID]
    else:
        DBName='NA'

    ref_identifer = ET.Element('Ref_Identifier')
    parent.append(ref_identifer)

    ET.SubElement(ref_identifer,'DBName').text=DBName
    ET.SubElement(ref_identifer,'Identifier').text=Identifier

def convert_to_xml(f_in_name, f_out_name):

    f_in = open(f_in_name, 'r')
    f_in.readline()

    doc = ET.Element('CODA_Knowledge_Units')

    for line in f_in:
        ku = ET.Element('CODA_Knowledge_Unit')
        doc.append(ku)

        ob_coda = kuReader(line.strip())

        format_handside(ku, 'Subject', ob_coda.lt)
        ET.SubElement(ku, 'Predicate').text = str(ob_coda.asso)
        format_handside(ku, 'Object', ob_coda.rt)
        format_asso_cnxt(ku, ob_coda.assoCntx)

        if str(ob_coda.assoInSource) != 'NA':
            ET.SubElement(ku, 'Predicate_In_Reference').text = str(ob_coda.assoInSource)
        else:
            ET.SubElement(ku, 'Predicate_In_Reference').text = 'Associate'

        spec = ET.Element('Species')
        ku.append(spec)
        append_ref_identifier(spec, str(ob_coda.spec))

        format_reference(ku, ob_coda.ref.ref_list)
        format_score(ku, ob_coda.evid.evid)

    reparsed = minidom.parseString(ET.tostring(doc))
    f_out = open(f_out_name, 'w')
    f_out.write(reparsed.toprettyxml())
    f_out.close()



if __name__ == '__main__':
    startTime = datetime.now()
    print("Start: " + str(startTime))

    convert_to_xml('codaml_converter_input2.tsv', 'codaml_examples2.xml')

    endTime = datetime.now()
    print("End: " + str(endTime))
    print("Process time: " + str(endTime - startTime))
