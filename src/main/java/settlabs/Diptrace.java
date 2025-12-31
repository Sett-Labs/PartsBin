package settlabs;

import util.database.SQLiteDB;
import util.xml.XMLdigger;
import util.xml.XmlMiniFab;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;


public class Diptrace {

    public static void expand(String xmlFile) {
        var path = Paths.get(xmlFile);
        if (Files.exists(path)) {
            // Build the dataset
            var dig = XMLdigger.goIn(path, "Library", "Components");
            var lite = SQLiteDB.createDB("data", Path.of("components.db"));
            for (var comp : dig.digOut("Component")) {
                var map = new HashMap<String, String>();

                comp.digDown("Part");
                map.put("Name", comp.peekAt("Name").value(""));
                map.put("Value", comp.peekAt("Value").value(""));
                map.put("Manufacturer", comp.peekAt("Manufacturer").value(""));
                map.put("Datasheet", comp.peekAt("Datasheet").value(""));
                comp.digDown("AddFields");

                addFieldsToMap(comp, map);
                Decoding.decodeMPN(map);
                saveOrUpdate(lite,map);
            }

        }
    }
    public static void saveOrUpdate(SQLiteDB lite, HashMap<String, String> map) {
        var ref = XMLdigger.goIn(Path.of("ref.xml"), "Capacitors", "database");
        var cols = ref.peekAt("columns").value("").toLowerCase();
        var colNames = cols.split(",");

        String sql = "INSERT OR REPLACE INTO capacitors(" + cols + ", enriched) VALUES (";
        sql += Arrays.stream(colNames).map(col -> {
            col = col.replace("_at_", " @ ");
            col = col.replace("_", " ");
            var field = map.get(toUpperFirstLetter(col));
            if (field != null)
                return "'" + field + "'";
            return "''";
        }).collect(Collectors.joining(","));
        sql += ",false);";

        lite.doInsert(sql);
    }
    public static String toUpperFirstLetter(String name) {
        name = name.toLowerCase();
        name = name.substring(0, 1).toUpperCase() + name.substring(1);
        return name;
    }
    public static void addFieldsToMap(XMLdigger dig, HashMap<String, String> map) {
        for (var field : dig.digOut("AddField")) {
            var name = field.peekAt("Name").value("");
            var value = field.peekAt("Text").value("");

            map.put(toUpperFirstLetter(name), value);
        }
    }

    public static String listFields(String filename) {
        var path = Paths.get(filename);

        if (Files.exists(path)) {
            Set<String> params = new HashSet<>();

            // First build a list of fields
            var dig = XMLdigger.goIn(path, "Library", "Components");
            for (var comp : dig.digOut("Component")) {
                comp.digDown("Part", "AddFields");
                for (var field : comp.digOut("AddField")) {
                    params.add(field.peekAt("Name").value(""));
                }
            }
            return String.join(";", params);
        }
        return "No fields found";
    }
    public static void updateFields(String xml) {
        var path = Paths.get(xml);

        if (Files.exists(path)) {
            Set<String> params = new HashSet<>();

            // First build a list of fields
            var dig = XMLdigger.goIn(path, "Library", "Components");
            for (var comp : dig.digOut("Component")) {
                comp.digDown("Part", "AddFields");
                var found = new ArrayList<String>();
                var mpn = "";
                for (var field : comp.digOut("AddField")) {
                    var fieldName = field.peekAt("Name").value("");
                    if( fieldName.equals("Manufacturer name") ){
                        mpn = field.peekAt("Text").value("");
                        System.out.println("Found: "+mpn);
                    }
                    found.add(fieldName);
                }
                // At this point we got the names of all the fields
                var map = retrieveCapFields(mpn);
                for( var exists : found ){
                    map.remove(exists);
                }
                // Add the rest?
                addFieldNode( comp.useEditor(), map );
            }
        }
    }
    public static void addFieldNode(XmlMiniFab fab, HashMap<String,String> map ){
        if(map.isEmpty())
            return;
        fab.goUp();
        for( var set : map.entrySet()) {
            fab.addAndAlterChild("AddField").attr("Type", "Text");
            fab.addChild("Name",set.getKey() );
            fab.addChild("Text",set.getValue());
            fab.goUp();
        }
        fab.build();
    }
    public static HashMap<String, String> retrieveCapFields(String mpn ){
        var lite = SQLiteDB.createDB("lite",Path.of("components.db"));
        var res = lite.doSelect("SELECT * FROM Capacitors WHERE Manufacturer_name == '"+mpn+"';",true);
        if( res.isEmpty() )
            return new HashMap<>();
        var data = res.get();
        if( data.size() < 2 ){
            System.out.println("Not enough data found for "+mpn);
            return new HashMap<>();
        }
        var cols = data.get(0);
        var vals = data.get(1);

        var map = new HashMap<String,String>();
        for(  int a=0;a<cols.size();a++ ){
            var val = String.valueOf(vals.get(a));
            if( !val.isEmpty() )
                map.put( fromColumnToField(cols.get(a)),val);
        }
        map.remove("Value");
        map.remove("Name");
        map.remove("Manufacturer");
        map.remove("Id");
        return map;
    }
    private static String fromColumnToField( Object column ){
        var field = String.valueOf(column);
        field = toUpperFirstLetter(field);
        if( field.equalsIgnoreCase("LCSC") )
            field="LCSC";
        field=field.replace("_"," ");
        return field.replace(" at "," @ ");
    }
}
