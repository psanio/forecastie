package cz.martykan.forecastie.tasks;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.design.widget.Snackbar;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ProgressBar;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import cz.martykan.forecastie.Constants;
import cz.martykan.forecastie.R;
import cz.martykan.forecastie.activities.MainActivity;

public abstract class GenericRequestTask extends AsyncTask<String, String, TaskOutput> {

    ProgressDialog progressDialog;
    Context context;
    MainActivity activity;
    public int loading = 0;

    public GenericRequestTask(Context context, MainActivity activity, ProgressDialog progressDialog) {
        this.context = context;
        this.activity = activity;
        this.progressDialog = progressDialog;
    }

    @Override
    protected void onPreExecute() {
        incLoadingCounter();
        if(!progressDialog.isShowing()) {
            progressDialog.setMessage(context.getString(R.string.downloading_data));
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.show();
        }
    }

    @Override
    protected TaskOutput doInBackground(String... params) {
        TaskOutput output = new TaskOutput();

        String response = "";
        String[] coords = new String[]{};

        if (params != null && params.length > 0) {
            final String zeroParam = params[0];
            if ("cachedResponse".equals(zeroParam)) {
                response = params[1];
                // Actually we did nothing in this case :)
                output.taskResult = TaskResult.SUCCESS;
            } else if ("coords".equals(zeroParam)) {
                String lat = params[1];
                String lon = params[2];
                coords = new String[]{lat, lon};
            }
        }

        if (response.isEmpty()) {
            try {
                URL url = provideURL(coords);
                Log.i("URL", url.getProtocol() + "://" + url.getHost() + url.getPath());
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                if (urlConnection instanceof HttpsURLConnection) {
                    ((HttpsURLConnection) urlConnection).setSSLSocketFactory(createTls12SocketFactory());
                }
                urlConnection.setConnectTimeout(15000);
                urlConnection.setReadTimeout(15000);
                if (urlConnection.getResponseCode() == 200) {
                    InputStreamReader inputStreamReader = new InputStreamReader(urlConnection.getInputStream());
                    BufferedReader r = new BufferedReader(inputStreamReader);

                    int responseCode = urlConnection.getResponseCode();
                    String line = null;
                    while ((line = r.readLine()) != null) {
                        response += line + "\n";
                    }
                    close(r);
                    urlConnection.disconnect();
                    // Background work finished successfully
                    Log.i("Task", "done successfully");
                    output.taskResult = TaskResult.SUCCESS;
                    // Save date/time for latest successful result
                    activity.saveLastUpdateTime(PreferenceManager.getDefaultSharedPreferences(context));
                }
                else if (urlConnection.getResponseCode() == 429) {
                    // Too many requests
                    Log.i("Task", "too many requests");
                    output.taskResult = TaskResult.TOO_MANY_REQUESTS;
                }
                else {
                    // Bad response from server
                    Log.i("Task", "bad response " + urlConnection.getResponseCode());
                    output.taskResult = TaskResult.BAD_RESPONSE;
                }
            } catch (IOException e) {
                Log.e("Network", e.getClass().getSimpleName() + ": " + e.getMessage());
                // Exception while reading data from url connection
                output.taskResult = TaskResult.IO_EXCEPTION;
            }
        }

        if (TaskResult.SUCCESS.equals(output.taskResult)) {
            // Parse JSON data
            ParseResult parseResult = parseResponse(response);
            if (ParseResult.CITY_NOT_FOUND.equals(parseResult)) {
                // Retain previously specified city if current one was not recognized
                restorePreviousCity();
            }
            output.parseResult = parseResult;
        }

        return output;
    }

    @Override
    protected void onPostExecute(TaskOutput output) {
        if(loading == 1) {
            progressDialog.dismiss();
        }
        decLoadingCounter();

        updateMainUI();

        handleTaskOutput(output);
    }

    protected final void handleTaskOutput(TaskOutput output) {
        switch (output.taskResult) {
            case SUCCESS: {
                ParseResult parseResult = output.parseResult;
                if (ParseResult.CITY_NOT_FOUND.equals(parseResult)) {
                    Snackbar.make(activity.findViewById(android.R.id.content), context.getString(R.string.msg_city_not_found), Snackbar.LENGTH_LONG).show();
                } else if (ParseResult.JSON_EXCEPTION.equals(parseResult)) {
                    Snackbar.make(activity.findViewById(android.R.id.content), context.getString(R.string.msg_err_parsing_json), Snackbar.LENGTH_LONG).show();
                }
                break;
            }
            case TOO_MANY_REQUESTS: {
                Snackbar.make(activity.findViewById(android.R.id.content), context.getString(R.string.msg_too_many_requests), Snackbar.LENGTH_LONG).show();
                break;
            }
            case BAD_RESPONSE: {
                Snackbar.make(activity.findViewById(android.R.id.content), context.getString(R.string.msg_connection_problem), Snackbar.LENGTH_LONG).show();
                break;
            }
            case IO_EXCEPTION: {
                Snackbar.make(activity.findViewById(android.R.id.content), context.getString(R.string.msg_connection_not_available), Snackbar.LENGTH_LONG).show();
                break;
            }
        }
    }

