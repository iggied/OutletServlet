package com.iggie.managerdeviceapp;

import android.content.ContentResolver;
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
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

public class AdminServlet extends HttpServlet
{
    final String TAG = this.getClass().getName();

    private ContentResolver resolver;
    private android.content.Context androidContext;
    private Manager manager;
    private Database database;

    @Override
    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);
        resolver = (ContentResolver)getServletContext().getAttribute("org.mortbay.ijetty.contentResolver");
        androidContext = (android.content.Context)config.getServletContext().getAttribute("org.mortbay.ijetty.context");

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

        View staffView = database.getView("outletstaff");
        staffView.setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                if (document.get("type").equals("staff") ) {

                    List staffList = (List) document.get("data");
                    Map<String, Object> staff;
                    for( ListIterator<Map<String, Object>> li = staffList.listIterator(); li.hasNext(); ) {
                        staff = li.next();
                        emitter.emit(document.get("outletId")+":"+staff.get("id"), staff);
                    }

                }
            }
        }, "10");

        Log.d(TAG, "Servlet init completed");

    }

    public ContentResolver getContentResolver()
    {
        return resolver;
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
            case "SYNCOUTLETDB": {
                syncOutletDB();
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

    public void syncOutletDB() throws IOException {

        ObjectMapper mapper = new ObjectMapper();
        InputStreamReader isr = null;
        Map<String, Object> staff = null;
        try {
            isr = new InputStreamReader(getServletContext().getResourceAsStream("/Staff.json"));
            staff = mapper.readValue(isr, new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            Log.e(TAG, "Cannot read/parse Staff.json", e);
        } finally {
            if (isr != null) {
                isr.close();
            }
        }

        //Map<String,Object> props = mapper.convertValue(staff, Map.class);
        Document document = database.getDocument("staff"+staff.get("outletId"));
        try {
            document.putProperties(staff);
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

        Query query = database.getView("outletstaff").createQuery();
        QueryEnumerator result = null;
        try {
            result = query.run();
        } catch (CouchbaseLiteException e) {
            Log.e(TAG, "Error running query", e);
        }
        for (Iterator<QueryRow> it = result; it.hasNext(); ) {
            QueryRow row = it.next();
            writer.println(row.getValue());
            Log.i(TAG, "Data %s : %s ", row.getKey(), row.getValue());
        }
    }
}
