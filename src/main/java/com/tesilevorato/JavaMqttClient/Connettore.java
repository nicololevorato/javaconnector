package com.tesilevorato.JavaMqttClient;


import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import java.util.List;

import com.google.gson.Gson;



import org.json.JSONException;
import org.json.JSONObject;


import org.ugeojson.model.feature.FeatureDto;

import org.ugeojson.model.geometry.PointDto;

import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.model.Location;
import de.fraunhofer.iosb.ilt.sta.model.Observation;
import de.fraunhofer.iosb.ilt.sta.model.Thing;
import de.fraunhofer.iosb.ilt.sta.service.SensorThingsService;


public class Connettore {
    int id;
    JSONObject payload;
    String content;
    Connection conn = null;
    String marca;
    SensorThingsService service=null;
    int thingId=-1;
    public Connettore(){
        //connsessione al servizio SensorThings
        URL serviceEndpoint;       
        try {
            serviceEndpoint = new URL("http://localhost:8080/FROST-Server/v1.0");
            service = new SensorThingsService(serviceEndpoint);
            System.out.println("Connesso al servizio sensorthings");
        } catch (MalformedURLException e1) {
            
            e1.printStackTrace();
        }
        //connessione db sql locale
       
        try {
            // db parameters
            String url = "jdbc:sqlite:C:/Universita/Materiale Tesi/workspace/Connettorejava/javaconnector/db/idsensor.db";
            // create a connection to the database
            conn = DriverManager.getConnection(url);
            
            System.out.println("Connection to SQLite has been established.");
            
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        //  finally {
        //     try {
        //         if (conn != null) {
        //             conn.close();
        //         }
        //     } catch (SQLException ex) {
        //         System.out.println(ex.getMessage());
        //     }
        
        // Thing thing = ThingBuilder.builder()
        // .name("Thingything")
        // .description("I'm a thing!")
        // .build();
        // try {
        //     service.create(thing);
        // } catch (ServiceFailureException e) {
        //     e.printStackTrace();
        // }
        }
    
    public void translate(String payload) throws JSONException, ServiceFailureException, SQLException{
        this.payload= new JSONObject(payload);
        this.id=this.payload.getInt("id");       
        String sql = "SELECT ids, brand FROM sensor WHERE id=" + this.id;        
        PreparedStatement pstmt  = conn.prepareStatement(sql);    
        //pstmt.setDouble(1,id);      
        ResultSet rs  = pstmt.executeQuery();         
        marca =rs.getString("brand");
        thingId=rs.getInt("ids");
        this.content=this.payload.getString("payload");
         
        switch (marca) {
            case "Datasense":
                    datasense(this.content,thingId);          
                break;
            case "Libellium":
                libellium(this.content);        
            break;
            default:
                break;
        }
    }
    private void libellium(String payload) throws SQLException {
        // String sql = "SELECT * FROM libelliumOBS WHERE id=" + thingId;        
        // PreparedStatement pstmt  = conn.prepareStatement(sql);         
        // ResultSet rs  = pstmt.executeQuery();
        //nell'header in posizione del terzo byte è presente l'informazione sulla tipologia di frame
        Charset charset = Charset.forName("ASCII");
        byte[] byteArrray = payload.getBytes(charset);
        if(byteArrray.length>2){
            int temp=(int)byteArrray[3] & 128;
            if(temp==128){
                libelliumASCII(payload);
            }
            else{
                libelliumBINARY(payload);
            }
        }



    }

    private void libelliumBINARY(String payload2) {
    }

    private void libelliumASCII(String payload2) {
    }

    public void print(){
        System.out.println("Ricevuto payload con marca :"+marca);
    }
    public void datasense(String payload, int thingId) throws ServiceFailureException, JSONException{
        int index =0;
        while (index < payload.length()) {
            String dataField = payload.substring(index,index + 2);
            index += 2;
            int content=Integer.parseInt(dataField, 16);
            switch (content) {
                case 10:               
                String latS = payload.substring(index, index + 3);
                Double lat = Integer.parseInt(latS, 16) * 256 * Math.pow(10, -7);
                System.out.println(lat);
                index += 3;
                String lonS = payload.substring(index, index + 3);
                Double lon = Integer.parseInt(lonS, 16) * 256 * Math.pow(10, -7);
                System.out.println(lon);
                index += 3;
                lat=-114.06;
                lon=51.05;
                FeatureDto feature = new FeatureDto();
                PointDto point = new PointDto(lat,lon);
                feature.setGeometry(point);		
                //feature.setProperties("{}");
                String featureGeo = "{'type':'Feature','geometry':{'type': 'Point','coordinates': ["+lat+","+lon+"]}}";//FeatureBuilder.getInstance().toGeoJSON(feature);//"{\"type\":\"Feature\",\"geometry\":{\"type\": \"Point\",\"coordinates\": [-114.06,51.05]}}"
                Location loc=new Location();
                Thing thing = service.things().find(thingId);
                Gson gson = new Gson();
	            Object object = gson.fromJson(featureGeo, Object.class);
                loc.setLocation(object);
                loc.setEncodingType("application/vnd.geo+json");
                loc.setName(thing.getName()+" position");
                loc.setDescription("Current position of "+thing.getName());
                
                //List<Thing> thingsList=new ArrayList<>();
                //thingsList.add(thing);
                //loc.setThings(thingsList);
                service.create(loc);
                break;
                case 128:
                int obsNumber=1;
                String nDataS = payload.substring(index, index + 1);
                index++;
                String dataTypeS = payload.substring(index, index + 1);
                index++;
                int nData=Integer.parseInt(nDataS, 16);
                int dataType=Integer.parseInt(dataTypeS, 16);
                String dataPayload=null;
                if(dataType==0 || dataType==1){
                    dataPayload=payload.substring(index, index + nData);
                    index+=nData;
                }
                else if(dataType==2){
                    dataPayload=payload.substring(index, index + nData*2);
                    index+=nData*2;
                }
                else if(dataType==3){
                    dataPayload=payload.substring(index, index + nData*4);
                    index+=nData*4;
                }
                else if(dataType==4){
                    nData=(int)Math.ceil(nData*1.5);
                    dataPayload=payload.substring(index, index + nData);
                    int i = Integer.parseInt(dataPayload, 16);
                    dataPayload = Integer.toBinaryString(i);                    
                }
                convertDataDatasense(dataType,dataPayload,obsNumber);
                break;
                default:
                    break;
            }
          }
    }

    private void convertDataDatasense(int dataType, String dataPayload,int obsNumber) throws ServiceFailureException {
        List<Double> observations=new ArrayList<>();
        switch (dataType) {
            case 0:
            for (String valueS: dataPayload.split("")) {
                Double value =Integer.parseInt(valueS,16) * 0.5 - 5;
                observations.add(value);
            }
            break;
            case 1:
            for (String valueS: dataPayload.split("")) {
                Double value =Integer.parseInt(valueS,16) * 0.5 - 40;
                observations.add(value);
            }               
            break;
            case 2:
            for (String valueS: dataPayload.split("(?<=\\G.{2})")) {
                Double value = (short)Integer.parseInt(valueS,16) * 0.01;
                observations.add(value);
            }   
            break;
            case 3:
            for (String valueS: dataPayload.split("(?<=\\G.{4})")) {
                Double value = Long.parseLong(valueS,16) * 0.001;
                observations.add(value);
            } 
            break;
            case 4:
            for (String valueS: dataPayload.split("(?<=\\G.{12})")) {
                Double value = Integer.parseInt(valueS,2)*0.05-20;
                observations.add(value);
            }
            break;
        
            default:
                break;
            
        }
        Thing thing = service.things().find(thingId);
        for (Double value : observations) {
            Observation observation=new Observation();
            observation.setResult(value);
            observation.setDatastream(thing.datastreams().find(obsNumber));
            service.create(observation);
        }
    }
}
