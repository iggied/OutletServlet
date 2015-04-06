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
            case "PLACEORDERPART": {
                placeOrderPartResponse(writer, request.getInputStream());
                break;
            }
            case "GETPENDINGORDERBYTABLE": {
                //String order_id = parameterMap != null ? parameterMap.get("order_id")[0] : "" ;
                String staffId = parameterMap != null ? parameterMap.get("staffId")[0] : "" ;
                String tableNumber = parameterMap != null ? parameterMap.get("tableNumber")[0] : "" ;
                getPendingOrderByTableResponse(writer, staffId, tableNumber);
                break;
            }
            case "GETPENDINGORDERS": {
                getPendingOrdersResponse(writer);
                break;
            }
            case "GETORDERBYID": {
                String id = parameterMap != null ? parameterMap.get("id")[0] : "" ;
                getOrderByIdResponse(writer, id);
                break;
            }
            case "CONFIRMORDERPART": {
                String id = parameterMap != null ? parameterMap.get("id")[0] : "" ;
                updateOrderStatusResponse(writer, id, "confirm", "0");
                break;
            }
            case "ORDERPARTDELIVERED": {
                String id = parameterMap != null ? parameterMap.get("id")[0] : "" ;
                updateOrderStatusResponse(writer, id, "delivered", "0");
                break;
            }
            case "ORDERPAID": {
                String order_id = parameterMap != null ? parameterMap.get("order_id")[0] : "" ;
                float amount = Float.parseFloat(parameterMap != null ? parameterMap.get("amount")[0] : "0") ;
                orderPaidResponse(writer, order_id, amount);
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

        Query query = outlet_database.getView("ddoc/tableview").createQuery();
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


    private void getMenuResponse(PrintWriter out) throws IOException {

        Query query = outlet_database.getView("ddoc/menuview").createQuery();
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


    private void placeOrderPartResponse(PrintWriter out, InputStream is) throws IOException {

        ObjectMapper mapper = new ObjectMapper();
        final Map<String, Object> orderMap;

        orderMap = mapper.readValue(is, Map.class);
        orderMap.put("type", "orderpart");
        orderMap.put("outletId", outletId);
        orderMap.put("status", "placed");
        orderMap.put("created_on", new java.util.Date().getTime());

        SavedRevision ret = null;

        boolean committed =
        order_database.runInTransaction(new TransactionalTask() {

            public boolean run() {

                Document doc = order_database.createDocument();
                try {
                    doc.putProperties(orderMap);
                } catch (CouchbaseLiteException e) {
                    Log.e(TAG, "Error creating order part document", e);
                    return false;
                }


                String orderpart_id = doc.getId();
                Map<String, Object> order = (Map) orderMap.get("order");
                String tableNumber = (String) order.get("tableNumber");
                String order_id = (String) order.get("order_id");
                Map<String, Object> statusMap = new HashMap();
                statusMap.put("type", "orderpartstatus");
                statusMap.put("updated_on", new java.util.Date().getTime());
                statusMap.put("tableNumber", tableNumber);
                statusMap.put("orderpart_id", orderpart_id);
                statusMap.put("order_id", order_id);
                statusMap.put("closed", "0");
                statusMap.put("status", "placed");

                doc = order_database.createDocument();
                try {
                    doc.putProperties(statusMap);
                } catch (CouchbaseLiteException e) {
                    Log.e(TAG, "Error creating order status document", e);
                    return false;
                }

                return true;
            }
        });

        //out.println(mapper.writeValueAsString(ret.getProperties() ));

        out.println(mapper.writeValueAsString(orderMap));
    }


    private void getPendingOrderByTableResponse(PrintWriter out, String staffId, String tableNumber) throws IOException {

        Query query = order_database.getView("ddoc/ordertablestatusview").createQuery();
        query.setStartKey(Arrays.asList(tableNumber, "0"));
        query.setEndKey(Arrays.asList(tableNumber, "0", "zzz"));
        QueryEnumerator result = null;
        try {
            result = query.run();
        } catch (CouchbaseLiteException e) {
            Log.e(TAG, "Error running query", e);
        }

        Document doc;
        Map<String, Object> order = new HashMap();
        Map<String, Object> resultMap = new HashMap();
        ArrayList orderitems = new ArrayList();
        ArrayList retOrders = new ArrayList();
        String o_id = null;

        Iterator<QueryRow> it = result;
        if (it.hasNext()) {
            for (; it.hasNext() ;) {
                QueryRow row = it.next();

                String orderpart_id = (String) row.getValue();

                doc = order_database.getDocument(orderpart_id);

                order = (Map<String, Object>) doc.getProperty("order");
                o_id = (String) order.get("order_id");

                orderitems = (ArrayList) order.get("data");
                Map<String, Object> itemMap ;
                for (Iterator i = orderitems.iterator(); i.hasNext();) {
                    itemMap = (Map<String, Object>) i.next();

                    retOrders.add(itemMap);
                }
            }
        } else {
            o_id = createInitOrder(tableNumber);
        }

        order = new HashMap();
        order.put("order_id", o_id);
        order.put("staffId", staffId);
        order.put("tableNumber", tableNumber);
        order.put("data", retOrders);     

        resultMap.put("order", order);

        ObjectMapper mapper = new ObjectMapper();
        out.println(mapper.writeValueAsString(resultMap));
    }

    private String createInitOrder(String tableNumber){
        Map<String, Object> orderMap = new HashMap();
        orderMap.put("type", "order");
        orderMap.put("outletId", outletId);
        orderMap.put("tableNumber", tableNumber);
        orderMap.put("status", "init");
        orderMap.put("created_on", new java.util.Date().getTime());

        SavedRevision rev = null;
        Document doc = order_database.createDocument();

        try {
            rev = doc.putProperties(orderMap);
        } catch (CouchbaseLiteException e) {
            Log.e(TAG, "Error creating order document", e);
        }

        return (String) rev.getProperty("_id");
    }

    private void getOrderByIdResponse(PrintWriter out, String id) throws IOException {

        Document doc = order_database.getDocument(id);

        ObjectMapper mapper = new ObjectMapper();
        out.println(mapper.writeValueAsString(doc.getUserProperties()));
    }

    private void updateOrderStatusResponse(PrintWriter out, String orderpart_id, String status, String closed) throws IOException {

        Query query = order_database.getView("ddoc/orderpartidstatusview").createQuery();
        ArrayList keyArray = new ArrayList();
        keyArray.add(orderpart_id);
        QueryEnumerator result = null;
        try {
            result = query.run();
        } catch (CouchbaseLiteException e) {
            Log.e(TAG, "Error running orderpartidstatusview query", e);
        }
        String orderstatus_id = null;
        if (result.hasNext()) {
            QueryRow row = result.next();
            orderstatus_id = row.getDocumentId();
        }

        Document doc = order_database.getDocument(orderstatus_id);

        Map<String, Object> properties = new HashMap<String, Object>();

        properties.putAll(doc.getProperties());
        properties.put("status", status);
        properties.put("closed", closed);        
        properties.put("updated_on", new java.util.Date().getTime());
        
        try {
            doc.putProperties(properties);
        } catch (CouchbaseLiteException e) {
            Log.e(TAG, "Error confirming order ", e);
        }

        out.println("{\"status\": \"ok\"}");
    }


    private void getPendingOrdersResponse(PrintWriter out) throws IOException {

        Query query = order_database.getView("ddoc/orderstatusview").createQuery();
        query.setStartKey(Arrays.asList("0"));
        query.setEndKey(Arrays.asList("0", "zzz"));

        QueryEnumerator result = null;
        try {
            result = query.run();
        } catch (CouchbaseLiteException e) {
            Log.e(TAG, "Error running query", e);
        }

        Document doc;
        Map<String, Object> order ;
        ArrayList orders = new ArrayList() ;
        for (Iterator<QueryRow> it = result; it.hasNext(); ) {
            QueryRow row = it.next();


            String orderpart_id = (String) row.getValue();

            doc = order_database.getDocument(orderpart_id);
            orders.add(doc.getProperties());
        }

        ObjectMapper mapper = new ObjectMapper();
        out.println(mapper.writeValueAsString(orders));

    }

    private void orderPaidResponse(PrintWriter out, String order_id, float amount) throws IOException {

        Query query = order_database.getView("ddoc/orderstatusbyorderidview").createQuery();
        query.setStartKey(Arrays.asList(order_id));
        query.setEndKey(Arrays.asList(order_id, "zzz"));

        QueryEnumerator result = null;
        try {
            result = query.run();
        } catch (CouchbaseLiteException e) {
            Log.e(TAG, "Error running query on orderstatusbyorderidview", e);
        }

        for (Iterator<QueryRow> it = result; it.hasNext(); ) {
            QueryRow row = it.next();

            Document doc = row.getDocument();

            Map<String, Object> properties = new HashMap<String, Object>();

            properties.putAll(doc.getProperties());
            properties.put("status", "paid");
            properties.put("closed", "1");        
            properties.put("updated_on", new java.util.Date().getTime());
            
            try {
                doc.putProperties(properties);
            } catch (CouchbaseLiteException e) {
                Log.e(TAG, "Error updating order status to paid ", e);
            }

        }

        ObjectMapper mapper = new ObjectMapper();
        out.println("{\"status\": \"ok\"}");
    }
}
