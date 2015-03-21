package com.iggie.managerdeviceapp;

import android.content.ContentResolver;
import com.couchbase.lite.*;
import com.couchbase.lite.android.AndroidContext;
import com.couchbase.lite.util.Log;
import org.codehaus.jackson.map.ObjectMapper;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
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
        String dbname = "branchcdb";
        try {
            database = manager.getDatabase(dbname);
            Log.d (TAG, "Database created");

        } catch (CouchbaseLiteException e) {
            Log.e(TAG, "Cannot get database");
            return;
        }

        // Temporary data creation for testing
        // Create a view and register its map function:

        SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        Calendar calendar = GregorianCalendar.getInstance();
        String currentTimeString = dateFormatter.format(calendar.getTime());

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("type", "staff");
        properties.put("staffId", "admin");
        properties.put("created_at", currentTimeString);
        properties.put("staffPin", "nimda");
        Document document = database.getDocument("admin");
        try {
            document.putProperties(properties);
        } catch (CouchbaseLiteException e) {
            Log.e(TAG, "Cannot write document to database", e);
        }

        View phoneView = database.getView("staff");
        phoneView.setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                if (document.get("type").equals("staff") ) {
                    emitter.emit(document.get("staffId"), document.get("staffPin"));
                }
            }
        }, "2");

        // Temporary till here

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
            case "VALIDATECREDS": {
                String staffId = parameterMap != null ? parameterMap.get("staffId")[0] : "" ;
                String staffPin = parameterMap != null ? parameterMap.get("staffPin")[0] : "" ;

                validateCredsResponse(writer, staffId, staffPin);
            }
        }

    }



    private void validateCredsResponse(PrintWriter out, String staffId, String staffPin)
    {


        Log.i(TAG, "post staffId=" + staffId);
        Log.i(TAG, "post staffPin=" + staffPin);


        Document document = database.getExistingDocument(staffId);

        if (document != null) {
            Log.i(TAG, "document staffId=" + document.getProperty("staffId"));
            Log.i(TAG, "document staffPin=" + document.getProperty("staffPin"));
        }


        class MyValue {
            public String valid;

            public MyValue( String value ){
                this.valid = value;
            }
        }

        MyValue value = new MyValue("1");

        ObjectMapper mapper = new ObjectMapper();
        try {
            String jsonString = mapper.writeValueAsString(value);
            Log.i(TAG, jsonString);
        } catch (IOException e) {
            Log.e(TAG, "error in JSON serializing");
        }


        out.println("{");
        if (document != null && document.getProperty("staffPin").equals(staffPin)) {
            out.println("\"valid\": \"1\"");
        } else {
            out.println("\"valid\": \"0\"");
        }
        out.println("}");

    }

}
