package com.tesilevorato.JavaMqttClient;

import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;


import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import org.ugeojson.model.feature.FeatureDto;
import org.ugeojson.model.geometry.PointDto;

import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.model.Datastream;
import de.fraunhofer.iosb.ilt.sta.model.FeatureOfInterest;
import de.fraunhofer.iosb.ilt.sta.model.Id;
import de.fraunhofer.iosb.ilt.sta.model.IdString;
import de.fraunhofer.iosb.ilt.sta.model.Location;
import de.fraunhofer.iosb.ilt.sta.model.Observation;
import de.fraunhofer.iosb.ilt.sta.model.ObservedProperty;
import de.fraunhofer.iosb.ilt.sta.model.Sensor;
import de.fraunhofer.iosb.ilt.sta.model.Thing;
import de.fraunhofer.iosb.ilt.sta.model.ext.EntityList;
import de.fraunhofer.iosb.ilt.sta.model.ext.UnitOfMeasurement;
import de.fraunhofer.iosb.ilt.sta.service.SensorThingsService;

public class ConnettoreDatasense {
  Connection conn = null;
  SensorThingsService service = null;
  public ConnettoreDatasense()  {
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
    int id = payloadjs.getInt("id");
    String sql = "SELECT ids FROM sensor WHERE id=" + id;
    PreparedStatement pstmt = conn.prepareStatement(sql);
    //pstmt.setDouble(1,id);      
    ResultSet rs = pstmt.executeQuery();
    //String marca = rs.getString("brand");
    int thingId = rs.getInt("ids");
    String content = payloadjs.getString("payload");
    int portId = payloadjs.getInt("portId");
    datasense(content, thingId, portId);
  }
  

  
  //#endregion

