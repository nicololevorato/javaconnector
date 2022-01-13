package com.tesilevorato.JavaMqttClient;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
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

public class ConnettoreDatasense {
  SensorThingsService service;
  ConnettoreUtility utility;
  Connection conn;
  public ConnettoreDatasense()  {
    utility=new ConnettoreUtility();
    service=utility.ConnectionSensorThings();
    System.out.println("Connesso al servizio sensorthings");
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
    buffer = utility.getHex(payload);
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
      utility.CreateLocation(thing, lat_lon[0], lat_lon[1]);
      return 6;
    }
    case 20: {
      int value = parseBatteryVoltage(buffer, index + index_shift);
      ObservedProperty observedProperty=utility.CreateObservedProperty("Battery Voltage", "Battery Voltage", "Battery Voltage");
      Datastream datastream=null;
      FeatureOfInterest foi=utility.CreateFeatureOfInterest("Battery voltage", "Battery voltage", placeholder);
      try {
        datastream=service.datastreams().query().filter("name eq 'Battery Voltage'").first();
      } catch (ServiceFailureException e) {
        
      }
      if(datastream==null){
        UnitOfMeasurement unitOfMeasurement = new UnitOfMeasurement("milliVolt", "mV", "Voltage/1000");
        datastream = utility.CreateDatastream(thing, "Battery Voltage", "description", unitOfMeasurement, "OM_CountObservation", sensor,observedProperty);
      }
      utility.CreateObservation(datastream, value,foi);
      return 2;
    }

    case 21: {
      int value = parseAnalogIn(buffer, index + index_shift);
      ObservedProperty observedProperty=utility.CreateObservedProperty("Analog In 1", "Analog In 1", "Analog In 1");
      Datastream datastream=null;
      FeatureOfInterest foi=utility.CreateFeatureOfInterest("Analog In 1", "Analog In 1", placeholder);
      try {
        datastream=service.datastreams().query().filter("name eq 'Analog In 1'").first();
      } catch (ServiceFailureException e) {
      }
      if(datastream==null){
        UnitOfMeasurement unitOfMeasurement = new UnitOfMeasurement("milliVolt", "mV", "Voltage/1000");
        datastream = utility.CreateDatastream(thing, "Analog In 1", "description", unitOfMeasurement, "OM_CountObservation",sensor,observedProperty);
      }
      utility.CreateObservation(datastream, value,foi);
      return 2;
    }
    case 22: {
      int value = parseAnalogIn(buffer, index + index_shift);
      ObservedProperty observedProperty=utility.CreateObservedProperty("Analog In 2", "Analog In 2", "Analog In 2");
      Datastream datastream=null;
      FeatureOfInterest foi=utility.CreateFeatureOfInterest("Analog In 2", "Analog In 2", placeholder);
      try {
        datastream=service.datastreams().query().filter("name eq 'Analog In 2'").first();
      } catch (ServiceFailureException e) {
        
      }
      if(datastream==null){
        UnitOfMeasurement unitOfMeasurement = new UnitOfMeasurement("milliVolt", "mV", "Voltage/1000");
        datastream = utility.CreateDatastream(thing, "Analog In 2", "description", unitOfMeasurement, "OM_CountObservation",sensor,observedProperty);
      }
      utility.CreateObservation(datastream, value,foi);
      return 2;
    }

