/*******************************************************************************
 * Copyright 2013 alladin-IT OG
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package at.alladin.rmbt.controlServer;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.Format;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.TimeZone;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.restlet.resource.Get;
import org.restlet.resource.Post;

import at.alladin.rmbt.db.Client;
import at.alladin.rmbt.shared.SignificantFormat;

public class HistoryResource extends ServerResource
{
    
    @Post("json")
    public String request(final String entity)
    {
        addAllowOrigin();
        
        JSONObject request = null;
        
        final ErrorList errorList = new ErrorList();
        final JSONObject answer = new JSONObject();
        String answerString;
        
        System.out.println(MessageFormat.format(labels.getString("NEW_HISTORY"), getIP()));
        
        if (entity != null && !entity.isEmpty())
            // try parse the string to a JSON object
            try
            {
                request = new JSONObject(entity);
                
                String lang = request.optString("language");
                
                // Load Language Files for Client
                
                final List<String> langs = Arrays.asList(settings.getString("RMBT_SUPPORTED_LANGUAGES").split(",\\s*"));
                
                if (langs.contains(lang))
                {
                    errorList.setLanguage(lang);
                    labels = (PropertyResourceBundle) ResourceBundle.getBundle("at.alladin.rmbt.res.SystemMessages",
                            new Locale(lang));
                }
                else
                    lang = settings.getString("RMBT_DEFAULT_LANGUAGE");
                
//                System.out.println(request.toString(4));
                
                if (conn != null)
                {
                    final Client client = new Client(conn);
                    
                    if (request.optString("uuid").length() > 0
                            && client.getClientByUuid(UUID.fromString(request.getString("uuid"))) > 0)
                    {
                        
                        final Locale locale = new Locale(lang);
                        final Format format = new SignificantFormat(2, locale);
                        
                        String limitRequest = "";
                        if (request.optInt("result_limit", 0) != 0)
                        {
                            final int limit = request.getInt("result_limit");
                            limitRequest = " LIMIT " + limit;
                        }
                        
                        String deviceRequest = "";
                        if (request.optJSONArray("devices") != null)
                        {
                            
                            String checkUnknown = "";
                            String tmpString = request.getJSONArray("devices").toString();
                            tmpString = tmpString.substring(1, tmpString.length() - 1);
                            tmpString = tmpString.replace('\"', '\'');
                            
                            if (tmpString.indexOf("Unknown Device") > -1)
                                checkUnknown = " OR model IS NULL OR model = ''";
                            
                            deviceRequest = " AND ( model IN (" + tmpString + ")" + checkUnknown + ")";
//                            System.out.println(deviceRequest);
                            
                        }
                        
                        final ArrayList<String> filterValues = new ArrayList<String>();
                        String networksRequest = "";
                        
                        if (request.optJSONArray("networks") != null)
                        {
                            final JSONArray tmpArray = request.getJSONArray("networks");
                            final StringBuilder tmpString = new StringBuilder();
                            
                            if (tmpArray.length() >= 1)
                            {
                                tmpString.append("AND nt.group_name IN (");
                                boolean first = true;
                                for (int i = 0; i < tmpArray.length(); i++)
                                {
                                    if (first)
                                        first = false;
                                    else
                                        tmpString.append(',');
                                    tmpString.append('?');
                                    filterValues.add(tmpArray.getString(i));
                                }
                                tmpString.append(')');
                            }
                            networksRequest = tmpString.toString();
                        }
                        
                        final JSONArray historyList = new JSONArray();
                        
                        try
                        {
                            
                            final PreparedStatement st = conn
                                    .prepareStatement(String
                                            .format("SELECT DISTINCT"
                                                    + " t.uuid, time, timezone, speed_upload, speed_download, ping_shortest, network_type, nt.group_name network_type_group_name,"
                                                    + " COALESCE(adm.fullname, t.model) model"
                                                    + " FROM test t"
                                                    + " LEFT JOIN android_device_map adm ON adm.codename=t.model"
                                                    + " LEFT JOIN network_type nt ON t.network_type=nt.uid"
                                                    + " WHERE t.deleted = false AND t.status = 'FINISHED'"
                                                    + " AND (client_id = ? OR client_id IN (SELECT uid FROM client WHERE sync_group_id = ?))"
                                                    + " %s %s" + " ORDER BY time DESC" + " %s", deviceRequest,
                                                    networksRequest, limitRequest));
                            
                            int i = 1;
                            st.setLong(i++, client.getUid());
                            st.setInt(i++, client.getSync_group_id());
                            
                            for (final String filterValue : filterValues)
                                st.setString(i++, filterValue);
                            
//                            System.out.println(st.toString());
                            
                            final ResultSet rs = st.executeQuery();
                            
                            while (rs.next())
                            {
                                final JSONObject jsonItem = new JSONObject();
                                
                                jsonItem.put("test_uuid", rs.getString("uuid"));
                                
                                final Date date = rs.getTimestamp("time");
                                final long time = date.getTime();
                                final String tzString = rs.getString("timezone");
                                final TimeZone tz = TimeZone.getTimeZone(tzString);
                                jsonItem.put("time", time);
                                jsonItem.put("timezone", tzString);
                                final DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM,
                                        DateFormat.MEDIUM, locale);
                                dateFormat.setTimeZone(tz);
                                jsonItem.put("time_string", dateFormat.format(date));
                                
                                jsonItem.put("speed_upload", format.format(rs.getInt("speed_upload") / 1000d));
                                jsonItem.put("speed_download", format.format(rs.getInt("speed_download") / 1000d));
                                
                                final long pingShortest = rs.getLong("ping_shortest");
                                jsonItem.put("ping_shortest", format.format(pingShortest / 1000000d));
                                jsonItem.put("model", rs.getString("model"));
                                jsonItem.put("network_type", rs.getString("network_type_group_name"));
                                historyList.put(jsonItem);
                            }
                            
                            if (historyList.length() == 0)
                                errorList.addError("ERROR_DB_GET_HISTORY");
                            // errorList.addError(MessageFormat.format(labels.getString("ERROR_DB_GET_CLIENT"),
                            // new Object[] {uuid}));
                            
                            rs.close();
                            st.close();
                        }
                        catch (final SQLException e)
                        {
                            e.printStackTrace();
                            errorList.addError("ERROR_DB_GET_HISTORY_SQL");
                            // errorList.addError("ERROR_DB_GET_CLIENT_SQL");
                        }
                        
                        answer.put("history", historyList);
                    }
                    else
                        errorList.addError("ERROR_REQUEST_NO_UUID");
                    
                }
                else
                    errorList.addError("ERROR_DB_CONNECTION");
                
            }
            catch (final JSONException e)
            {
                errorList.addError("ERROR_REQUEST_JSON");
                System.out.println("Error parsing JSDON Data " + e.toString());
            }
        else
            errorList.addErrorString("Expected request is missing.");
        
        try
        {
            answer.putOpt("error", errorList.getList());
        }
        catch (final JSONException e)
        {
            System.out.println("Error saving ErrorList: " + e.toString());
        }
        
        answerString = answer.toString();
        
        return answerString;
    }
    
    @Get("json")
    public String retrieve(final String entity)
    {
        return request(entity);
    }
    
}