package com.iggie.managerdeviceapp;

import com.couchbase.lite.*;
import com.couchbase.lite.android.AndroidContext;
import com.couchbase.lite.listener.Credentials;
import com.couchbase.lite.listener.LiteListener;
import com.couchbase.lite.util.Log;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.HashMap;

public class DatabaseServlet extends HttpServlet {

    final String TAG = this.getClass().getName();

    private android.content.Context androidContext;
    private Manager manager = null;
    private Database outlet_database = null;
    private Database order_database = null;
    private LiteListener listener = null;

    private String outletId;

    private static final int DEFAULT_LISTEN_PORT = 5984;
    private static final String OUTLET_DATABASE_NAME = "outletdb";
    private static final String ORDER_DATABASE_NAME = "orderdb";
    private static final String LISTEN_PORT_PARAM_NAME = "listen_port";

    private static final String LISTEN_LOGIN_PARAM_NAME = "username";
    private static final String LISTEN_PASSWORD_PARAM_NAME = "password";

    private Credentials allowedCredentials;


    @Override
    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);
        androidContext = (android.content.Context)config.getServletContext().getAttribute("org.mortbay.ijetty.context");
        outletId = getServletContext().getInitParameter("outletId");

        try {
            int port = startCBLListener(DEFAULT_LISTEN_PORT);
            showListenPort(port);
            showListenCredentials();
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error starting LiteServ", e);
        }

        CreateViews();
        com.couchbase.lite.util.Log.enableLogging(Log.TAG_VIEW, Log.VERBOSE);
        com.couchbase.lite.util.Log.enableLogging(Log.TAG_QUERY, Log.VERBOSE);
        com.couchbase.lite.util.Log.enableLogging(Log.TAG_DATABASE, Log.VERBOSE);

        Log.d(TAG, "Servlet init completed");

    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    }

    private void showListenPort(int listenPort) {
        android.util.Log.d(TAG, "listenPort: " + listenPort);
    }

    private void showListenCredentials() {
        String credentialsDisplay = String.format(
                "login: %s password: %s",
                allowedCredentials.getLogin(),
                allowedCredentials.getPassword()
        );
        android.util.Log.v(TAG, credentialsDisplay);

    }

    private int startCBLListener(int suggestedListenPort) throws IOException, CouchbaseLiteException {

        manager = new Manager(new AndroidContext(androidContext), Manager.DEFAULT_OPTIONS);

        outlet_database = manager.getDatabase(OUTLET_DATABASE_NAME + outletId);
        outlet_database.open();

        order_database = manager.getDatabase(ORDER_DATABASE_NAME);
        order_database.open();

        getServletContext().setAttribute("outlet_database", outlet_database);
        getServletContext().setAttribute("order_database", order_database);


        if (LISTEN_LOGIN_PARAM_NAME!=null && LISTEN_PASSWORD_PARAM_NAME!=null){
            allowedCredentials = new Credentials(LISTEN_LOGIN_PARAM_NAME, LISTEN_PASSWORD_PARAM_NAME);
        } else{
            allowedCredentials = new Credentials();
        }

        listener = new LiteListener(manager, suggestedListenPort, allowedCredentials);

        int port = listener.getListenPort();
        Thread thread = new Thread(listener);
        thread.start();

        return port;

    }

    protected void CreateViews(){

        outlet_database.getView("ddoc/staffview").setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {

                if (document.get("type").equals("staff")) {
                    List dataList = (List) document.get("data");

                    Map<String, Object> dataMap;
                    for (ListIterator<Map<String, Object>> li = dataList.listIterator(); li.hasNext(); ) {
                        dataMap = li.next();
                        emitter.emit(Arrays.asList(dataMap.get("id"), dataMap.get("pin")), null);
                    }
                }
            }
        }, "5");

        outlet_database.getView("ddoc/deviceview").setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {

                if (document.get("type").equals("device")) {
                    List dataList = (List) document.get("data");

                    Map<String, Object> dataMap;
                    for (ListIterator<Map<String, Object>> li = dataList.listIterator(); li.hasNext(); ) {
                        dataMap = li.next();
                        emitter.emit(Arrays.asList(dataMap.get("id")), null);
                    }
                }
            }
        }, "3");

        outlet_database.getView("ddoc/tableview").setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {

                if (document.get("type").equals("table")) {
                    List dataList = (List) document.get("data");

                    Map<String, Object> dataMap;
                    for (ListIterator<Map<String, Object>> li = dataList.listIterator(); li.hasNext(); ) {
                        dataMap = li.next();
                        emitter.emit(Arrays.asList(dataMap.get("id")), null);
                    }
                }
            }
        }, "3");

