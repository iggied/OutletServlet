package com.iggie.managerdeviceapp;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Database;
import com.couchbase.lite.Manager;
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

public class DatabaseServlet extends HttpServlet {

    final String TAG = this.getClass().getName();

    private android.content.Context androidContext;
    private Manager manager = null;
    private Database database = null;
    private LiteListener listener = null;


    private static final int DEFAULT_LISTEN_PORT = 5984;
    private static final String DATABASE_NAME = "cblite-test";
    private static final String LISTEN_PORT_PARAM_NAME = "listen_port";

    private static final String LISTEN_LOGIN_PARAM_NAME = "username";
    private static final String LISTEN_PASSWORD_PARAM_NAME = "password";

    private Credentials allowedCredentials;


    @Override
    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);
        androidContext = (android.content.Context)config.getServletContext().getAttribute("org.mortbay.ijetty.context");

        try {
            int port = startCBLListener(DEFAULT_LISTEN_PORT);
            showListenPort(port);
            showListenCredentials();
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error starting LiteServ", e);
        }

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

        database = manager.getDatabase(DATABASE_NAME);
        database.open();

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

    public void destroy() {
        listener.stop();
        database.close();
        manager.close();
    }

}
