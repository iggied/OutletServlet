package com.iggie.managerdeviceapp;

import com.couchbase.lite.*;
import com.couchbase.lite.util.Log;
import org.codehaus.jackson.map.ObjectMapper;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.*;


public class OutletServlet extends HttpServlet
{
    final String TAG = this.getClass().getName();

    private android.content.Context androidContext;
    private Database outlet_database;
    private Database order_database;
    private String outletId;

    @Override
    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);
        androidContext = (android.content.Context)config.getServletContext().getAttribute("org.mortbay.ijetty.context");
        outlet_database = (Database) getServletContext().getAttribute("outlet_database");
        order_database = (Database) getServletContext().getAttribute("order_database");
        outletId = getServletContext().getInitParameter("outletId");
        Log.d(TAG, "Servlet init completed");
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        doGet(request, response);
    }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        response.setContentType("text/json");
        response.setStatus(HttpServletResponse.SC_OK);
        PrintWriter writer = response.getWriter();
        doJSON(writer, request, response);
        writer.flush();

    }


    protected void doJSON (PrintWriter writer, HttpServletRequest request,
                           HttpServletResponse response) throws IOException {


        Map<String,String[]> parameterMap = (Map<String, String[]>) request.getParameterMap();

        String action = parameterMap != null ? parameterMap.get("action")[0] : "" ;

        response.setContentType("application/json");

        switch (action) {
            case "VALIDATECREDS": {
                String staffId = parameterMap != null ? parameterMap.get("staffId")[0] : "" ;
                String staffPin = parameterMap != null ? parameterMap.get("staffPin")[0] : "" ;

                validateCredsResponse(writer, staffId, staffPin);
                break;
            }
            case "GETTABLES": {
                getTablesResponse(writer);
                break;
            }
            case "GETMENU": {
                getMenuResponse(writer);
                break;
            }
            case "PLACEORDER": {
                placeOrderResponse(writer, request.getInputStream());
                break;
            }
            case "GETPENDINGORDERBYTABLE": {
                String tableArea = parameterMap != null ? parameterMap.get("tableArea")[0] : "" ;
                String tableNumber = parameterMap != null ? parameterMap.get("tableNumber")[0] : "" ;
                getPendingOrderByTableResponse(writer, tableArea, tableNumber, "placed");
                break;
            }
            case "GETPENDINGORDERS": {
                getPendingOrdersResponse(writer, "placed");
                break;
            }
            case "GETORDERBYID": {
                String id = parameterMap != null ? parameterMap.get("id")[0] : "" ;
                getOrderByIdResponse(writer, id);
                break;
            }
            case "CONFIRMORDER": {
                String id = parameterMap != null ? parameterMap.get("id")[0] : "" ;
                confirmOrderResponse(writer, id);
                break;
            }

        }

    }



    private void validateCredsResponse(PrintWriter out, String staffId, String staffPin) throws IOException {
        Map<String, String> replyMap = new HashMap<>();
        replyMap.put("valid", "0");

        ArrayList keyArray = new ArrayList();
        keyArray.add(Arrays.asList(staffId, staffPin));

        Query query = outlet_database.getView("ddoc/staffview").createQuery();
        query.setKeys(keyArray);
        QueryEnumerator result = null;
        try {
            result = query.run();
        } catch (CouchbaseLiteException e) {
            Log.e(TAG, "Error running query", e);
        }

        if (result.hasNext()) {
            Log.i(TAG, "document found = " + result.next().getKey());
                replyMap.put("valid", "1");
        }

        out.println(new ObjectMapper().writeValueAsString(replyMap));
    }

    private void getTablesResponse(PrintWriter out) throws IOException {

        Query query = outlet_database.getView("tableview").createQuery();
        //query.setStartKey(Arrays.asList(outletId));
        //query.setEndKey(Arrays.asList(outletId, "zzz"));
        QueryEnumerator result = null;
        try {
            result = query.run();
        } catch (CouchbaseLiteException e) {
            Log.e(TAG, "Error running query", e);
        }

        if (result.hasNext()) {
            //Map<String, Object> resultMap = (Map) result.next().getValue();
            Document doc = result.next().getDocument();
            ObjectMapper mapper = new ObjectMapper();
            out.println(mapper.writeValueAsString(doc.getProperty("data")));
        }

    }