/*        outlet_database.getView("menuview").setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {

                if (document.get("type").equals("menu")) {
                    List dataList = (List) document.get("data");

                    Map<String, Object> dataMap;
                    for (ListIterator<Map<String, Object>> li = dataList.listIterator(); li.hasNext(); ) {
                        dataMap = li.next();

                        List priceCatList = (List) dataMap.get("priceCat");
                        Map<String, Object> priceMap;
                        for (ListIterator<Map<String, Object>> i = priceCatList.listIterator(); i.hasNext(); ) {
                            priceMap = i.next();

                            emitter.emit(Arrays.asList(dataMap.get("itemId"), priceMap.get("catCode")), null);
                        }
                    }
                }
            }
        }, "5");*/

        outlet_database.getView("ddoc/menuview").setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {

                if (document.get("type").equals("menuitem")) {

                    List priceCatList = (List) document.get("priceCat");
                    Map<String, Object> priceMap;
                    for (ListIterator<Map<String, Object>> i = priceCatList.listIterator(); i.hasNext(); ) {
                        priceMap = i.next();

                        emitter.emit(Arrays.asList(document.get("itemId"), priceMap.get("catCode")), null);
                    }
                
                }
            }
        }, "5");


/*        order_database.getView("ddoc/ordertablestatusview").setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {

                if (document.get("type").equals("orderpart")) {
                    Map<String, Object> order;
                    order = (HashMap) document.get("order");

                    emitter.emit(Arrays.asList(order.get("tableNumber"), document.get("status"), document.get("created_on")),
                            null);
/*
//                    List dataList = (List) order.get("data");
//                    Map<String, Object> dataMap;
//                    for (ListIterator<Map<String, Object>> li = dataList.listIterator(); li.hasNext(); ) {
//                        dataMap = li.next();
//                        emitter.emit(Arrays.asList(document.get("outletId"), dataMap.get("itemId")), dataMap);
//                    }

                }
            }
        }, "5");
*/


/*        order_database.getView("ddoc/orderbystatusview").setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {

                if (document.get("type").equals("orderpart")) {

                    emitter.emit(Arrays.asList(document.get("status"), document.get("created_on")),
                            null);

                }
            }
        }, "2");*/


/*        order_database.getView("ddoc/pendingorderview").setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {

                if (document.get("type").equals("orderpart") && (document.get("status").equals("placed") || document.get("status").equals("confirmed")) ) {
                    emitter.emit(Arrays.asList(document.get("created_on")),
                            null);
                }
            }
        }, "2");
*/
 
        order_database.getView("ddoc/orderstatusbyorderidview").setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {

                if (document.get("type").equals("orderpartstatus")) {

                    emitter.emit(Arrays.asList(document.get("order_id"), document.get("closed"), document.get("status")), null);
                }
            }
        }, "4");

        order_database.getView("ddoc/orderpartidstatusview").setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {

                if (document.get("type").equals("orderpartstatus")) {

                    emitter.emit(document.get("orderpart_id"), null);
                }
            }
        }, "3");

        order_database.getView("ddoc/orderstatusview").setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {

                if (document.get("type").equals("orderpartstatus")) {

                    emitter.emit(Arrays.asList(document.get("closed"), document.get("status")), document.get("orderpart_id"));
                }
            }
        }, "3");

        order_database.getView("ddoc/ordertablestatusview").setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {

                if (document.get("type").equals("orderpartstatus")) {

                    emitter.emit(Arrays.asList(document.get("tableNumber"), document.get("closed"), document.get("status")), 
                        document.get("orderpart_id"));
                }
            }
        }, "6");

    }


    public void destroy() {
        listener.stop();
        outlet_database.close();
        order_database.close();
        manager.close();
    }

}
