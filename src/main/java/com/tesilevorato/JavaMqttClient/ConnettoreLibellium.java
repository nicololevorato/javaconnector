package com.tesilevorato.JavaMqttClient;

import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

import com.google.gson.Gson;
import com.tesilevorato.ConnettoreUtility;

import org.json.JSONException;
import org.json.JSONObject;

import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.model.Datastream;
import de.fraunhofer.iosb.ilt.sta.model.FeatureOfInterest;
import de.fraunhofer.iosb.ilt.sta.model.ObservedProperty;
import de.fraunhofer.iosb.ilt.sta.model.Sensor;
import de.fraunhofer.iosb.ilt.sta.model.Thing;
import de.fraunhofer.iosb.ilt.sta.model.ext.UnitOfMeasurement;
import de.fraunhofer.iosb.ilt.sta.service.SensorThingsService;

public class ConnettoreLibellium {
    ConnettoreUtility utility;
    Connection conn;
    SensorThingsService service;
    public ConnettoreLibellium(){
        utility=new ConnettoreUtility();
        service=utility.ConnectionSensorThings();
        try {
            // db parameters
            String url = "jdbc:sqlite:javaconnector/db/idsensor.db";
            // create a connection to the database
            conn = DriverManager.getConnection(url);
      
            System.out.println("Connection to SQLite has been established.");
      
          } catch (SQLException e) {
            System.out.println(e.getMessage());
          }
    }
    public void translate(String payload) throws JSONException, SQLException, ServiceFailureException{
        JSONObject payloadjs = new JSONObject(payload);
        // int id = payloadjs.getInt("id");
        // String sql = "SELECT ids FROM sensor WHERE id=" + id;
        // PreparedStatement pstmt = conn.prepareStatement(sql);
        // //pstmt.setDouble(1,id);      
        // ResultSet rs = pstmt.executeQuery();
        //String marca = rs.getString("brand");
        String content = payloadjs.getString("payload");
        libellium(content);
      }
    private void libellium(String payload) throws SQLException, ServiceFailureException, JSONException {
        // String sql = "SELECT * FROM libelliumOBS WHERE id=" + thingId;        
        // PreparedStatement pstmt  = conn.prepareStatement(sql);         
        // ResultSet rs  = pstmt.executeQuery();
        JSONObject payloadjs = new JSONObject(payload);
        String content=payloadjs.getString("payload");
        //nell'header in posizione del terzo byte è presente l'informazione sulla tipologia di frame
        int[] byteArrray=utility.getHex(content);
        if (byteArrray.length > 2) {
          //controllo primo bit
          if ((byteArrray[3] & 0x10000000) == 0x10000000) {
            StringBuilder output = new StringBuilder("");
            for (int i = 0; i < content.length(); i += 2) {
            String str = content.substring(i, i + 2);
            output.append((char) Integer.parseInt(str, 16));
            }
            libelliumASCII(output.toString());
          } else {
            libelliumBINARY(byteArrray);
          }
        }
    }
        private void libelliumBINARY(int[] payload) {
            // String serialIDs = payload.substring(10, 13);
            // int serialID = Integer.parseInt(serialIDs, 16);
            // String[] temp_fields = payload.split("(?<=\\G.{2})");
        
          }
          //#region parser dataframe ASCII libellium
          private void libelliumASCII(String payload) throws SQLException, ServiceFailureException {
            String[] temp_fields = payload.split("#");
            int serial_id = Integer.parseInt(temp_fields[1]);
            String sql_thing = "SELECT THINGID from libelliumID2THING WHERE SERIALID=" + serial_id;
            PreparedStatement pstmt_thing = conn.prepareStatement(sql_thing);
            ResultSet rs_thing = pstmt_thing.executeQuery();
            int thingID = rs_thing.getInt("THINGID");
            String featureGeo2 = "{'type':'Feature','geometry':{'type': 'Point','coordinates': [0,0]}}"; //FeatureBuilder.getInstance().toGeoJSON(feature);//"{\"type\":\"Feature\",\"geometry\":{\"type\": \"Point\",\"coordinates\": [-114.06,51.05]}}"
            Gson gson2 = new Gson();
            Object placeholder = gson2.fromJson(featureGeo2, Object.class);//dummy placeholder per le feature of interest
            Thing temp_thing = service.things().find((long)thingID);
            Sensor sensor=service.sensors().find(2L);
            String[] fields = Arrays.copyOfRange(temp_fields, 4, temp_fields.length - 1);
            for (String field: fields) {
              String[] splitted_field = field.split(":");
              if (splitted_field[0].toUpperCase() == "GPS") {
                String[] tempValue = splitted_field[1].split(";");
                Double lat = Double.parseDouble(tempValue[0]);
                Double lon = Double.parseDouble(tempValue[1]);
                utility.CreateLocation(temp_thing, lat, lon);
              } else {
                String sql_um = "SELECT * FROM libelliumUM WHERE ASCII=" + splitted_field[0].toUpperCase();
                PreparedStatement pstmt_um = conn.prepareStatement(sql_um);
                ResultSet rs_um = pstmt_um.executeQuery();
                String sensor_tag = rs_um.getString("sensor tag");
                String dataName = rs_um.getString("sensor");
                Datastream datastream=service.datastreams().query().filter("name eq '"+sensor+"'").first();
                if(datastream==null){
                    ObservedProperty obsPro=utility.CreateObservedProperty(sensor_tag, rs_um.getString("ObservedPorperty Definition"), rs_um.getString("ObservedPorperty Description"));
                    UnitOfMeasurement um=new UnitOfMeasurement(rs_um.getString("unit"), rs_um.getString("unit symbol"), rs_um.getString("unit definition"));
                    String obsType=rs_um.getString("Datastream Description");
                    String OMtype;
                    switch (obsType) {
                        case "float":
                            OMtype="OM_Measurement";
                            break;
                        case "uint8_t":
                            OMtype="OM_CountObservation";
                            break;
                        case "int":
                            OMtype="OM_CountObservation";
                            break;
                        case "string":
                            OMtype="OM_Observation";
                            break;
                        case "ulong":
                            OMtype="OM_Measurement";
                            break;
                        default:
                            OMtype="OM_Observation";
                            break;
                    }
                    datastream=utility.CreateDatastream(temp_thing, dataName, rs_um.getString("Datastream Description"), um,OMtype, sensor, obsPro);
                }
                FeatureOfInterest foi=utility.CreateFeatureOfInterest(rs_um.getString("FeaureOfInterest Name"), rs_um.getString("FeaureOfInterest Description"), placeholder);
                String[] tempValue = splitted_field[1].split(";");
                switch (datastream.getObservationType()) {
                    case "OM_Measurement":
                        for (String string : tempValue) {
                            double value = Double.parseDouble(string);
                            utility.CreateObservation(datastream, value, foi);
                        }
                    break;
                    case "OM_CountObservation":
                    for (String string : tempValue) {
                        int value = Integer.parseInt(string);
                        utility.CreateObservation(datastream, value, foi);
                    }
                    break;
                    case "OM_Observation":
                    for (String string : tempValue) {
                        utility.CreateObservation(datastream, string, foi);
                    }
                    break;
                
                    default:
                        break;
                }
              }
            }
        
          }
      }

