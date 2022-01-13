package com.tesilevorato.JavaMqttClient;

import java.net.MalformedURLException;
import java.net.URL;
import com.google.gson.Gson;
import org.ugeojson.model.feature.FeatureDto;
import org.ugeojson.model.geometry.PointDto;
import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.model.Datastream;
import de.fraunhofer.iosb.ilt.sta.model.FeatureOfInterest;
import de.fraunhofer.iosb.ilt.sta.model.Location;
import de.fraunhofer.iosb.ilt.sta.model.Observation;
import de.fraunhofer.iosb.ilt.sta.model.ObservedProperty;
import de.fraunhofer.iosb.ilt.sta.model.Sensor;
import de.fraunhofer.iosb.ilt.sta.model.Thing;
import de.fraunhofer.iosb.ilt.sta.model.ext.UnitOfMeasurement;
import de.fraunhofer.iosb.ilt.sta.service.SensorThingsService;

public class ConnettoreUtility {
    SensorThingsService service;
    public ConnettoreUtility(){     
    try {
      URL serviceEndpoint = new URL("http://localhost:8080/FROST-Server/v1.0");
      service = new SensorThingsService(serviceEndpoint);
      
    } catch (MalformedURLException e1) {
        System.out.println("Errore connessione servizio SensorThings");
        e1.printStackTrace();
    }
    
      
    }
    public SensorThingsService ConnectionSensorThings(){
        //connsessione al servizio SensorThings
    
    return this.service;
    }
    public  void CreateLocation(Thing temp_thing, Double lat, Double lon) throws ServiceFailureException {
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
      public Datastream CreateDatastream(Thing temp_thing, String name, String description, UnitOfMeasurement unitOfMeasurement, String observationType, Sensor sensor,ObservedProperty obsPro) {
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
        public ObservedProperty CreateObservedProperty(String name, String definition, String description){
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
      public  Sensor CreateSensor(String name, String description, String encodingType, Object metadata){
        Sensor sensor=new Sensor();
        sensor.setName(name);   
        sensor.setDescription(description);
        sensor.setMetadata(metadata);
        //IdString id=new IdString("Datasense");
        //sensor.setId(id);
        try {
          service.create(sensor);
        } catch (ServiceFailureException e) {
          e.printStackTrace();
        }
        return sensor;
    }
    public void CreateObservation(Datastream datastream, Object value, FeatureOfInterest foi) throws ServiceFailureException {
        Observation obs = new Observation();
        obs.setResult(value);
        obs.setFeatureOfInterest(foi);
        datastream.observations().create(obs);
      }
      public FeatureOfInterest CreateFeatureOfInterest(String name, String description, Object feature) {
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
        public long parseLittleEndianInt32(int[] buffer, int offset) {
            long result = (long)parseLittleEndianUInt32(buffer, offset);
        
            if ((result & 0x80000000) > 0)
        
              result = result - 0x100000000L;
        
            return result;
          }
          public  int parseLittleEndianInt24(int[] buffer, int offset) {
            int result = (buffer[offset + 2] << 16) + (buffer[offset + 1] << 8) + buffer[offset];
        
            if ((result & 0x800000) > 0)
              result = result - 0x1000000;
        
            return result;
          }
          public int parseLittleEndianInt16(int[] buffer, int offset) {
            int result = (buffer[offset + 1] << 8) + buffer[offset];
        
            if ((result & 0x8000) > 0)
              result = result - 0x10000;
        
            return result;
          }
          public int parseLittleEndianUInt16(int[] buffer, int offset) {
            int result = (buffer[offset + 1] << 8) + buffer[offset];
        
            return result;
          }
          public int[] getHex(String value) {

            int num_bytes = value.length() / 2;
            int[] bytes = new int[num_bytes];
        
            for (int i = 0; i < num_bytes; i++) {
              bytes[i] = Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
            }
            return bytes;
          }
          public int parseLittleEndianUInt32(int[] buffer, int offset) {
            int result = (buffer[offset + 3] << 24) + (buffer[offset + 2] << 16) + (buffer[offset + 1] << 8) + buffer[offset];
        
            return result;
          }
          public float parseLittleEndianFloat(int[] buffer, int offset){
            int result = parseLittleEndianUInt32(buffer, offset);
            return Float.intBitsToFloat(result);
          }
          public int parseLittleEndianUInt8(int[] buffer, int offset){
            return buffer[offset];
          }
          public char parseChar(int[] buffer, int offset){
              return (char) buffer[offset];     
          }
}