    case 23: {
      int value = parseAnalogIn(buffer, index + index_shift);
      ObservedProperty observedProperty=utility.CreateObservedProperty("Analog In 3", "Analog In 3", "Analog In 3");
      Datastream datastream=null;
      FeatureOfInterest foi=utility.CreateFeatureOfInterest("Analog In 3", "Analog In 3", placeholder);
      try {
        datastream=service.datastreams().query().filter("name eq 'Analog In 3'").first();
      } catch (ServiceFailureException e) {
        
      }
      if(datastream==null){
        UnitOfMeasurement unitOfMeasurement = new UnitOfMeasurement("milliVolt", "mV", "Voltage/1000");
        datastream = utility.CreateDatastream(thing, "Analog In 3", "description", unitOfMeasurement, "OM_CountObservation",sensor,observedProperty);
      }
      utility.CreateObservation(datastream, value,foi);
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
      ObservedProperty observedProperty=utility.CreateObservedProperty("Internal Temperature", "Internal Temperature", "Internal Temperature");
      Datastream datastream=null;
      FeatureOfInterest foi=utility.CreateFeatureOfInterest("Internal Temperature", "Internal Temperature", placeholder);
      try {
        datastream=service.datastreams().query().filter("name eq 'Internal Temperature'").first();
      } catch (ServiceFailureException e) {
        
      }
      if(datastream==null){
        UnitOfMeasurement unitOfMeasurement = new UnitOfMeasurement("Celsius", "°C", "Celsius Degree");
        datastream = utility.CreateDatastream(thing, "Internal Temperature", "description", unitOfMeasurement, "OM_Measurement",sensor,observedProperty);
      }
      utility.CreateObservation(datastream, value,foi);
      return 2;
    }
    case 41: {
      double value = parseI2CTempProbe(buffer, index + index_shift);
      ObservedProperty observedProperty=utility.CreateObservedProperty("Digital Matter I2C Temperature Probe 1 (Red)", "Digital Matter I2C Temperature Probe 1 (Red)", "Digital Matter I2C Temperature Probe 1 (Red)");
      Datastream datastream=null;
      FeatureOfInterest foi=utility.CreateFeatureOfInterest("Digital Matter I2C Temperature Probe 1 (Red)", "Digital Matter I2C Temperature Probe 1 (Red)", placeholder);
      try {
        datastream=service.datastreams().query().filter("name eq 'Digital Matter I2C Temperature Probe 1 (Red)'").first();
      } catch (ServiceFailureException e) {
        
      }
      if(datastream==null){
        UnitOfMeasurement unitOfMeasurement = new UnitOfMeasurement("Celsius", "°C", "Celsius Degree");
        datastream = utility.CreateDatastream(thing, "Digital Matter I2C Temperature Probe 1 (Red)", "description", unitOfMeasurement, "OM_Measurement",sensor,observedProperty);
      }
      utility.CreateObservation(datastream, value,foi);
      return 2;
    }
    case 42: {
      double value = parseI2CTempProbe(buffer, index + index_shift);
      ObservedProperty observedProperty=utility.CreateObservedProperty("Digital Matter I2C Temperature Probe 2 (Blue)", "Digital Matter I2C Temperature Probe 2 (Blue)", "Digital Matter I2C Temperature Probe 2 (Blue)");
      Datastream datastream=null;
      FeatureOfInterest foi=utility.CreateFeatureOfInterest("Digital Matter I2C Temperature Probe 2 (Blue)", "Digital Matter I2C Temperature Probe 2 (Blue)", placeholder);
      try {
        datastream=service.datastreams().query().filter("name eq 'Digital Matter I2C Temperature Probe 2 (Blue)'").first();
      } catch (ServiceFailureException e) {
        
      }
      if(datastream==null){
        UnitOfMeasurement unitOfMeasurement = new UnitOfMeasurement("Celsius", "°C", "Celsius Degree");
        datastream = utility.CreateDatastream(thing, "Digital Matter I2C Temperature Probe 2 (Blue)", "description", unitOfMeasurement, "OM_Measurement",sensor,observedProperty);
      }
      utility.CreateObservation(datastream, value,foi);
      return 2;
    }

    case 43: {
      double[] values = parseI2CTempRelativeHumidity(buffer, index + index_shift);
      ObservedProperty observedProperty=utility.CreateObservedProperty("Digital Matter I2C Temperature", "Digital Matter I2C Temperature", "Digital Matter I2C Temperature");
      Datastream datastream=null;
      FeatureOfInterest foi=utility.CreateFeatureOfInterest("Digital Matter I2C Temperature", "Digital Matter I2C Temperature", placeholder);
      try {
        datastream=service.datastreams().query().filter("name eq 'Digital Matter I2C Temperature'").first();
      } catch (ServiceFailureException e) {
      }
      if(datastream==null){
        UnitOfMeasurement unitOfMeasurement = new UnitOfMeasurement("Celsius", "°C", "Celsius Degree");
        datastream = utility.CreateDatastream(thing, "Digital Matter I2C Temperature", "description", unitOfMeasurement, "OM_Measurement",sensor,observedProperty);
      
      }
      utility.CreateObservation(datastream, values[0],foi); 
      foi=utility.CreateFeatureOfInterest("Digital Matter I2C Relative Humidity", "Digital Matter I2C Relative Humidity", placeholder);  
      try {
        datastream=service.datastreams().query().filter("name eq 'Digital Matter I2C Relative Humidity'").first();
      } catch (ServiceFailureException e) {
        
      }
      if(datastream==null){
        UnitOfMeasurement unitOfMeasurement = new UnitOfMeasurement("Percentage", "%", "1/100");
        datastream = utility.CreateDatastream(thing, "Digital Matter I2C Relative Humidity", "description", unitOfMeasurement, "OM_Measurement",sensor,observedProperty);
      }
      utility.CreateObservation(datastream, values[0],foi);
      return 3;
    }

