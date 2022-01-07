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
import de.fraunhofer.iosb.ilt.sta.model.Location;
import de.fraunhofer.iosb.ilt.sta.model.Observation;
import de.fraunhofer.iosb.ilt.sta.model.Thing;
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

    //List<Thing> thingsList=new ArrayList<>();
    //thingsList.add(thing);
    //loc.setThings(thingsList);
    service.create(loc);
  }
  private Datastream CreateDatastream(Thing temp_thing, String name, String description, UnitOfMeasurement unitOfMeasurement, String observationType) {
      Datastream datastream = new Datastream();
      datastream.setName(name);
      datastream.setDescription(description);
      datastream.setUnitOfMeasurement(unitOfMeasurement);
      datastream.setObservationType(observationType);
      try {
        temp_thing.datastreams().create(datastream);
      } catch (ServiceFailureException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      return datastream;
    }
  
  private void CreateObservation(Datastream datastream, Object value) throws ServiceFailureException {
    Observation obs = new Observation();
    obs.setResult(value);
    datastream.observations().create(obs);
  }

  
  public void datasense(String payload, int thingId, int portId) throws ServiceFailureException, JSONException {
    //System.out.println(service.things().find((long)thingId));
    Thing thing = service.things().find((long)thingId);
    for(int i=1;i<6;i++){
      UnitOfMeasurement um=new UnitOfMeasurement();
      CreateDatastream(thing, "SDI-12 Measurement "+i , "SDI-12 Measurement "+i,um , "OM_Measurement");
    }
    int[] buffer;
    buffer = getHex(payload);
    int payload_length = buffer.length;
    int iterated_message_length = 0;
    int temp_msg_lenght;
    temp_msg_lenght = parseDataField(buffer, iterated_message_length, portId, thing);
    iterated_message_length += temp_msg_lenght;
    while (iterated_message_length < payload_length) {
      temp_msg_lenght = parseDataField(buffer, iterated_message_length, -1, thing);
      iterated_message_length += temp_msg_lenght + 1;
    }
  }

  private int parseDataField(int[] buffer, int index, int port_id, Thing thing) throws ServiceFailureException {
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
      Datastream datastream;
      try {
        datastream = thing.datastreams().find("Battery Voltage");
      } catch (ServiceFailureException e) {
        UnitOfMeasurement unitOfMeasurement = new UnitOfMeasurement("milliVolt", "mV", "Voltage/1000");
        datastream = CreateDatastream(thing, "Battery Voltage", "description", unitOfMeasurement, "OM_CountObservation");
      }
      CreateObservation(datastream, value);
      return 2;
    }

    case 21: {
      int value = parseAnalogIn(buffer, index + index_shift);
      Datastream datastream;
      try {
        datastream = thing.datastreams().find("Analog In 1");
      } catch (ServiceFailureException e) {
        UnitOfMeasurement unitOfMeasurement = new UnitOfMeasurement("milliVolt", "mV", "Voltage/1000");
        datastream = CreateDatastream(thing, "Analog In 1", "description", unitOfMeasurement, "OM_CountObservation");
      }
      CreateObservation(datastream, value);
      return 2;
    }
    case 22: {
      int value = parseAnalogIn(buffer, index + index_shift);
      Datastream datastream;
      try {
        datastream = thing.datastreams().find("Analog In 2");
      } catch (ServiceFailureException e) {
        UnitOfMeasurement unitOfMeasurement = new UnitOfMeasurement("milliVolt", "mV", "Voltage/1000");
        datastream = CreateDatastream(thing, "Analog In 2", "description", unitOfMeasurement, "OM_CountObservation");
      }
      CreateObservation(datastream, value);
      return 2;
    }

    case 23: {
      int value = parseAnalogIn(buffer, index + index_shift);
      Datastream datastream;
      try {
        datastream = thing.datastreams().find("Analog In 3");
      } catch (ServiceFailureException e) {
        UnitOfMeasurement unitOfMeasurement = new UnitOfMeasurement("milliVolt", "mV", "Voltage/1000");
        datastream = CreateDatastream(thing, "Analog In 3", "description", unitOfMeasurement, "OM_CountObservation");
      }
      CreateObservation(datastream, value);
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
      Datastream datastream;
      try {
        datastream = thing.datastreams().find("Internal Temperature");
      } catch (ServiceFailureException e) {
        UnitOfMeasurement unitOfMeasurement = new UnitOfMeasurement("Celsius", "°C", "Celsius Degree");
        datastream = CreateDatastream(thing, "Internal Temperature", "description", unitOfMeasurement, "OM_Measurement");
      }
      CreateObservation(datastream, value);
      return 2;
    }
    case 41: {
      double value = parseI2CTempProbe(buffer, index + index_shift);
      Datastream datastream;
      try {
        datastream = thing.datastreams().find("Digital Matter I2C Temperature Probe 1 (Red)");
      } catch (ServiceFailureException e) {
        UnitOfMeasurement unitOfMeasurement = new UnitOfMeasurement("Celsius", "°C", "Celsius Degree");
        datastream = CreateDatastream(thing, "Digital Matter I2C Temperature Probe 1 (Red)", "description", unitOfMeasurement, "OM_Measurement");
      }
      CreateObservation(datastream, value);
      return 2;
    }
    case 42: {
      double value = parseI2CTempProbe(buffer, index + index_shift);
      Datastream datastream;
      try {
        datastream = thing.datastreams().find("Digital Matter I2C Temperature Probe 2 (Blue)");
      } catch (ServiceFailureException e) {
        UnitOfMeasurement unitOfMeasurement = new UnitOfMeasurement("Celsius", "°C", "Celsius Degree");
        datastream = CreateDatastream(thing, "Digital Matter I2C Temperature Probe 2 (Blue)", "description", unitOfMeasurement, "OM_Measurement");
      }
      CreateObservation(datastream, value);
      return 2;
    }

    case 43: {
      double[] values = parseI2CTempRelativeHumidity(buffer, index + index_shift);
      Datastream datastream;
      try {
        datastream = thing.datastreams().find("Digital Matter I2C Temperature");
      } catch (ServiceFailureException e) {
        UnitOfMeasurement unitOfMeasurement = new UnitOfMeasurement("Celsius", "°C", "Celsius Degree");
        datastream = CreateDatastream(thing, "Digital Matter I2C Temperature", "description", unitOfMeasurement, "OM_Measurement");
      }
      CreateObservation(datastream, values[0]);
      try {
        datastream = thing.datastreams().find("Digital Matter I2C Relative Humidity");
      } catch (ServiceFailureException e) {
        UnitOfMeasurement unitOfMeasurement = new UnitOfMeasurement("Percentage", "%", "1/100");
        datastream = CreateDatastream(thing, "Digital Matter I2C Relative Humidity", "description", unitOfMeasurement, "OM_Measurement");
      }
      CreateObservation(datastream, values[0]);
      return 3;
    }

    case 50: {
      int value = parseBatteryEnergySincePower(buffer, index + index_shift);
      Datastream datastream;
      try {
        datastream = thing.datastreams().find("Battery Energy Used Since Power Up");
      } catch (ServiceFailureException e) {
        UnitOfMeasurement unitOfMeasurement = new UnitOfMeasurement("milli Ampere hour", "mAh", "Amper/(hour*1000)");
        datastream = CreateDatastream(thing, "Battery Energy Used Since Power Up", "description", unitOfMeasurement, "OM_Measurement");
      }
      CreateObservation(datastream, value);
      return 2;
    }

    case 51: {
      int value = parseEstimatedBatteryRemaining(buffer, index + index_shift);
      Datastream datastream;
      try {
        datastream = thing.datastreams().find("Estimated Battery % Remaining");
      } catch (ServiceFailureException e) {
        UnitOfMeasurement unitOfMeasurement = new UnitOfMeasurement("Percentage", "%", "1/100");
        datastream = CreateDatastream(thing, "Estimated Battery % Remaining", "description", unitOfMeasurement, "OM_Measurement");
      }
      CreateObservation(datastream, value);
      return 1;
    }

    case 128: {
      ArrayList < Double > values = parseSDIMeasurement(id, buffer, index + index_shift);
      Datastream datastream;
      try {
        datastream = thing.datastreams().find("SDI-12 Measurement 1");
        for (Double value: values) {
          CreateObservation(datastream, value);
        }
      } catch (ServiceFailureException e) {
        System.out.println(("Datastream misurazione non presente!"));
      }

      return getSDISize(buffer, index + index_shift);
    }

    case 129:{
      ArrayList < Double > values = parseSDIMeasurement(id, buffer, index + index_shift);
      Datastream datastream;
      try {
        datastream = thing.datastreams().find("SDI-12 Measurement 1");
        for (Double value: values) {
          CreateObservation(datastream, value);
        }
      } catch (ServiceFailureException e) {
        System.out.println(("Datastream misurazione non presente!"));
      }

      return getSDISize(buffer, index + index_shift);
    }

    case 130:
    {
      ArrayList < Double > values = parseSDIMeasurement(id, buffer, index + index_shift);
      Datastream datastream;
      try {
        datastream = thing.datastreams().find("SDI-12 Measurement 2");
        for (Double value: values) {
          CreateObservation(datastream, value);
        }
      } catch (ServiceFailureException e) {
        System.out.println(("Datastream misurazione non presente!"));
      }

      return getSDISize(buffer, index + index_shift);
    }

    case 131:
    {
      ArrayList < Double > values = parseSDIMeasurement(id, buffer, index + index_shift);
      Datastream datastream;
      try {
        datastream = thing.datastreams().find("SDI-12 Measurement 2");
        for (Double value: values) {
          CreateObservation(datastream, value);
        }
      } catch (ServiceFailureException e) {
        System.out.println(("Datastream misurazione non presente!"));
      }

      return getSDISize(buffer, index + index_shift);
    }

    case 132:
    {
      ArrayList < Double > values = parseSDIMeasurement(id, buffer, index + index_shift);
      Datastream datastream;
      try {
        datastream = thing.datastreams().find("SDI-12 Measurement 3");
        for (Double value: values) {
          CreateObservation(datastream, value);
        }
      } catch (ServiceFailureException e) {
        System.out.println(("Datastream misurazione non presente!"));
      }

      return getSDISize(buffer, index + index_shift);
    }

    case 133:
    {
      ArrayList < Double > values = parseSDIMeasurement(id, buffer, index + index_shift);
      Datastream datastream;
      try {
        datastream = thing.datastreams().find("SDI-12 Measurement 3");
        for (Double value: values) {
          CreateObservation(datastream, value);
        }
      } catch (ServiceFailureException e) {
        System.out.println(("Datastream misurazione non presente!"));
      }

      return getSDISize(buffer, index + index_shift);
    }

    case 134:
    {
      ArrayList < Double > values = parseSDIMeasurement(id, buffer, index + index_shift);
      Datastream datastream;
      try {
        datastream = thing.datastreams().find("SDI-12 Measurement 4");
        for (Double value: values) {
          CreateObservation(datastream, value);
        }
      } catch (ServiceFailureException e) {
        System.out.println(("Datastream misurazione non presente!"));
      }

      return getSDISize(buffer, index + index_shift);
    }

    case 135:
    {
      ArrayList < Double > values = parseSDIMeasurement(id, buffer, index + index_shift);
      Datastream datastream;
      try {
        datastream = thing.datastreams().find("SDI-12 Measurement 4");
        for (Double value: values) {
          CreateObservation(datastream, value);
        }
      } catch (ServiceFailureException e) {
        System.out.println(("Datastream misurazione non presente!"));
      }

      return getSDISize(buffer, index + index_shift);
    }

    case 136:
    {
      ArrayList < Double > values = parseSDIMeasurement(id, buffer, index + index_shift);
      Datastream datastream;
      try {
        datastream = thing.datastreams().find("SDI-12 Measurement 5");
        for (Double value: values) {
          CreateObservation(datastream, value);
        }
      } catch (ServiceFailureException e) {
        System.out.println(("Datastream misurazione non presente!"));
      }

      return getSDISize(buffer, index + index_shift);
    }

    case 137:
    {
      ArrayList < Double > values = parseSDIMeasurement(id, buffer, index + index_shift);
      Datastream datastream;
      try {
        datastream = thing.datastreams().find("SDI-12 Measurement 5");
        for (Double value: values) {
          CreateObservation(datastream, value);
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
    double internal_temperature = parseLittleEndianInt16(buffer, index) / 100;
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