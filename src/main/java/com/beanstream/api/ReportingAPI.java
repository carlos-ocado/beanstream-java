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
package com.beanstream.api;

import com.beanstream.Configuration;
import com.beanstream.connection.BeanstreamUrls;
import com.beanstream.connection.HttpMethod;
import com.beanstream.connection.HttpsConnector;
import com.beanstream.domain.Transaction;
import com.beanstream.exceptions.BeanstreamApiException;
import com.beanstream.responses.BeanstreamResponse;
import com.google.gson.Gson;
import org.apache.http.HttpStatus;
import java.util.Date;

/**
 * Get a transaction or search for a range of transactions with the Reporting API.
 * 
 * You can get an individual transaction and all of its Adjustments (voids, returns) or 
 * you can search for transactions using date ranges and specific criteria.
 * 
 * @author bowens
 */
public class ReportingAPI {
    
    private Configuration config;
    private HttpsConnector connector;
    private final Gson gson = new Gson();

    public ReportingAPI(Configuration config) {
        this.config = config;
        connector = new HttpsConnector(config.getMerchantId(), config.getPaymentsApiPasscode());
    }

    public void setConfig(Configuration config) {
        this.config = config;
        connector = new HttpsConnector(config.getMerchantId(), config.getPaymentsApiPasscode());
    }
    
    /**
     * Get a single transaction
     * @param paymentId
     * @return
     * @throws BeanstreamApiException 
     */
    public Transaction getTransaction(String paymentId) throws BeanstreamApiException {
        
        assertNotEmpty(paymentId, "invalid paymentId");
        
        String url = BeanstreamUrls.getPaymentUrl(config.getPlatform(), config.getVersion(), paymentId);
        
        // get the transaction using the REST API
        String response = connector.ProcessTransaction(HttpMethod.get, url, null);
        
        return gson.fromJson(response, Transaction.class);
    }
    
    
    private void assertNotEmpty(String value, String errorMessage)
            throws BeanstreamApiException {
        // could use StringUtils.assertNotNull();
        if (value == null || value.trim().isEmpty()) {
            // TODO - do we need to supply category and code ids here?
            BeanstreamResponse response = BeanstreamResponse.fromMessage("invalid payment request");
            throw BeanstreamApiException.getMappedException(HttpStatus.SC_BAD_REQUEST, response);
        }
    }
    
   public static boolean isNullOrEmpty(String s)
   {
     if (s.trim().length() == 0 || s == null)
      return true;
     else
      return false;
   }    
    
   public List<TransactionRecord> Query(final Date startDate, final Date endDate, final int startRow, final int endRow, final Criteria[] criteriaa) throws BeanstreamApiException
    {
     if (endDate == null || startDate == null)
      throw new IllegalArgumentException("Start Date and End Date cannot be null!");
     if (endDate.compareTo(startDate)<0)
      throw new IllegalArgumentException("End Date cannot be less than Start Date!");
     if (endRow < startRow)
      throw new IllegalArgumentException("End Row cannot be less than Start Row!");
     if (endRow - startRow > 1000)
      throw new IllegalArgumentException("You cannot query more than 1000 rows at a time!");

     String url = BeanstreamUrls.ReportsUrl.replace("{v}", isNullOrEmpty(config.getVersion()) ? "v1" : "v" + config.getVersion())
     .replace("{p}", isNullOrEmpty(config.getPlatform()) ? "www" : config.getPlatform());

     Object query = new Object()
     {
      String name = "Search";
      Date start_date = startDate;
      Date end_date = endDate;
      int start_row = startRow;
      int end_row = endRow;
      Criteria[] criteria = criteriaa; 
     };

     String data = gson.toJson(query);

     System.out.println(data);

     String response = connector.ProcessTransaction(HttpMethod.post, url, query);

     Records records = gson.fromJson(response, Records.class);

     return records.records;
 }
}