    case 50: {
      int value = parseBatteryEnergySincePower(buffer, index + index_shift);
      ObservedProperty observedProperty=utility.CreateObservedProperty("Battery Energy Used Since Power Up", "Battery Energy Used Since Power Up", "Battery Energy Used Since Power Up");
      Datastream datastream=null;
      FeatureOfInterest foi=utility.CreateFeatureOfInterest("Battery Energy Used Since Power Up", "Battery Energy Used Since Power Up", placeholder);
      try {
        datastream=service.datastreams().query().filter("name eq 'Battery Energy Used Since Power Up'").first();
      } catch (ServiceFailureException e) {
        
      }
      if(datastream==null){
        UnitOfMeasurement unitOfMeasurement = new UnitOfMeasurement("milli Ampere hour", "mAh", "Amper/(hour*1000)");
        datastream = utility.CreateDatastream(thing, "Battery Energy Used Since Power Up", "description", unitOfMeasurement, "OM_Measurement",sensor,observedProperty);
      }
      utility.CreateObservation(datastream, value,foi);
      return 2;
    }

    case 51: {
      int value = parseEstimatedBatteryRemaining(buffer, index + index_shift);
      ObservedProperty observedProperty=utility.CreateObservedProperty("Estimated Battery % Remaining","Estimated Battery % Remaining","Estimated Battery % Remaining");
      Datastream datastream=null;
      FeatureOfInterest foi=utility.CreateFeatureOfInterest("Estimated Battery % Remaining", "Estimated Battery % Remaining", placeholder);
      try {
        datastream=service.datastreams().query().filter("name eq 'Estimated Battery % Remaining'").first();
      } catch (ServiceFailureException e) {
        
      }
      if(datastream==null){
        UnitOfMeasurement unitOfMeasurement = new UnitOfMeasurement("Percentage", "%", "1/100");
        datastream = utility.CreateDatastream(thing, "Estimated Battery % Remaining", "description", unitOfMeasurement, "OM_Measurement",sensor,observedProperty);
      }
      utility.CreateObservation(datastream, value,foi);
      return 1;
    }

