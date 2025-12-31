package settlabs;

import util.database.SQLiteDB;
import util.tools.TimeTools;

import java.util.ArrayList;

public class Lcsc {

    public static ArrayList<String[]> processPrices(String sku, String prices){
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
    public static String getSearchPage( String sku ){
        return"https://www.lcsc.com/search?q="+ sku;
    }
    public static String getProductPage( String sku ){
        return "https://www.lcsc.com/product-detail/" + sku + ".html";
    }
    public static String getSkuRegex(){
        return "C\\d{4,}[-]?\\d*";
    }
    private static void fillIn(SQLiteDB db) {
        var link = "https://www.lcsc.com/search?q=";
        //fillInSKU(db, link,"lcsc",getSkuRegex());
    }
}
