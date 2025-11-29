package settlabs;

import util.PartNumberWorkflow;
import util.database.SQLiteDB;
import util.database.SqlTable;
import util.tools.FileTools;
import util.tools.TimeTools;
import util.xml.XMLdigger;
import util.xml.XmlMiniFab;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;


//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class ReStock {

    static String[] unit = {"pF", "nF", "uF", "mF"};

    private static void decodeMPN(HashMap<String, String> fields) {
        var ref = XMLdigger.goIn(Path.of("ref.xml"), "Capacitors", fields.get("Manufacturer"));

        if (ref.isValid()) { // Meaning we found the manufacturer
            var partNumber = fields.get("Manufacturer name");
            if (partNumber.isEmpty()) {
                System.err.println("Manufacturer name is empty.");
                return;
            }
            fields.put("Series", convert(ref, partNumber, "Series"));
            if (fields.get("Series").equals("??"))
                return;
            fields.put("Size", convert(ref, partNumber, "Size"));
            fields.put("Dielectric", convert(ref, partNumber, "Dielectric"));
            fields.put("Height", convert(ref, partNumber, "Height"));
            fields.put("Voltage rating", convert(ref, partNumber, "Voltage"));
            fields.put("Tolerance", convert(ref, partNumber, "Tolerance"));

            if (ref.hasPeek("ProductPage")) {
                ref.digDown("Datasheet");
                var link = ref.peekAt("link").value("");
                var crop = ref.peekAt("crop").value(0);
                var cropped = partNumber.substring(0, partNumber.length() - crop);
                fields.put("Product page", link.replace("(MPN)", cropped));
            }

            var cap = extractCode(ref, partNumber, "Capacitance");
            fields.put("Capacitance", parseCap(cap));
            ref.goUp();
        }
    }
    private static String convert(XMLdigger dig, String MPN, String type) {
        if (!dig.hasPeek(type))
            return "";

        String code = extractCode(dig, MPN, type);// GRM
        dig.digDown(type);
        if (code.isEmpty()) {
            System.out.println("Not valid code in node for " + type);
            return "??";
        }
        var res = dig.peekAt("code", "from", code).attr("to", "??");
        if (res.equals("??") && !type.equalsIgnoreCase("capacitance")) {
            System.out.println("Not found: " + code + " for " + type);
        }
        dig.goUp();
        return res;
    }
    private static String extractCode(XMLdigger dig, String mpn, String type) {
        if (!dig.hasPeek(type))
            return "";
        dig.digDown(type);
        var from = from(dig);
        String result = "";
        if (from != -1) {
            result = mpn.substring(from, to(dig));
        }
        dig.goUp();
        return result;
    }
    private static int from(XMLdigger dig) {
        return dig.peekAt("range").attr("from", -1);
    }
    private static int to(XMLdigger dig) {
        return dig.peekAt("range").attr("to", -1);
    }
    public static String parseCap(String code) {

        if (code.length() < 3)
            return code;

        if (code.contains("R")) {
            if (code.startsWith("R"))
                code = code.replace("R", "0.");
            code = code.replace("R", ".");
            return code + "pF";
        }
        int msb = Integer.parseInt(code.substring(0, 2));
        int zeros = Integer.parseInt(code.substring(2, 3));

        int unitSkip = zeros / 3;
        var cap = msb * Math.pow(10, zeros % 3);

        if (cap >= 1000) {
            cap /= 1000;
            unitSkip++;
        }
        var capString = String.valueOf(cap).replace(".0", "");
        return capString + unit[unitSkip];
    }

    public static void expand(String xmlFile) {
        var path = Paths.get(xmlFile);
        if (Files.exists(path)) {
            // Build the dataset
            var dig = XMLdigger.goIn(path, "Library", "Components");
            var lite = SQLiteDB.createDB("data",Path.of("components.db"));
            for (var comp : dig.digOut("Component")) {
                var map = new HashMap<String, String>();

                comp.digDown("Part");
                map.put("Name", comp.peekAt("Name").value(""));
                map.put("Value", comp.peekAt("Value").value(""));
                map.put("Manufacturer", comp.peekAt("Manufacturer").value(""));
                map.put("Datasheet", comp.peekAt("Datasheet").value(""));
                comp.digDown("AddFields");

                addFieldsToMap(comp, map);
                decodeMPN(map);
                saveOrUpdate(lite,map);
            }

        }
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
    public static void fillInLCSC(String table) {
        var link = "https://www.lcsc.com/search?q=";
        fillInSKU(table,link,"lcsc","C\\d{4,}[-]?\\d*");
    }
    public static void fillInMouser(String table) {
        var link = "https://www.mouser.be/c/?q=";
        fillInSKU(table,link,"mouser","^\\d{2,4}-[A-Z0-9./-]{3,}$");
    }
    public static void fillInSKU(String table, String link, String supplier, String regex) {
        System.out.println("Looking for SKU's for "+supplier);
        var db = SQLiteDB.createDB("comps", Path.of("components.db"));

        db.connect(false);
        db.addRegexFunction();  // Enable option to use regexp

        if (!db.isValid(1000)) { // Verify connection
            System.err.println("No valid database connection");
            return;
        }
        // Do Query and verify there's a result
        var selectSQL = "SELECT id, manufacturer_name, "+supplier+" FROM "+table+" WHERE "+supplier+" != 'None' AND REGEXP('"+regex+"', "+supplier+") = 0";
        var res = db.doSelect(selectSQL);
        if( res.isEmpty() ) {
            System.err.println("Query failed");
            return;
        }
        var data = res.get();
        if( data.isEmpty() ) {
            System.out.println("No query results");
            return;
        }

        // Start processing
        PartNumberWorkflow workflow = new PartNumberWorkflow();
        var list = new ArrayList<String>();

        for( int a =0;a<data.size();a++ ){
            var l = data.get(a);
            System.out.println("\n--- Processing Part " + (a+1) + " of " + data.size() + " ---");
            var mpn = String.valueOf(l.get(1));

            // Execute the copy-wait-read cycle
            var newClipboardContent = workflow.executeCycle(link + mpn);
            if (newClipboardContent == null  ){
                System.out.println("Cycle failed or clipboard content was not text, trying again.");
                a--;
                continue;
            }

            System.out.println("Final Result (New Content): " + newClipboardContent);
            if( newClipboardContent.matches(regex) ){ // Needs to match regex
                System.out.println("Stored...");
                list.add("UPDATE "+table+" SET "+supplier+" = '" + newClipboardContent.trim() + "' WHERE manufacturer_name ='" + mpn + "';");
            }else if( newClipboardContent.equals("stop") ){ // Stop command chosen
                System.out.println("Stopping...");
                break;
            }else if( newClipboardContent.length()<=3 ){ // Skip command chosen
                System.out.println("Skipping...");
                list.add("UPDATE "+table+" SET "+supplier+" = 'None' WHERE manufacturer_name ='" + mpn + "';");
            }else{
                System.out.println("Failed Regex: "+newClipboardContent );
            }
            // Intermediate dump
            if( list.size()>10 ){
                db.doBatchRun(list);
                list.clear();
            }
        }
        db.doBatchRun(list);
        System.out.println("\nAll part numbers processed.");
    }
    public static void processBom( String bomFileName ){
        var lines = new ArrayList<String>();
        FileTools.readTxtFile(lines,Path.of(bomFileName));

        if( lines.isEmpty())
            return;

        //Convert to array?
        var colTitles = new ArrayList<>(List.of(lines.removeFirst().replace("\"","").split(";")));
        int mpnIndex = colTitles.indexOf("Manufacturer name");
        int mouserIndex = colTitles.indexOf("mouser");
        int lcscIndex = colTitles.indexOf("LCSC");
        int qIndex = colTitles.indexOf("Quantity");

        var name = bomFileName.substring(0,bomFileName.indexOf("."));
        var db = SQLiteDB.createDB("comps", Path.of("components.db"));
        db.connect(false);

        // Process bom
        for( var bomLine : lines ){
            var cols = bomLine.replace("\"","").split(",");
            var mpn = cols[mpnIndex];
            if( mpn.isEmpty() )
                continue;

            var global = new String[]{mpn,mouserIndex>=cols.length?"":cols[mouserIndex],lcscIndex>=cols.length?"":cols[lcscIndex]};
            addToGlobal(db,mpn,global );

            // Clear previous data

            //Insert new data
            var items = new String[]{name, mpn, qIndex >= cols.length ? "0" : cols[qIndex]};
            var insert ="INSERT into bom (boardname,manufacturer_name,quantity) VALUES (?,?,?);";
            if( !db.doPreparedInsert( insert,items ) )
                System.err.println("Insert failed for "+mpn+ " -> "+insert);
        }
        db.finish();
    }

    private static boolean addToGlobal(SQLiteDB lite, String mpn, String[] data ){
        var opt = lite.doSelect( "SELECT * FROM global WHERE manufacturer_name='"+mpn+"';");
        if( opt.isEmpty() )// query failed
            return false;
        var resSet = opt.get();

        if( resSet.isEmpty() ) {// Not in global yet, so add it
            var insert = "INSERT INTO global (manufacturer_name,mouser,lcsc) VALUES (?,?,?);";
            if( !lite.doPreparedInsert( insert,data ) )
                System.err.println("Insert failed for "+mpn+ " -> "+insert);
        }else{
            System.out.println("Found "+mpn);
        }
        return true;
    }
    private static void fillGlobal( ){
        fillInLCSC("global");
        fillInMouser("global");
    }
    private static void fillInLcscTable(){
        var db = SQLiteDB.createDB("comps", Path.of("components.db"));
        db.connect(false);

        // Get lcsl from global that isn't in lcsc
        var query = """
                SELECT g.lcsc_prices, g.manufacturer_name
                FROM global g
                LEFT JOIN lcsc_prices l ON g.lcsc = l.sku
                WHERE l.sku IS NULL AND g.lcsc != 'None';
                """;
        var res = db.doSelect(query);
        if( res.isEmpty() )
            return;
        var rs = res.get();
        // Now iterate over and add
        PartNumberWorkflow workflow = new PartNumberWorkflow();
        var list = new ArrayList<String[]>();
        var insert = "INSERT INTO lcsc_prices (sku,moq,price,timestamp) VALUES (?,?,?,?);";

        for( int a=0;a<rs.size();a++ ){
            var row = rs.get(a);
            var lcsc = String.valueOf(row.getFirst());
            var link = "https://www.lcsc.com/product-detail/"+lcsc+".html";
            var ncb = workflow.executeCycle(link);

            if (ncb == null  ){
                System.out.println("Cycle failed or clipboard content was not text, trying again.");
                a--;
                continue;
            }
            System.out.println("Final Result (New Content): " + ncb);
            if( ncb.equals("stop") ){ // Stop command chosen
                System.out.println("Stopping...");
                break;
            }else if( ncb.length()<=3 ){ // Skip command chosen
                System.out.println("Skipping...");
            }else{
                System.out.println("Stored...");
                list.addAll(processLcscPrices(lcsc,ncb));
            }
            // Intermediate dump
            if( list.size()>2 ){
                while( !list.isEmpty() ) {
                    if( !db.doPreparedInsert(insert, list.removeFirst(), false))
                        return;
                }
                db.commit();
            }
        }
        if( list.size()>10 ){
            while( !list.isEmpty() )
                db.doPreparedInsert(insert,list.removeFirst(),false);
            db.commit();
        }
    }
    private static ArrayList<String[]> processLcscPrices(String sku, String prices){
        // Cleanup
        prices = prices.replace("\n\t","\t");
        prices = prices.replace("\n",";");
        prices = prices.replace("\t","/");
        prices = prices.replaceAll("[\\s+€,]+", ""); // Remove whitespace
        // Split per moq line
        var split = prices.split(";");
        var prep = new ArrayList<String[]>();
        for( var moq : split ){
            var all = moq.split("/");
            prep.add( new String[]{sku,all[0],all[1], TimeTools.formatShortUTCNow()}); // Don't need multiplication
        }
        return prep;
    }
    public static void main(String[] args) {
        //TIP Press <shortcut actionId="ShowIntentionActions"/> with your caret at the highlighted text
        // to see how IntelliJ IDEA suggests fixing it.

        // expand("caps.elixml" );
       // fillInLCSC();
       // fillInMouser();
       // updateFields( "caps.elixml");
        //processBom("burrowv3.csv");
        //fillGlobal();
        fillInLcscTable();
    }
}