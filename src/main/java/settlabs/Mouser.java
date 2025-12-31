package settlabs;

import util.database.SQLiteDB;
import util.tools.TimeTools;

import java.util.ArrayList;
import java.util.Map;

public class Mouser {

    public static String getSearchPage( String sku ){
        return "https://www.mouser.be/c/?q="+ sku;
    }
    public static String getProductPage( String sku ){
        return getSearchPage(sku);
    }
    public static String getSkuRegex(){
        return "^\\d{2,3}-[A-Za-z0-9\\-./]{2,}$";
    }
    public static ArrayList<String[]> processPrices(String sku, Map<String,String> prices){
        var prep = new ArrayList<String[]>();
        for( var set : prices.entrySet() ){
            var price = set.getValue().replace("\n\t","\t");
            price = price.replace("\n","");
            price = price.replaceAll("[\\s+€,]+", ""); // Remove whitespace
            // Split per moq line
            if( !price.isEmpty())
                prep.add( new String[]{sku, set.getKey(),price, TimeTools.formatShortUTCNow()}); // Don't need multiplication
        }
        return prep;
    }
}