    case 128: {
      ArrayList < Double > values = parseSDIMeasurement(id, buffer, index + index_shift);
      //ObservedProperty observedProperty=CreateObservedProperty("SDI-12 Measurement 1","SDI-12 Measurement 1","SDI-12 Measurement 1");
      Datastream datastream=null;
      FeatureOfInterest foi=utility.CreateFeatureOfInterest("SDI-12 Measurement 1", "SDI-12 Measurement 1", placeholder);
      try {
        datastream=service.datastreams().query().filter("name eq 'SDI-12 Measurement 1'").first();
        for (Double value: values) {
          utility.CreateObservation(datastream, value,foi);
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
      FeatureOfInterest foi=utility.CreateFeatureOfInterest("SDI-12 Measurement 1", "SDI-12 Measurement 1", placeholder);
      try {
        datastream=service.datastreams().query().filter("name eq 'SDI-12 Measurement 1'").first();
        for (Double value: values) {
          utility.CreateObservation(datastream, value,foi);
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
      FeatureOfInterest foi=utility.CreateFeatureOfInterest("SDI-12 Measurement 2", "SDI-12 Measurement 2", placeholder);
      try {
        datastream=service.datastreams().query().filter("name eq 'SDI-12 Measurement 2'").first();
        for (Double value: values) {
          utility.CreateObservation(datastream, value,foi);
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
      FeatureOfInterest foi=utility.CreateFeatureOfInterest("SDI-12 Measurement 2", "SDI-12 Measurement 2", placeholder);
      try {
        datastream=service.datastreams().query().filter("name eq 'SDI-12 Measurement 2'").first();
        for (Double value: values) {
          utility.CreateObservation(datastream, value,foi);
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
      FeatureOfInterest foi=utility.CreateFeatureOfInterest("SDI-12 Measurement 3", "SDI-12 Measurement 3", placeholder);
      try {
        datastream=service.datastreams().query().filter("name eq 'SDI-12 Measurement 3'").first();
        for (Double value: values) {
          utility.CreateObservation(datastream, value,foi);
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
      FeatureOfInterest foi=utility.CreateFeatureOfInterest("SDI-12 Measurement 3", "SDI-12 Measurement 3", placeholder);
      try {
        datastream=service.datastreams().query().filter("name eq 'SDI-12 Measurement 3'").first();
        for (Double value: values) {
          utility.CreateObservation(datastream, value,foi);
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
      FeatureOfInterest foi=utility.CreateFeatureOfInterest("SDI-12 Measurement 4", "SDI-12 Measurement 4", placeholder);
      try {
        datastream=service.datastreams().query().filter("name eq 'SDI-12 Measurement 4'").first();
        for (Double value: values) {
          utility.CreateObservation(datastream, value,foi);
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
      FeatureOfInterest foi=utility.CreateFeatureOfInterest("SDI-12 Measurement 4", "SDI-12 Measurement 4", placeholder);
      try {
        datastream=service.datastreams().query().filter("name eq 'SDI-12 Measurement 4'").first();
        for (Double value: values) {
          utility.CreateObservation(datastream, value,foi);
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
      FeatureOfInterest foi=utility.CreateFeatureOfInterest("SDI-12 Measurement 5", "SDI-12 Measurement 5", placeholder);
      try {
        datastream=service.datastreams().query().filter("name eq 'SDI-12 Measurement 5'").first();
        for (Double value: values) {
          utility.CreateObservation(datastream, value,foi);
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
      FeatureOfInterest foi=utility.CreateFeatureOfInterest("SDI-12 Measurement 5", "SDI-12 Measurement 5", placeholder);
      try {
        datastream=service.datastreams().query().filter("name eq 'SDI-12 Measurement 5'").first();
        for (Double value: values) {
          utility.CreateObservation(datastream, value,foi);
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
    return utility.parseLittleEndianUInt16(buffer, index);
  }
  private int parseAnalogIn(int[] buffer, int index) {
    return utility.parseLittleEndianUInt16(buffer, index);
  }
  private double parseInternalTemperature(int[] buffer, int index) {
    double internal_temperature = (double)utility.parseLittleEndianInt16(buffer, index) / 100;
    return internal_temperature;
  }
  private double parseI2CTempProbe(int[] buffer, int index) {
    double temp_probe = (double)utility.parseLittleEndianInt16(buffer, index) / 100;
    return temp_probe;
  }
  private double[] parseI2CTempRelativeHumidity(int[] buffer, int index) {
    double[] values = new double[2];
    values[0] = (double)utility.parseLittleEndianInt16(buffer, index) / 100;
    values[1] = buffer[index + 2] / 2;

    return values;
  }
  private int parseBatteryEnergySincePower(int[] buffer, int index) {
    int battery_energy_used = utility.parseLittleEndianInt16(buffer, index);

    return battery_energy_used;
  }
  private int parseEstimatedBatteryRemaining(int[] buffer, int index) {
    int estimated_battery_remaining = utility.parseLittleEndianInt16(buffer, index);

    return estimated_battery_remaining;
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
      data.add((double) utility.parseLittleEndianInt16(buffer, index + i * 2 + 1) / 100);
    }

    return data;
  }

  ArrayList < Double > parseSDIINT32Data(int[] buffer, int index, int num_samples) {
    ArrayList < Double > data = new ArrayList < Double > ();

    for (int i = 0; i < num_samples; i++) {
      data.add((double) utility.parseLittleEndianInt32(buffer, index + i * 4 + 1) / 1000);
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
  
  double[] parseGPSData(int[] buffer, int index) {

    double[] lat_lon = new double[2];
    lat_lon[0] = 0.0000256 * (double)utility.parseLittleEndianInt24(buffer, index); //latitudine
    lat_lon[1] = 0.0000256 * (double)utility.parseLittleEndianInt24(buffer, index + 3);//longitudine

    return lat_lon;
  }
}