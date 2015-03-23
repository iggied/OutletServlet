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
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


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
            }
        }

    }



    private void validateCredsResponse(PrintWriter out, String staffId, String staffPin) throws IOException {
        Map<String, String> replyMap = new HashMap<>();
        replyMap.put("valid", "0");



        ArrayList keyArray = new ArrayList();
        keyArray.add(outletId);
        keyArray.add(staffId);

        Query query = database.getView("outletstaff").createQuery();
        query.setStartKey(Arrays.asList(outletId, staffId) );
        query.setEndKey(Arrays.asList(outletId, staffId));
        QueryEnumerator result = null;
        try {
            result = query.run();
        } catch (CouchbaseLiteException e) {
            Log.e(TAG, "Error running query", e);
        }

        if (result.hasNext()) {
            Map<String, Object> staffMap = (Map) result.next().getValue();
            Log.i(TAG, "document pin=" + staffMap.get("pin"));
            if (staffMap.get("pin").equals(staffPin)) {
                replyMap.put("valid", "1");
            }
        }

        ObjectMapper mapper = new ObjectMapper();
        out.println( mapper.writeValueAsString(replyMap));
    }

}
