/**
 * Copyright 2014 IBM Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/


package com.ibm.dcm;

import java.io.*;
import java.net.*;
import java.security.cert.*;
import javax.net.ssl.*;


/**
 * This class opens, exposes, and closes an SSL connection that accepts any server.
 * 
 */
public class SSLConnection {

  private class VeryTrustingHostNameVerifier implements HostnameVerifier {
    public boolean verify (String hostname, SSLSession session) {
      return true; 
    } 
  }

  // Set up a very trusting trust manager.
  private static TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
      public X509Certificate[] getAcceptedIssuers() {
        return null;
      } 
      public void checkClientTrusted(X509Certificate[] certs, String authType) {} 
      public void checkServerTrusted(X509Certificate[] certs, String authType) {}
    }
  };

  private SSLContext context;


  public SSLConnection() throws Exception {
    // Do all the lookups and whatnot, get the overhead out of the way.
    context = SSLContext.getInstance("SSL");
    context.init(null, trustAllCerts, null);
    HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());
  }


  public NamedParams sendAndReceive (NamedParams params, String request) throws Exception {
    String hostname = params.get("hostname");
    if (params.get("host") != null) {
      // Code is inconsistent in using "host" or "hostname".  Oh well.
      hostname = params.get("host");
    }
    String url = "https://" + hostname + ":" + params.get("port") + "/service/mgmt/current";
    boolean dumpinput = false;
    if (params.get("dumpinput") != null && params.get("dumpinput").equals("true"))
      dumpinput = true;
    boolean dumpoutput = false;
    if (params.get("dumpoutput") != null && params.get("dumpoutput").equals("true"))
      dumpoutput = true;
    String rawresponse = sendAndReceive (url, params.get("uid"), params.get("pwd"), request, dumpinput, dumpoutput);
    NamedParams result = new NamedParams (params);
    result.set ("request", request);
    result.set ("rawresponse", rawresponse);
    return result;
  }


  /**
   * This method sends a SOMA request and returns the response.
   * 
   * An exception is thrown for any overt failure.
   * 
   */
  public String sendAndReceive (String dpurl, String uid, String pwd, String request) throws Exception {
    return sendAndReceive(dpurl, uid, pwd, request, false, false);
  }


  /**
   * This method sends a SOMA request and returns the response.
   * 
   * An exception is thrown for any overt failure.
   * 
   */
  public String sendAndReceive (String dpurl, String uid, String pwd, String request, boolean dumpinput, boolean dumpoutput) throws Exception {

    String result = "";

    HttpsURLConnection conn = null;
    Writer out = null;
    BufferedReader in = null;
    try {
      conn = (HttpsURLConnection)(new URL(dpurl)).openConnection();
      conn.setHostnameVerifier(new VeryTrustingHostNameVerifier());

      // Set some options.
      conn.setUseCaches(false);
      conn.setDoOutput(true);
      conn.setDoInput(true);
      conn.setRequestMethod("POST");
      String authString = uid + ":" + pwd;
      conn.setRequestProperty("Authorization", "Basic " + Base64.bytesToString(Base64.toBase64 (authString.getBytes(), null)));

      if (dumpinput) {
        System.out.println("SSLConnection.sendAndReceive sent to " + dpurl + " :");
        System.out.println(request);
      }

      // Send the request.
      out = new OutputStreamWriter(conn.getOutputStream());
      out.write(request); 
      out.flush();

      // Gather the response.  (This is actually the moment when the send/receive takes place.)
      in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
      StringBuffer sb = new StringBuffer();
      String line = null;
      while (true) {
        line = in.readLine();
        if ((line == null) || line.equals(null)) {
          break;
        } else {
          sb.append(line);
        }
      }
      result = sb.toString();

      if (dumpoutput) {
        System.out.println("SSLConnection.sendAndReceive received:");
        System.out.println(result);
      }

      in.close();
      out.close();

    } catch (Exception e) {

      // Close everything, ignoring any errors.
      if (in != null) {
        try {
          in.close();
        } catch (Exception x) {
        }
      }
      if (out != null) {
        try {
          out.close();
        } catch (Exception x) {
        }
      }

      // Pass on the exception.
      throw e;
    }

    return result;
  }
} 
