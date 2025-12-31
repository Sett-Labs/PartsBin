package settlabs;

import util.xml.XMLdigger;

import java.nio.file.Path;
import java.util.HashMap;

public class Decoding {
    static String[] unit = {"pF", "nF", "uF", "mF"};

    public static void decodeMPN(HashMap<String, String> fields) {
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
    private static int from(XMLdigger dig) {
        return dig.peekAt("range").attr("from", -1);
    }
    private static int to(XMLdigger dig) {
        return dig.peekAt("range").attr("to", -1);
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
}