  private void CreateLocation(Thing temp_thing, Double lat, Double lon) throws ServiceFailureException {
    FeatureDto feature = new FeatureDto();
    PointDto point = new PointDto(lat, lon);
    feature.setGeometry(point);
    //feature.setProperties("{}");
    String featureGeo = "{'type':'Feature','geometry':{'type': 'Point','coordinates': [" + lat + "," + lon + "]}}"; //FeatureBuilder.getInstance().toGeoJSON(feature);//"{\"type\":\"Feature\",\"geometry\":{\"type\": \"Point\",\"coordinates\": [-114.06,51.05]}}"
    Location loc = new Location();
    Gson gson = new Gson();
    Object object = gson.fromJson(featureGeo, Object.class);
    loc.setLocation(object);
    loc.setEncodingType("application/vnd.geo+json");
    loc.setName(temp_thing.getName() + " position");
    loc.setDescription("Current position of " + temp_thing.getName());
    service.create(loc);
  }
  private Datastream CreateDatastream(Thing temp_thing, String name, String description, UnitOfMeasurement unitOfMeasurement, String observationType, Sensor sensor,ObservedProperty obsPro) {
      Datastream datastream = new Datastream();
      datastream.setName(name);
      datastream.setDescription(description);
      datastream.setUnitOfMeasurement(unitOfMeasurement);
      datastream.setObservationType(observationType);
      datastream.setThing(temp_thing);
      //IdString id=new IdString(name);
      //datastream.setId(id);
      datastream.setSensor(sensor);
      datastream.setObservedProperty(obsPro);
      try {
        service.create(datastream);
      } catch (ServiceFailureException e) {
        
        e.printStackTrace();
      }
      return datastream;
    }
  private ObservedProperty CreateObservedProperty(String name, String definition, String description){
    try{
      return service.observedProperties().query().filter("name eq '"+name+"'").first();
    } catch(ServiceFailureException e1){
      ObservedProperty obsPro=new ObservedProperty();
      obsPro.setName(name);
      obsPro.setDefinition(definition);
      obsPro.setDescription(description);
      //IdString id=new IdString(name);
      //obsPro.setId(id);
      try {
        service.create(obsPro);
      } catch (ServiceFailureException e2) {
      }
      return obsPro;
    }
  }
  private Sensor CreateSensor(String name, String description, String encodingType, Object metadata){
    Sensor sensor=new Sensor();
    sensor.setName(name);   
    sensor.setDescription(description);
    sensor.setMetadata(metadata);
    IdString id=new IdString("Datasense");
    sensor.setId(id);
    try {
      service.create(sensor);
    } catch (ServiceFailureException e) {
      e.printStackTrace();
    }
    return sensor;
}
  private void CreateObservation(Datastream datastream, Object value, FeatureOfInterest foi) throws ServiceFailureException {
    Observation obs = new Observation();
    obs.setResult(value);
    obs.setFeatureOfInterest(foi);
    datastream.observations().create(obs);
  }
  private FeatureOfInterest CreateFeatureOfInterest(String name, String description, Object feature) {
    FeatureOfInterest foi;
    try{
    foi=service.featuresOfInterest().query().filter("name eq '"+name+"'").first();
    if(foi!=null){
      return foi;
    }
    } catch (ServiceFailureException e1){
      System.out.println("Problema featureofinterest");
    }
    foi=new FeatureOfInterest();
    foi.setEncodingType("application/vnd.geo+json");
    foi.setDescription(description);
    foi.setName(name);
    foi.setFeature(feature);
    try {
      service.featuresOfInterest().create(foi);
    } catch (ServiceFailureException e) {
      e.printStackTrace();
    }
    return foi;
    }
  
  
  public void datasense(String payload, int thingId, int portId) throws JSONException, ServiceFailureException {
    //System.out.println(service.things().find((long)thingId));
    Thing thing= service.things().find((long)thingId);
    Sensor sensor=service.sensors().find(2L); //todo
    //Datastream datastreams=service.datastreams().query().filter("name eq 'SDI-12 Measurement 1'").first();
    // try {
    //    sensor=service.sensors().find("Datasense"); 
    // } catch (ServiceFailureException e) {
    //    sensor=CreateSensor("Datasense", "Sensore Datasense", "application/pdf","https://support.digitalmatter.com/helpdesk/attachments/16073699325" );
    // }
    // for(int i =1;i<6;i++){
    //   ObservedProperty obs=CreateObservedProperty("SDI-12 Measurement "+i, "SDI-12 Measurement "+i, "SDI-12 Measurement "+i);
    //   UnitOfMeasurement um=new UnitOfMeasurement();
    //   CreateDatastream(thing, "SDI-12 Measurement "+i, "SDI-12 Measurement "+i, um, "OM_Measurement", sensor, obs);
    // }
    int[] buffer;
    buffer = getHex(payload);
    int payload_length = buffer.length;
    int iterated_message_length = 0;
    int temp_msg_lenght = parseDataField(buffer, iterated_message_length, portId, thing, sensor);
    
    iterated_message_length += temp_msg_lenght;
    while (iterated_message_length < payload_length) {
      temp_msg_lenght = parseDataField(buffer, iterated_message_length, -1, thing, sensor);
      iterated_message_length += temp_msg_lenght + 1;
    }
  }

