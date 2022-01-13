package com.tesilevorato.JavaMqttClient;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;

import com.google.gson.Gson;

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
        if (byteArrray.length > 0) {
          //controllo primo bit
          if ((byteArrray[3] & 0x80000000) > 0 ) {
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
        private void libelliumBINARY(int[] buffer) throws ServiceFailureException, SQLException {
            // String serialIDs = payload.substring(10, 13);
            // int serialID = Integer.parseInt(serialIDs, 16);
            // String[] temp_fields = payload.split("(?<=\\G.{2})");
            int payload_length=buffer.length;
            int iterated_message_length=5;
            int serial_id=utility.parseLittleEndianInt16(buffer, iterated_message_length);
            iterated_message_length+=4;
            String sql_thing = "SELECT THINGID from libelliumID2THING WHERE SERIALID=" + serial_id;
            PreparedStatement pstmt_thing = conn.prepareStatement(sql_thing);
            ResultSet rs_thing = pstmt_thing.executeQuery();
            int thingID = rs_thing.getInt("THINGID");
            Thing temp_thing = service.things().find((long)thingID);
            Sensor sensor=service.sensors().find(2L);
            //#=35
            while(buffer[iterated_message_length]!=35){//si cerca la fine della stringa segnata da #
                iterated_message_length++;
            }         
            while(iterated_message_length<payload_length){
                iterated_message_length++;//salto al byte successivo dove iniziano le misurazioni
                int temp_msg_lenght = parseDataFieldLibelliumArray(buffer, iterated_message_length, temp_thing, sensor);
                iterated_message_length+=temp_msg_lenght;
            }
          }
          private int parseDataFieldLibelliumArray(int[] buffer, int iterated_message_length, Thing temp_thing,Sensor sensor) throws SQLException, ServiceFailureException {
            int sensor_id_binary=buffer[iterated_message_length];
            if(sensor_id_binary==53){//GPS
                double lat=utility.parseLittleEndianFloat(buffer, iterated_message_length++);
                iterated_message_length+=4;
                double lon=utility.parseLittleEndianFloat(buffer, iterated_message_length++);
                utility.CreateLocation(temp_thing, lat, lon);
                return 9;
            }
            iterated_message_length++;//si passa ai valori
            String sql_thing = "SELECT * from libelliumUM WHERE binary=" + sensor_id_binary;
            PreparedStatement pstmt_UM = conn.prepareStatement(sql_thing);
            ResultSet rs_UM = pstmt_UM.executeQuery();
            String datastream_name=rs_UM.getString("sensor");
            String type=rs_UM.getString("type");
            Datastream datastream=temp_thing.datastreams().query().filter("name eq '"+datastream_name+"'").first();
            if(datastream==null){
                ObservedProperty obsPro=utility.CreateObservedProperty(datastream_name, rs_UM.getString("ObservedProperty definition"), rs_UM.getString("ObservedProperty Description"));
                UnitOfMeasurement um=new UnitOfMeasurement(rs_UM.getString("unit"), rs_UM.getString("unit symbol"),rs_UM.getString("unit definition"));
                String OMobs=OMfromType(type);
                utility.CreateDatastream(temp_thing, datastream_name, rs_UM.getString("Datastream description"), um, OMobs, sensor, obsPro);
            }
            String featureGeo2 = "{'type':'Feature','geometry':{'type': 'Point','coordinates': [0,0]}}"; //FeatureBuilder.getInstance().toGeoJSON(feature);//"{\"type\":\"Feature\",\"geometry\":{\"type\": \"Point\",\"coordinates\": [-114.06,51.05]}}"
            Gson gson2 = new Gson();
            Object placeholder = gson2.fromJson(featureGeo2, Object.class);//dummy placeholder per le feature of interest
            FeatureOfInterest foi=utility.CreateFeatureOfInterest(rs_UM.getString("FeaureOfInterest Name"), rs_UM.getString("FeaureOfInterest Description"), placeholder);           
            int offset=0;           
            //todo string
            if(type=="string"){
            ArrayList<Character> values=new ArrayList<Character>();
            while(buffer[iterated_message_length+offset]!=53){
                values.add(utility.parseChar(buffer, iterated_message_length+offset));
                offset++;
                }
            StringBuilder builder = new StringBuilder(values.size());
            for(Character ch: values)
            {
                builder.append(ch);
            }
            utility.CreateObservation(datastream, builder.toString(), foi);
            }
            ////////////////////////////////////////////////////////////
            else{
            ArrayList<Double> values=new ArrayList<Double>();
            int fields=rs_UM.getInt("n fields");
            for(int i=0;i<fields;i++){
                switch (type) {
                case "float":
                    values.add((double)utility.parseLittleEndianFloat(buffer, iterated_message_length + (i*4)));
                    offset+=4;
                case "uint8_t":
                    values.add((double)utility.parseLittleEndianUInt8(buffer, offset));
                    offset++;
                    
                case "int":
                    values.add((double)utility.parseLittleEndianInt16(buffer, offset));
                    offset+=2;
                    
                case "ulong":
                    values.add((double)utility.parseLittleEndianUInt32(buffer, offset));
                    offset+=4;
                default:
                    
            }
        }
        if(values.size()<2){
            utility.CreateObservation(datastream, values.toArray()[0], foi);
        }else{
            utility.CreateObservation(datastream, values.toString(), foi);
        }
        }
        
        return offset;
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
                String datastream_name = rs_um.getString("sensor");
                Datastream datastream=service.datastreams().query().filter("name eq '"+datastream_name+"'").first();
                if(datastream==null){
                    ObservedProperty obsPro=utility.CreateObservedProperty(sensor_tag, rs_um.getString("ObservedPorperty Definition"), rs_um.getString("ObservedPorperty Description"));
                    UnitOfMeasurement um=new UnitOfMeasurement(rs_um.getString("unit"), rs_um.getString("unit symbol"), rs_um.getString("unit definition"));
                    String obsType=rs_um.getString("type");
                    String OMtype=OMfromType(obsType);
                    datastream=utility.CreateDatastream(temp_thing, datastream_name, rs_um.getString("Datastream Description"), um,OMtype, sensor, obsPro);
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
          private static String OMfromType(String obsType){
            switch (obsType) {
                case "float":
                    return "OM_Measurement";
                case "uint8_t":
                    return "OM_CountObservation";
                case "int":
                    return "OM_CountObservation";
                case "string":
                    return "OM_Observation";
                case "ulong":
                    return "OM_Measurement";
                default:
                    return "OM_Observation";
          }
      }
    }

