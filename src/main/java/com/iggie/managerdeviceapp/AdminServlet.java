package com.iggie.managerdeviceapp;

import com.couchbase.lite.*;
import com.couchbase.lite.android.AndroidContext;
import com.couchbase.lite.util.Log;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.*;

public class AdminServlet extends HttpServlet
{
    final String TAG = this.getClass().getName();

//    private ContentResolver resolver;
    private android.content.Context androidContext;
    private Manager manager;
    private Database database;
    private String outletId;

    @Override
    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);
//        resolver = (ContentResolver)getServletContext().getAttribute("org.mortbay.ijetty.contentResolver");
        androidContext = (android.content.Context)config.getServletContext().getAttribute("org.mortbay.ijetty.context");
        outletId = getServletContext().getInitParameter("outletId");

        // create a manager
        try {
            manager = new Manager(new AndroidContext(androidContext), Manager.DEFAULT_OPTIONS);
            Log.d (TAG, "Manager created");
        } catch (IOException e) {
            Log.e(TAG, "Cannot create manager object");
            return;
        }

        // create a new database
        String dbName = getServletContext().getInitParameter("outletdbname");
        try {
            database = manager.getDatabase(dbName);
            Log.d (TAG, "Database created");

        } catch (CouchbaseLiteException e) {
            Log.e(TAG, "Cannot get database");
            return;
        }

        getServletContext().setAttribute("database", database );

        database.getView("staffview").setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {

                if (document.get("type").equals("staff")) {
                    List dataList = (List) document.get("data");

                    Map<String, Object> dataMap;
                    for (ListIterator<Map<String, Object>> li = dataList.listIterator(); li.hasNext(); ) {
                        dataMap = li.next();
                        emitter.emit(Arrays.asList(document.get("outletId"), dataMap.get("id")), dataMap);
                    }
                }
            }
        }, "1");

        database.getView("deviceview").setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {

                if (document.get("type").equals("device")) {
                    List dataList = (List) document.get("data");

                    Map<String, Object> dataMap;
                    for (ListIterator<Map<String, Object>> li = dataList.listIterator(); li.hasNext(); ) {
                        dataMap = li.next();
                        emitter.emit(Arrays.asList(document.get("outletId"), dataMap.get("id")), dataMap);
                    }
                }
            }
        }, "1");

        database.getView("tableview").setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {

                if (document.get("type").equals("table")) {
                    List dataList = (List) document.get("data");

                    Map<String, Object> dataMap;
                    for (ListIterator<Map<String, Object>> li = dataList.listIterator(); li.hasNext(); ) {
                        dataMap = li.next();
                        emitter.emit(Arrays.asList(document.get("outletId"), dataMap.get("id")), dataMap);
                    }
                }
            }
        }, "1");

        database.getView("menuview").setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {

                if (document.get("type").equals("menu")) {
                    List dataList = (List) document.get("data");

                    Map<String, Object> dataMap;
                    for (ListIterator<Map<String, Object>> li = dataList.listIterator(); li.hasNext(); ) {
                        dataMap = li.next();
                        emitter.emit(Arrays.asList(document.get("outletId"), dataMap.get("itemId")), dataMap);
                    }
                }
            }
        }, "1");

        com.couchbase.lite.util.Log.enableLogging(Log.TAG_VIEW, Log.VERBOSE);
        com.couchbase.lite.util.Log.enableLogging(Log.TAG_QUERY, Log.VERBOSE);
        com.couchbase.lite.util.Log.enableLogging(Log.TAG_DATABASE, Log.VERBOSE);

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
            case "LOADOUTLETDB": {
                loadOutletDB();
                writer.println("{\"status\": \"OK\"}" );
                break;
            }
            case "PURGEOUTLETDB": {
                purgeOutletDB();
                writer.println("{\"status\": \"OK\"}" );
                break;
            }
            case "DUMPSTAFF": {
                dumpStaff(writer);
                break;
            }
        }

    }

    public void loadOutletDB() throws IOException {

        loadJsonFile("device");
        loadJsonFile("staff");
        loadJsonFile("table");
        loadJsonFile("menu");
    }

    private void loadJsonFile(String type)  throws IOException {

        ObjectMapper mapper = new ObjectMapper();
        InputStreamReader isr = null;
        Map<String, Object> jsonMap = null;
        try {
            isr = new InputStreamReader(getServletContext().getResourceAsStream("/"+type+".json"));
            jsonMap = mapper.readValue(isr, new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            Log.e(TAG, "Cannot read/parse " + type + ".json" , e);
        } finally {
            if (isr != null) {
                isr.close();
            }
        }

        Document document = database.getDocument(type+jsonMap.get("outletId"));
        try {
            document.putProperties(jsonMap);
        } catch (CouchbaseLiteException e) {
            Log.e(TAG, "Cannot write document to database", e);
        }

        Map<String, Object> doc = document.getProperties();
        Log.i(TAG, "document.data=" + doc.get("data"));
    }


    public void purgeOutletDB() {

        Query query = database.createAllDocumentsQuery();
        query.setIndexUpdateMode(Query.IndexUpdateMode.NEVER);
        query.setAllDocsMode(Query.AllDocsMode.ALL_DOCS);
        QueryEnumerator result = null;
        try {
            result = query.run();
        } catch (CouchbaseLiteException e) {
            Log.e(TAG, "Error running query", e);
        }
        for (Iterator<QueryRow> it = result; it.hasNext(); ) {
            QueryRow row = it.next();
            try {
                row.getDocument().purge();
                Log.i(TAG, "Purged document : %s", row.getDocumentId());
            } catch (CouchbaseLiteException e) {
                Log.e(TAG, "Error purging document", e);
            }
        }
    }


    public void dumpStaff( PrintWriter writer ) {

        Query query = database.getView("staffview").createQuery();
        QueryEnumerator result = null;
        try {
            result = query.run();
        } catch (CouchbaseLiteException e) {
            Log.e(TAG, "Error running query", e);
        }
        for (Iterator<QueryRow> it = result; it.hasNext(); ) {
            QueryRow row = it.next();
            writer.println(row.getValue());
            Log.i(TAG, "Data %s | %s ", row.getKey(), row.getValue());
        }
    }
}