  private int parseDataField(int[] buffer, int index, int port_id, Thing thing, Sensor sensor) throws ServiceFailureException {
    //placeholder per features of interest
    String featureGeo2 = "{'type':'Feature','geometry':{'type': 'Point','coordinates': [0,0]}}"; //FeatureBuilder.getInstance().toGeoJSON(feature);//"{\"type\":\"Feature\",\"geometry\":{\"type\": \"Point\",\"coordinates\": [-114.06,51.05]}}"
    Gson gson2 = new Gson();
    Object placeholder = gson2.fromJson(featureGeo2, Object.class);
    /////////////////////////////////
    int id = buffer[index];
    int index_shift = 1;

    if (port_id != -1) {
      id = port_id;
      index_shift = 0;
    }

    switch (id) {
    case 0:
      return 0;

    case 1:
      return 4;

    case 2:
      return 0;

    case 3:
      return 4;

    case 10: {
      double[] lat_lon = parseGPSData(buffer, index + index_shift);
      CreateLocation(thing, lat_lon[0], lat_lon[1]);
      return 6;
    }
    case 20: {
      int value = parseBatteryVoltage(buffer, index + index_shift);
      ObservedProperty observedProperty=CreateObservedProperty("Battery Voltage", "Battery Voltage", "Battery Voltage");
      Datastream datastream=null;
      FeatureOfInterest foi=CreateFeatureOfInterest("Battery voltage", "Battery voltage", placeholder);
      try {
        datastream=service.datastreams().query().filter("name eq 'Battery Voltage'").first();
      } catch (ServiceFailureException e) {
        
      }
      if(datastream==null){
        UnitOfMeasurement unitOfMeasurement = new UnitOfMeasurement("milliVolt", "mV", "Voltage/1000");
        datastream = CreateDatastream(thing, "Battery Voltage", "description", unitOfMeasurement, "OM_CountObservation", sensor,observedProperty);
      }
        CreateObservation(datastream, value,foi);
      return 2;
    }

    case 21: {
      int value = parseAnalogIn(buffer, index + index_shift);
      ObservedProperty observedProperty=CreateObservedProperty("Analog In 1", "Analog In 1", "Analog In 1");
      Datastream datastream=null;
      FeatureOfInterest foi=CreateFeatureOfInterest("Analog In 1", "Analog In 1", placeholder);
      try {
        datastream=service.datastreams().query().filter("name eq 'Analog In 1'").first();
      } catch (ServiceFailureException e) {
      }
      if(datastream==null){
        UnitOfMeasurement unitOfMeasurement = new UnitOfMeasurement("milliVolt", "mV", "Voltage/1000");
        datastream = CreateDatastream(thing, "Analog In 1", "description", unitOfMeasurement, "OM_CountObservation",sensor,observedProperty);
      }
      CreateObservation(datastream, value,foi);
      return 2;
    }
    case 22: {
      int value = parseAnalogIn(buffer, index + index_shift);
      ObservedProperty observedProperty=CreateObservedProperty("Analog In 2", "Analog In 2", "Analog In 2");
      Datastream datastream=null;
      FeatureOfInterest foi=CreateFeatureOfInterest("Analog In 2", "Analog In 2", placeholder);
      try {
        datastream=service.datastreams().query().filter("name eq 'Analog In 2'").first();
      } catch (ServiceFailureException e) {
        
      }
      if(datastream==null){
        UnitOfMeasurement unitOfMeasurement = new UnitOfMeasurement("milliVolt", "mV", "Voltage/1000");
        datastream = CreateDatastream(thing, "Analog In 2", "description", unitOfMeasurement, "OM_CountObservation",sensor,observedProperty);
      }
      CreateObservation(datastream, value,foi);
      return 2;
    }

    case 23: {
      int value = parseAnalogIn(buffer, index + index_shift);
      ObservedProperty observedProperty=CreateObservedProperty("Analog In 3", "Analog In 3", "Analog In 3");
      Datastream datastream=null;
      FeatureOfInterest foi=CreateFeatureOfInterest("Analog In 3", "Analog In 3", placeholder);
      try {
        datastream=service.datastreams().query().filter("name eq 'Analog In 3'").first();
      } catch (ServiceFailureException e) {
        
      }
      if(datastream==null){
        UnitOfMeasurement unitOfMeasurement = new UnitOfMeasurement("milliVolt", "mV", "Voltage/1000");
        datastream = CreateDatastream(thing, "Analog In 3", "description", unitOfMeasurement, "OM_CountObservation",sensor,observedProperty);
      }
      CreateObservation(datastream, value,foi);
      return 2;
    }

    case 30:

      return 1;

    case 31:

      return 2;

    case 32:

      return 2;

    case 33:

      return 2;

    case 39:

      return 6;

    case 40: {
      double value = parseInternalTemperature(buffer, index + index_shift);
      ObservedProperty observedProperty=CreateObservedProperty("Internal Temperature", "Internal Temperature", "Internal Temperature");
      Datastream datastream=null;
      FeatureOfInterest foi=CreateFeatureOfInterest("Internal Temperature", "Internal Temperature", placeholder);
      try {
        datastream=service.datastreams().query().filter("name eq 'Internal Temperature'").first();
      } catch (ServiceFailureException e) {
        
      }
      if(datastream==null){
        UnitOfMeasurement unitOfMeasurement = new UnitOfMeasurement("Celsius", "°C", "Celsius Degree");
        datastream = CreateDatastream(thing, "Internal Temperature", "description", unitOfMeasurement, "OM_Measurement",sensor,observedProperty);
      }
      CreateObservation(datastream, value,foi);
      return 2;
    }
    case 41: {
      double value = parseI2CTempProbe(buffer, index + index_shift);
      ObservedProperty observedProperty=CreateObservedProperty("Digital Matter I2C Temperature Probe 1 (Red)", "Digital Matter I2C Temperature Probe 1 (Red)", "Digital Matter I2C Temperature Probe 1 (Red)");
      Datastream datastream=null;
      FeatureOfInterest foi=CreateFeatureOfInterest("Digital Matter I2C Temperature Probe 1 (Red)", "Digital Matter I2C Temperature Probe 1 (Red)", placeholder);
      try {
        datastream=service.datastreams().query().filter("name eq 'Digital Matter I2C Temperature Probe 1 (Red)'").first();
      } catch (ServiceFailureException e) {
        
      }
      if(datastream==null){
        UnitOfMeasurement unitOfMeasurement = new UnitOfMeasurement("Celsius", "°C", "Celsius Degree");
        datastream = CreateDatastream(thing, "Digital Matter I2C Temperature Probe 1 (Red)", "description", unitOfMeasurement, "OM_Measurement",sensor,observedProperty);
      }
      CreateObservation(datastream, value,foi);
      return 2;
    }
    case 42: {
      double value = parseI2CTempProbe(buffer, index + index_shift);
      ObservedProperty observedProperty=CreateObservedProperty("Digital Matter I2C Temperature Probe 2 (Blue)", "Digital Matter I2C Temperature Probe 2 (Blue)", "Digital Matter I2C Temperature Probe 2 (Blue)");
      Datastream datastream=null;
      FeatureOfInterest foi=CreateFeatureOfInterest("Digital Matter I2C Temperature Probe 2 (Blue)", "Digital Matter I2C Temperature Probe 2 (Blue)", placeholder);
      try {
        datastream=service.datastreams().query().filter("name eq 'Digital Matter I2C Temperature Probe 2 (Blue)'").first();
      } catch (ServiceFailureException e) {
        
      }
      if(datastream==null){
        UnitOfMeasurement unitOfMeasurement = new UnitOfMeasurement("Celsius", "°C", "Celsius Degree");
        datastream = CreateDatastream(thing, "Digital Matter I2C Temperature Probe 2 (Blue)", "description", unitOfMeasurement, "OM_Measurement",sensor,observedProperty);
      }
      CreateObservation(datastream, value,foi);
      return 2;
    }

    case 43: {
      double[] values = parseI2CTempRelativeHumidity(buffer, index + index_shift);
      ObservedProperty observedProperty=CreateObservedProperty("Digital Matter I2C Temperature", "Digital Matter I2C Temperature", "Digital Matter I2C Temperature");
      Datastream datastream=null;
      FeatureOfInterest foi=CreateFeatureOfInterest("Digital Matter I2C Temperature", "Digital Matter I2C Temperature", placeholder);
      try {
        datastream=service.datastreams().query().filter("name eq 'Digital Matter I2C Temperature'").first();
      } catch (ServiceFailureException e) {
      }
      if(datastream==null){
        UnitOfMeasurement unitOfMeasurement = new UnitOfMeasurement("Celsius", "°C", "Celsius Degree");
        datastream = CreateDatastream(thing, "Digital Matter I2C Temperature", "description", unitOfMeasurement, "OM_Measurement",sensor,observedProperty);
      
      }
      CreateObservation(datastream, values[0],foi); 
      foi=CreateFeatureOfInterest("Digital Matter I2C Relative Humidity", "Digital Matter I2C Relative Humidity", placeholder);  
      try {
        datastream=service.datastreams().query().filter("name eq 'Digital Matter I2C Relative Humidity'").first();
      } catch (ServiceFailureException e) {
        
      }
      if(datastream==null){
        UnitOfMeasurement unitOfMeasurement = new UnitOfMeasurement("Percentage", "%", "1/100");
        datastream = CreateDatastream(thing, "Digital Matter I2C Relative Humidity", "description", unitOfMeasurement, "OM_Measurement",sensor,observedProperty);
      }
      CreateObservation(datastream, values[0],foi);
      return 3;
    }

    case 50: {
      int value = parseBatteryEnergySincePower(buffer, index + index_shift);
      ObservedProperty observedProperty=CreateObservedProperty("Battery Energy Used Since Power Up", "Battery Energy Used Since Power Up", "Battery Energy Used Since Power Up");
      Datastream datastream=null;
      FeatureOfInterest foi=CreateFeatureOfInterest("Battery Energy Used Since Power Up", "Battery Energy Used Since Power Up", placeholder);
      try {
        datastream=service.datastreams().query().filter("name eq 'Battery Energy Used Since Power Up'").first();
      } catch (ServiceFailureException e) {
        
      }
      if(datastream==null){
        UnitOfMeasurement unitOfMeasurement = new UnitOfMeasurement("milli Ampere hour", "mAh", "Amper/(hour*1000)");
        datastream = CreateDatastream(thing, "Battery Energy Used Since Power Up", "description", unitOfMeasurement, "OM_Measurement",sensor,observedProperty);
      }
      CreateObservation(datastream, value,foi);
      return 2;
    }

    case 51: {
      int value = parseEstimatedBatteryRemaining(buffer, index + index_shift);
      ObservedProperty observedProperty=CreateObservedProperty("Estimated Battery % Remaining","Estimated Battery % Remaining","Estimated Battery % Remaining");
      Datastream datastream=null;
      FeatureOfInterest foi=CreateFeatureOfInterest("Estimated Battery % Remaining", "Estimated Battery % Remaining", placeholder);
      try {
        datastream=service.datastreams().query().filter("name eq 'Estimated Battery % Remaining'").first();
      } catch (ServiceFailureException e) {
        
      }
      if(datastream==null){
        UnitOfMeasurement unitOfMeasurement = new UnitOfMeasurement("Percentage", "%", "1/100");
        datastream = CreateDatastream(thing, "Estimated Battery % Remaining", "description", unitOfMeasurement, "OM_Measurement",sensor,observedProperty);
      }
      CreateObservation(datastream, value,foi);
      return 1;
    }

    case 128: {
      ArrayList < Double > values = parseSDIMeasurement(id, buffer, index + index_shift);
      //ObservedProperty observedProperty=CreateObservedProperty("SDI-12 Measurement 1","SDI-12 Measurement 1","SDI-12 Measurement 1");
      Datastream datastream=null;
      FeatureOfInterest foi=CreateFeatureOfInterest("SDI-12 Measurement 1", "SDI-12 Measurement 1", placeholder);
      try {
        datastream=service.datastreams().query().filter("name eq 'SDI-12 Measurement 1'").first();
        for (Double value: values) {
          CreateObservation(datastream, value,foi);
        }
      } catch (ServiceFailureException e) {
        System.out.println(("Datastream misurazione non presente!"));
      }

      return getSDISize(buffer, index + index_shift);
    }

    case 129:{
      ArrayList < Double > values = parseSDIMeasurement(id, buffer, index + index_shift);
      // ObservedProperty observedProperty=CreateObservedProperty("SDI-12 Measurement 1","SDI-12 Measurement 1","SDI-12 Measurement 1");
      Datastream datastream=null;
      FeatureOfInterest foi=CreateFeatureOfInterest("SDI-12 Measurement 1", "SDI-12 Measurement 1", placeholder);
      try {
        datastream=service.datastreams().query().filter("name eq 'SDI-12 Measurement 1'").first();
        for (Double value: values) {
          CreateObservation(datastream, value,foi);
        }
      } catch (ServiceFailureException e) {
        System.out.println(("Datastream misurazione non presente!"));
      }

      return getSDISize(buffer, index + index_shift);
    }

    case 130:
    {
      ArrayList < Double > values = parseSDIMeasurement(id, buffer, index + index_shift);
      // ObservedProperty observedProperty=CreateObservedProperty("SDI-12 Measurement 2","SDI-12 Measurement 2","SDI-12 Measurement 2");
      Datastream datastream=null;
      FeatureOfInterest foi=CreateFeatureOfInterest("SDI-12 Measurement 2", "SDI-12 Measurement 2", placeholder);
      try {
        datastream=service.datastreams().query().filter("name eq 'SDI-12 Measurement 2'").first();
        for (Double value: values) {
          CreateObservation(datastream, value,foi);
        }
      } catch (ServiceFailureException e) {
        System.out.println(("Datastream misurazione non presente!"));
      }

      return getSDISize(buffer, index + index_shift);
    }

    case 131:
    {
      ArrayList < Double > values = parseSDIMeasurement(id, buffer, index + index_shift);
      // ObservedProperty observedProperty=CreateObservedProperty("SDI-12 Measurement 2","SDI-12 Measurement 2","SDI-12 Measurement 2");
      Datastream datastream=null;
      FeatureOfInterest foi=CreateFeatureOfInterest("SDI-12 Measurement 2", "SDI-12 Measurement 2", placeholder);
      try {
        datastream=service.datastreams().query().filter("name eq 'SDI-12 Measurement 2'").first();
        for (Double value: values) {
          CreateObservation(datastream, value,foi);
        }
      } catch (ServiceFailureException e) {
        System.out.println(("Datastream misurazione non presente!"));
      }

      return getSDISize(buffer, index + index_shift);
    }

    case 132:
    {
      ArrayList < Double > values = parseSDIMeasurement(id, buffer, index + index_shift);
      // ObservedProperty observedProperty=CreateObservedProperty("SDI-12 Measurement 3","SDI-12 Measurement 3","SDI-12 Measurement 3");
      Datastream datastream=null;
      FeatureOfInterest foi=CreateFeatureOfInterest("SDI-12 Measurement 3", "SDI-12 Measurement 3", placeholder);
      try {
        datastream=service.datastreams().query().filter("name eq 'SDI-12 Measurement 3'").first();
        for (Double value: values) {
          CreateObservation(datastream, value,foi);
        }
      } catch (ServiceFailureException e) {
        System.out.println(("Datastream misurazione non presente!"));
      }

      return getSDISize(buffer, index + index_shift);
    }

    case 133:
    {
      ArrayList < Double > values = parseSDIMeasurement(id, buffer, index + index_shift);
      // ObservedProperty observedProperty=CreateObservedProperty("SDI-12 Measurement 3","SDI-12 Measurement 3","SDI-12 Measurement 3");
      Datastream datastream=null;
      FeatureOfInterest foi=CreateFeatureOfInterest("SDI-12 Measurement 3", "SDI-12 Measurement 3", placeholder);
      try {
        datastream=service.datastreams().query().filter("name eq 'SDI-12 Measurement 3'").first();
        for (Double value: values) {
          CreateObservation(datastream, value,foi);
        }
      } catch (ServiceFailureException e) {
        System.out.println(("Datastream misurazione non presente!"));
      }

      return getSDISize(buffer, index + index_shift);
    }

    case 134:
    {
      ArrayList < Double > values = parseSDIMeasurement(id, buffer, index + index_shift);
      // ObservedProperty observedProperty=CreateObservedProperty("SDI-12 Measurement 4","SDI-12 Measurement 4","SDI-12 Measurement 4");
      Datastream datastream=null;
      FeatureOfInterest foi=CreateFeatureOfInterest("SDI-12 Measurement 4", "SDI-12 Measurement 4", placeholder);
      try {
        datastream=service.datastreams().query().filter("name eq 'SDI-12 Measurement 4'").first();
        for (Double value: values) {
          CreateObservation(datastream, value,foi);
        }
      } catch (ServiceFailureException e) {
        System.out.println(("Datastream misurazione non presente!"));
      }

      return getSDISize(buffer, index + index_shift);
    }

    case 135:
    {
      ArrayList < Double > values = parseSDIMeasurement(id, buffer, index + index_shift);
      // ObservedProperty observedProperty=CreateObservedProperty("SDI-12 Measurement 4","SDI-12 Measurement 4","SDI-12 Measurement 4");
      Datastream datastream=null;
      FeatureOfInterest foi=CreateFeatureOfInterest("SDI-12 Measurement 4", "SDI-12 Measurement 4", placeholder);
      try {
        datastream=service.datastreams().query().filter("name eq 'SDI-12 Measurement 4'").first();
        for (Double value: values) {
          CreateObservation(datastream, value,foi);
        }
      } catch (ServiceFailureException e) {
        System.out.println(("Datastream misurazione non presente!"));
      }

      return getSDISize(buffer, index + index_shift);
    }

    case 136:
    {
      ArrayList < Double > values = parseSDIMeasurement(id, buffer, index + index_shift);
      // ObservedProperty observedProperty=CreateObservedProperty("SDI-12 Measurement 5","SDI-12 Measurement 5","SDI-12 Measurement 5");
      Datastream datastream=null;
      FeatureOfInterest foi=CreateFeatureOfInterest("SDI-12 Measurement 5", "SDI-12 Measurement 5", placeholder);
      try {
        datastream=service.datastreams().query().filter("name eq 'SDI-12 Measurement 5'").first();
        for (Double value: values) {
          CreateObservation(datastream, value,foi);
        }
      } catch (ServiceFailureException e) {
        System.out.println(("Datastream misurazione non presente!"));
      }

      return getSDISize(buffer, index + index_shift);
    }

    case 137:
    {
      ArrayList < Double > values = parseSDIMeasurement(id, buffer, index + index_shift);
      // ObservedProperty observedProperty=CreateObservedProperty("SDI-12 Measurement 5","SDI-12 Measurement 5","SDI-12 Measurement 5");
      Datastream datastream=null;
      FeatureOfInterest foi=CreateFeatureOfInterest("SDI-12 Measurement 5", "SDI-12 Measurement 5", placeholder);
      try {
        datastream=service.datastreams().query().filter("name eq 'SDI-12 Measurement 5'").first();
        for (Double value: values) {
          CreateObservation(datastream, value,foi);
        }
      } catch (ServiceFailureException e) {
        System.out.println(("Datastream misurazione non presente!"));
      }

      return getSDISize(buffer, index + index_shift);
    }

    case 223:
      return 0;

    default:
      return 0;
    }
  }
  private int getSDISize(int[] buffer, int index) {
    int num_samples = buffer[index] & 0x0f;
    int data_type_id = buffer[index] >> 4;
    int size = 0;

    switch (data_type_id) {
    case 0: //Soil moisture
      size = num_samples + 1;
      return size;

    case 1: // Temperature
      size = num_samples + 1;
      return size;

    case 2: //INT16
      size = num_samples * 2 + 1;
      return size;

    case 3: //INT32
      size = num_samples * 4 + 1;
      return size;

    case 4: //INT12
      size = (int) Math.ceil(num_samples * 1.5) + 1;
      return size;

    default:
      size = 0 + 1;
      return size;
    }
  }

