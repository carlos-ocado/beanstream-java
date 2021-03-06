/* The MIT License (MIT)
 *
 * Copyright (c) 2014 Beanstream Internet Commerce Corp, Digital River, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.beanstream.connection;

import com.beanstream.exceptions.BeanstreamApiException;
import com.beanstream.responses.BeanstreamResponse;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.impl.client.DefaultHttpClient;

/**
 * Performs the connection to the API.
 * It will serialize any posted objects into json before submitting.
 * You can give it a custom GsonBuilder to style the json output to what is required
 * by the API service.
 * 
 * @author bowens
 */
public class HttpsConnector {
    
    private final int merchantId;
    private String apiPasscode;
    private GsonBuilder gsonBuilder;
    private HttpClient customHttpClient;

    public HttpsConnector(int merchantId, String apiPasscode) {
        this.merchantId = merchantId;
        this.apiPasscode = apiPasscode;
    }

    public void setCustomHttpClient(HttpClient customHttpClient) {
        this.customHttpClient = customHttpClient;
    }

    public void setGsonBuilder(GsonBuilder gsonBuilder) {
        this.gsonBuilder = gsonBuilder;
    }

    public GsonBuilder getGsonBuilder() {
        if (gsonBuilder == null) {
            gsonBuilder = new GsonBuilder();
        }
        
        return gsonBuilder;
    }

    public void setApiPasscode(String apiPasscode) {
        this.apiPasscode = apiPasscode;
    }

    public String ProcessTransaction(HttpMethod httpMethod, String url,
			Object data) throws BeanstreamApiException {
    
        try {
            
            Gson gson = getGsonBuilder().create();
            String json = data != null ? gson.toJson(data) : null;
            
            ResponseHandler<BeanstreamResponse> responseHandler = new ResponseHandler<BeanstreamResponse>() {
                @Override
                public BeanstreamResponse handleResponse(final HttpResponse http)
                        throws ClientProtocolException, IOException {
                    return BeanstreamResponse.fromHttpResponse(http);
                }
            };
            
            HttpUriRequest http = null;
                
            switch(httpMethod) {
                case post: {
                    StringEntity entity = new StringEntity(json);
                    http = new HttpPost(url);
                    ((HttpPost) http).setEntity(entity);
                    break;
                }
                case put: {
                    StringEntity entity = new StringEntity(json);
                    http = new HttpPut(url);
                    ((HttpPut) http).setEntity(entity);
                    break;
                }
                case get: {
                    http = new HttpGet(url);
                    break;
                }
                case delete: {
                    http = new HttpDelete(url);
                    break;
                }
            }

            BeanstreamResponse bsRes = process(http, responseHandler);
            int httpStatus = bsRes.getHttpStatusCode();
            if (httpStatus >= 200 && httpStatus < 300) {
                return bsRes.getResponseBody();
            } else {
                throw mappedException(httpStatus, bsRes);
            }

        } catch (UnsupportedEncodingException ex) {
            throw handleException(ex, null);
            
        } catch (IOException ex) {
            throw handleException(ex, null);
        } 

    }

    private BeanstreamResponse process(HttpUriRequest http,
                ResponseHandler<BeanstreamResponse> responseHandler) throws IOException {
        
        HttpClient httpclient;
        if (customHttpClient != null)
            httpclient = customHttpClient;
        else
            httpclient = HttpClients.createDefault();
        // Remove newlines from base64 since they can bork the header.
        // Some base64 encoders will append a newline to the end
        String auth = Base64.encode((merchantId + ":" + apiPasscode).trim().getBytes());
        
        http.addHeader("Content-Type", "application/json");
        http.addHeader("Authorization", "Passcode " + auth);

        BeanstreamResponse responseBody = httpclient.execute(http, responseHandler);
        
        return responseBody;
    }
    
    private HttpRequest getHttp(HttpMethod httpMethod, StringEntity entity) {
        if (HttpMethod.post.equals(httpMethod)) {
            HttpPost http = new HttpPost();
            http.setEntity(entity);
            return http;
		} else if (HttpMethod.put.equals(httpMethod)) {
            HttpPut http = new HttpPut();
            http.setEntity(entity);
            return http;
		} else if (HttpMethod.get.equals(httpMethod)) {
            HttpGet http = new HttpGet();
            return http;
		} else if (HttpMethod.delete.equals(httpMethod)) {
            HttpDelete http = new HttpDelete();
            return http;
        }
        return null;
    }
    
    /**
     * Provide a detailed error message when connecting to the Beanstream API fails.
     */
    private BeanstreamApiException handleException(Exception ex, HttpsURLConnection connection) {
        String message = "";
        if (connection != null) {
            try {
                int responseCode = connection.getResponseCode();
                message = responseCode+" "+connection.getContent().toString();
            } catch (IOException ex1) {
                Logger.getLogger(HttpsConnector.class.getName()).log(Level.SEVERE, "Error getting response code", ex1);
            }
        } else {
            message = "Connection error";
        }
        return new BeanstreamApiException(ex, message);
    }
    
    /**
     * Each exception will have a code and an ID that can help distinguish if the error
     * is card-holder facing (ie. Insufficient Funds) or programmer-facing (wrong API key).
     */
    private BeanstreamApiException mappedException(int status, BeanstreamResponse bsRes) {
        
        if (bsRes != null) {
            return BeanstreamApiException.getMappedException(status, bsRes);
        }
			
        return BeanstreamApiException.getMappedException(status);
    }

}
