import xml.etree.ElementTree as ET

en_tree = ET.parse('app/src/main/res/values/strings.xml')
es_tree = ET.parse('app/src/main/res/values-es/strings.xml')

en_keys = {elem.attrib['name'] for elem in en_tree.getroot() if elem.tag == 'string'}
es_keys = {elem.attrib['name'] for elem in es_tree.getroot() if elem.tag == 'string'}

missing_in_es = en_keys - es_keys
missing_in_en = es_keys - en_keys

if missing_in_es:
    print(f"Missing in Spanish: {missing_in_es}")
if missing_in_en:
    print(f"Missing in English: {missing_in_en}")
if not missing_in_es and not missing_in_en:
    print("All string keys match between English and Spanish!")