  private int parseBatteryVoltage(int[] buffer, int index) {
    return parseLittleEndianUInt16(buffer, index);
  }
  private int parseAnalogIn(int[] buffer, int index) {
    return parseLittleEndianUInt16(buffer, index);
  }
  private double parseInternalTemperature(int[] buffer, int index) {
    double internal_temperature = (double)parseLittleEndianInt16(buffer, index) / 100;
    return internal_temperature;
  }
  private double parseI2CTempProbe(int[] buffer, int index) {
    double temp_probe = parseLittleEndianInt16(buffer, index) / 100;
    return temp_probe;
  }
  private double[] parseI2CTempRelativeHumidity(int[] buffer, int index) {
    double[] values = new double[2];
    values[0] = parseLittleEndianInt16(buffer, index) / 100;
    values[1] = buffer[index + 2] / 2;

    return values;
  }
  private int parseBatteryEnergySincePower(int[] buffer, int index) {
    int battery_energy_used = parseLittleEndianInt16(buffer, index);

    return battery_energy_used;
  }
  private int parseEstimatedBatteryRemaining(int[] buffer, int index) {
    int estimated_battery_remaining = parseLittleEndianInt16(buffer, index);

    return estimated_battery_remaining;
  }

  

  long parseLittleEndianInt32(int[] buffer, int offset) {
    long result = (buffer[offset + 3] << 24) + (buffer[offset + 2] << 16) + (buffer[offset + 1] << 8) + buffer[offset];

    if ((result & 0x80000000) > 0)

      result = result - 0x100000000L;

    return result;
  }
  int parseLittleEndianInt24(int[] buffer, int offset) {
    int result = (buffer[offset + 2] << 16) + (buffer[offset + 1] << 8) + buffer[offset];

    if ((result & 0x800000) > 0)
      result = result - 0x1000000;

    return result;
  }
  int parseLittleEndianInt16(int[] buffer, int offset) {
    int result = (buffer[offset + 1] << 8) + buffer[offset];

    if ((result & 0x8000) > 0)
      result = result - 0x10000;

    return result;
  }
  int parseLittleEndianUInt16(int[] buffer, int offset) {
    int result = (buffer[offset + 1] << 8) + buffer[offset];

    return result;
  }
  ArrayList < Double > parseSDIMeasurement(int id, int[] buffer, int index) {
    int num_samples = buffer[index] & 0x0f;
    int data_type_id = buffer[index] >> 4;
    ArrayList < Double > data;

    switch (data_type_id) {
    case 0:
      //Soil moisture
      data = parseSDISoilMoistureData(buffer, index, num_samples);
      return data;

    case 1: //Temperature
      data = parseSDITempData(buffer, index, num_samples);
      return data;

    case 2: //INT16
      data = parseSDIINT16Data(buffer, index, num_samples);
      return data;

    case 3: //INT32
      data = parseSDIINT32Data(buffer, index, num_samples);
      return data;

    case 4: //INT12
      data = parseSDIINT12Data(buffer, index, num_samples);
      return data;

    default:
      return null;
    }
  }
  ArrayList < Double > parseSDISoilMoistureData(int[] buffer, int index, int num_samples) {
    ArrayList < Double > data = new ArrayList < Double > ();

    for (int i = 0; i < num_samples; i++) {
      data.add((double) buffer[index + i + 1] / 2 - 5);
    }

    return data;
  }