    private String getLanguage() {
        String language = Locale.getDefault().getLanguage();
        if (language.equals("cs")) {
            language = "cz";
        }
        return language;
    }

    private URL provideURL(String[] coords) throws UnsupportedEncodingException, MalformedURLException {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        String apiKey = sp.getString("apiKey", activity.getResources().getString(R.string.apiKey)).trim();

        StringBuilder urlBuilder = new StringBuilder("https://api.openweathermap.org/data/2.5/");
        urlBuilder.append(getAPIName()).append("?");
        if (coords.length == 2) {
            urlBuilder.append("lat=").append(coords[0]).append("&lon=").append(coords[1]);
        } else {
            final String city = sp.getString("city", Constants.DEFAULT_CITY);
            urlBuilder.append("q=").append(URLEncoder.encode(city, "UTF-8"));
        }
        urlBuilder.append("&lang=").append(getLanguage());
        urlBuilder.append("&mode=json");
        urlBuilder.append("&appid=").append(apiKey);

        return new URL(urlBuilder.toString());
    }

    private SSLSocketFactory createTls12SocketFactory() throws IOException {
        try {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            InputStream certificateStream = context.getResources().openRawResource(R.raw.sectigo_root_r46);
            Certificate certificate;
            try {
                certificate = certificateFactory.generateCertificate(certificateStream);
            } finally {
                close(certificateStream);
            }

            KeyStore bundledStore = KeyStore.getInstance(KeyStore.getDefaultType());
            bundledStore.load(null, null);
            bundledStore.setCertificateEntry("sectigo-r46", certificate);

            TrustManagerFactory systemTrustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            systemTrustManagerFactory.init((KeyStore) null);
            TrustManagerFactory bundledTrustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            bundledTrustManagerFactory.init(bundledStore);

            X509TrustManager systemTrustManager = findTrustManager(systemTrustManagerFactory.getTrustManagers());
            X509TrustManager bundledTrustManager = findTrustManager(bundledTrustManagerFactory.getTrustManagers());
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{new CombinedTrustManager(systemTrustManager, bundledTrustManager)}, null);
            return new Tls12SocketFactory(context.getSocketFactory());
        } catch (Exception e) {
            throw new IOException("Unable to enable TLS 1.2", e);
        }
    }

    private static X509TrustManager findTrustManager(TrustManager[] trustManagers) throws IOException {
        for (TrustManager trustManager : trustManagers) {
            if (trustManager instanceof X509TrustManager) {
                return (X509TrustManager) trustManager;
            }
        }
        throw new IOException("No X.509 trust manager available");
    }

    private static class CombinedTrustManager implements X509TrustManager {
        private final X509TrustManager systemTrustManager;
        private final X509TrustManager bundledTrustManager;

        CombinedTrustManager(X509TrustManager systemTrustManager, X509TrustManager bundledTrustManager) {
            this.systemTrustManager = systemTrustManager;
            this.bundledTrustManager = bundledTrustManager;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
            systemTrustManager.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
            try {
                systemTrustManager.checkServerTrusted(chain, authType);
            } catch (java.security.cert.CertificateException ignored) {
                bundledTrustManager.checkServerTrusted(chain, authType);
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            List<X509Certificate> issuers = new ArrayList<X509Certificate>();
            for (X509Certificate issuer : systemTrustManager.getAcceptedIssuers()) {
                issuers.add(issuer);
            }
            for (X509Certificate issuer : bundledTrustManager.getAcceptedIssuers()) {
                issuers.add(issuer);
            }
            return issuers.toArray(new X509Certificate[issuers.size()]);
        }
    }

    private static class Tls12SocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;

        Tls12SocketFactory(SSLSocketFactory delegate) {
            this.delegate = delegate;
        }

        private Socket configure(Socket socket) {
            if (socket instanceof SSLSocket) {
                ((SSLSocket) socket).setEnabledProtocols(new String[]{"TLSv1.2"});
            }
            return socket;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return delegate.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
            return configure(delegate.createSocket(socket, host, port, autoClose));
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return configure(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return configure(delegate.createSocket(host, port, localHost, localPort));
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return configure(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
            return configure(delegate.createSocket(address, port, localAddress, localPort));
        }
    }

    private void restorePreviousCity() {
        if (!TextUtils.isEmpty(activity.recentCity)) {
            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
            editor.putString("city", activity.recentCity);
            editor.commit();
            activity.recentCity = "";
        }
    }

    private static void close(Closeable x) {
        try {
            if (x != null) {
                x.close();
            }
        } catch (IOException e) {
            Log.e("IOException Data", "Error occurred while closing stream");
        }
    }

    private void incLoadingCounter() {
        loading++;
    }

    private void decLoadingCounter() {
        loading--;
    }

    protected void updateMainUI() { }

    protected abstract ParseResult parseResponse(String response);
    protected abstract String getAPIName();
}