/*    private void getMenuResponse(PrintWriter out) throws IOException {

        Query query = outlet_database.getView("menuview").createQuery();
        QueryEnumerator result = null;
        try {
            result = query.run();
        } catch (CouchbaseLiteException e) {
            Log.e(TAG, "Error running query", e);
        }

        if (result.hasNext()) {
            Document doc = result.next().getDocument();
            ObjectMapper mapper = new ObjectMapper();
            out.println( mapper.writeValueAsString(doc.getProperty("data")));
        }

    }*/

    private void getMenuResponse(PrintWriter out) throws IOException {

        Query query = outlet_database.getView("menuview").createQuery();
        QueryEnumerator result = null;
        try {
            result = query.run();
        } catch (CouchbaseLiteException e) {
            Log.e(TAG, "Error running query", e);
        }

        ArrayList resultMenu = new ArrayList();
        for (Iterator<QueryRow> it = result; it.hasNext(); ) {
            QueryRow row = it.next();

            Document doc = row.getDocument();
            resultMenu.add(doc.getProperties());
        }

        ObjectMapper mapper = new ObjectMapper();
        out.println( mapper.writeValueAsString(resultMenu));
    }


    private void placeOrderResponse(PrintWriter out, InputStream is) throws IOException {

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> orderMap;

        orderMap = mapper.readValue(is, Map.class);
        orderMap.put("type", "order");
        orderMap.put("outletId", outletId);
        orderMap.put("status", "placed");
        orderMap.put("created_on", new java.util.Date().getTime());

        SavedRevision ret = null;
        Document doc = order_database.createDocument();
        try {
            ret = doc.putProperties(orderMap);
        } catch (CouchbaseLiteException e) {
            Log.e(TAG, "Error creating order document", e);
        }

        out.println(mapper.writeValueAsString(ret.getProperties() ));

    }


    private void getPendingOrderByTableResponse(PrintWriter out, String tableArea, String tableNumber, String status) throws IOException {

        Query query = order_database.getView("orderview").createQuery();
        query.setStartKey(Arrays.asList(tableNumber, status));
        query.setEndKey(Arrays.asList(tableNumber, status, "zzz"));
        QueryEnumerator result = null;
        try {
            result = query.run();
        } catch (CouchbaseLiteException e) {
            Log.e(TAG, "Error running query", e);
        }

        Document doc;
        Map<String, Object> order ;
        ArrayList orders = null ;
        ArrayList retOrders = new ArrayList();
        for (Iterator<QueryRow> it = result; it.hasNext(); ) {
            QueryRow row = it.next();

            //if (row.getValue().equals("placed")) {
                doc = row.getDocument();
                order = (Map<String, Object>) doc.getProperty("order");

                orders = (ArrayList) order.get("data");
                Map<String, Object> itemMap ;
                for (Iterator i = orders.iterator(); i.hasNext();) {
                    itemMap = (Map<String, Object>) i.next();

                    retOrders.add(itemMap);
                }
            //}

        }

        ObjectMapper mapper = new ObjectMapper();
        out.println(mapper.writeValueAsString(retOrders));
    }


    private void getOrderByIdResponse(PrintWriter out, String id) throws IOException {

        Document doc = order_database.getDocument(id);

        ObjectMapper mapper = new ObjectMapper();
        out.println(mapper.writeValueAsString(doc.getUserProperties()));
    }

    private void confirmOrderResponse(PrintWriter out, String id) throws IOException {

        Document doc = order_database.getDocument(id);

        Map<String, Object> properties = new HashMap<String, Object>();

        properties.putAll(doc.getProperties());
        properties.put("status", "confirmed");
        properties.put("updated_on", new java.util.Date().getTime());
        
        try {
            doc.putProperties(properties);
        } catch (CouchbaseLiteException e) {
            Log.e(TAG, "Error confirming order ", e);
        }

        out.println("{\"status\": \"ok\"}");
    }


    private void getPendingOrdersResponse(PrintWriter out, String status) throws IOException {

       Query query = order_database.getView("orderbystatusview").createQuery();
        query.setStartKey(Arrays.asList(status));
        query.setEndKey(Arrays.asList(status, "zzz"));
        QueryEnumerator result = null;
        try {
            result = query.run();
        } catch (CouchbaseLiteException e) {
            Log.e(TAG, "Error running query", e);
        }

        Document doc;
        Map<String, Object> order ;
        ArrayList orders = new ArrayList() ;
        //ArrayList retOrders = new ArrayList();
        for (Iterator<QueryRow> it = result; it.hasNext(); ) {
            QueryRow row = it.next();

            doc = row.getDocument();
            orders.add(doc.getProperties());
/*            order = (Map<String, Object>) doc.getProperty("order");

            orders = (ArrayList) order.get("data");
            Map<String, Object> itemMap ;
            for (Iterator i = orders.iterator(); i.hasNext();) {
                itemMap = (Map<String, Object>) i.next();

                retOrders.add(itemMap);
            }
*/        }

        ObjectMapper mapper = new ObjectMapper();
        out.println(mapper.writeValueAsString(orders));

    }

}