  ArrayList < Double > parseSDITempData(int[] buffer, int index, int num_samples) {
    ArrayList < Double > data = new ArrayList < Double > ();

    for (int i = 0; i < num_samples; i++) {
      data.add((double) buffer[index + i + 1] / 2 - 40);
    }

    return data;
  }

  ArrayList < Double > parseSDIINT16Data(int[] buffer, int index, int num_samples) {
    ArrayList < Double > data = new ArrayList < Double > ();

    for (int i = 0; i < num_samples; i++) {
      data.add((double) parseLittleEndianInt16(buffer, index + i * 2 + 1) / 100);
    }

    return data;
  }

  ArrayList < Double > parseSDIINT32Data(int[] buffer, int index, int num_samples) {
    ArrayList < Double > data = new ArrayList < Double > ();

    for (int i = 0; i < num_samples; i++) {
      data.add((double) parseLittleEndianInt32(buffer, index + i * 4 + 1) / 1000);
    }

    return data;
  }

  ArrayList < Double > parseSDIINT12Data(int[] buffer, int index, int num_samples) {
    ArrayList < Double > data = new ArrayList < Double > ();

    for (int i = 0; i < num_samples; i++) {
      int rawVal = 0;
      int twiceOffset = i * 3;
      if ((twiceOffset & 1) > 0) {
        rawVal =
          ((buffer[index + 1 + (twiceOffset - 1) / 2] & 0xf) << 8) +
          buffer[index + 1 + (twiceOffset + 1) / 2];
      } else {
        rawVal =
          (buffer[index + 1 + twiceOffset / 2] << 4) +
          (buffer[index + 1 + twiceOffset / 2 + 1] >> 4);
      }
      data.add((double) rawVal / 20 - 50);
    }

    return data;
  }
  int[] getHex(String value) {

    int num_bytes = value.length() / 2;
    int[] bytes = new int[num_bytes];

    for (int i = 0; i < num_bytes; i++) {
      bytes[i] = Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
    }
    return bytes;
  }
  double[] parseGPSData(int[] buffer, int index) {

    double[] lat_lon = new double[2];
    double latitude = 0.0000256 * parseLittleEndianInt24(buffer, index);
    double longitude = 0.0000256 * parseLittleEndianInt24(buffer, index + 3);

    return lat_lon;
  }
}