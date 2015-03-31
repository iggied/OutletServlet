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
    private Database database;
    private String outletId;

    @Override
    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);
        androidContext = (android.content.Context)config.getServletContext().getAttribute("org.mortbay.ijetty.context");
        database = (Database) getServletContext().getAttribute("database");
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
            case "GETORDER": {
                String tableArea = parameterMap != null ? parameterMap.get("tableArea")[0] : "" ;
                String tableNumber = parameterMap != null ? parameterMap.get("tableNumber")[0] : "" ;
                getOrderResponse(writer, tableArea, tableNumber);
                break;
            }
        }

    }



    private void validateCredsResponse(PrintWriter out, String staffId, String staffPin) throws IOException {
        Map<String, String> replyMap = new HashMap<>();
        replyMap.put("valid", "0");

        ArrayList keyArray = new ArrayList();
        //keyArray.add(outletId);
        //keyArray.add(staffId);
        keyArray.add(Arrays.asList(outletId, staffId, staffPin));


        Query query = database.getView("ddoc/staffview").createQuery();
        //query.setStartKey(Arrays.asList(outletId, staffId) );
        //query.setEndKey(Arrays.asList(outletId, staffId));
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

        Query query = database.getView("tableview").createQuery();
        query.setStartKey(Arrays.asList(outletId));
        query.setEndKey(Arrays.asList(outletId, "ZZ"));
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
            Log.i(TAG, "document id=" + doc.getId() + " outletid=" + doc.getProperty("outletId"));
        }

    }

    private void getMenuResponse(PrintWriter out) throws IOException {

        Query query = database.getView("menuview").createQuery();
        query.setStartKey(Arrays.asList(outletId) );
        query.setEndKey(Arrays.asList(outletId, "zzz"));
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
            out.println( mapper.writeValueAsString(doc.getProperty("data")));
            Log.i(TAG, "document id=" + doc.getId() + " outletid=" + doc.getProperty("outletId"));
        }

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
        Document doc = database.createDocument();
        try {
            ret = doc.putProperties(orderMap);
        } catch (CouchbaseLiteException e) {
            Log.e(TAG, "Error creating order document", e);
        }

        out.println(mapper.writeValueAsString(ret.getProperties() ));

    }


    private void getOrderResponse(PrintWriter out, String tableArea, String tableNumber) throws IOException {

        Query query = database.getView("pendingorderview").createQuery();
        query.setStartKey(Arrays.asList(outletId, tableArea, tableNumber));
        query.setEndKey(Arrays.asList(outletId, tableArea, tableNumber, "zzzzz"));
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

            if (row.getValue().equals("placed")) {
                doc = row.getDocument();
                order = (Map<String, Object>) doc.getProperty("order");

                orders = (ArrayList) order.get("data");
                Map<String, Object> itemMap ;
                for (Iterator i = orders.iterator(); i.hasNext();) {
                    itemMap = (Map<String, Object>) i.next();

                    retOrders.add(itemMap);
                }
            }

        }

        ObjectMapper mapper = new ObjectMapper();
        out.println(mapper.writeValueAsString(retOrders));

    }
}
