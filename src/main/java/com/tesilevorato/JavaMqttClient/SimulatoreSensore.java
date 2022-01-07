package com.tesilevorato.JavaMqttClient;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.*;
// import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class SimulatoreSensore extends Thread {
    int id =1;
    JSONObject content= new JSONObject();
    String topic        = "MQTT Examples";
    String payload      = "16706f6e6e7071283608";
    int portId          =  130;
    int qos             = 2;
    String broker       = "tcp://localhost:1883";
    String clientId     = "Sensore";
    MemoryPersistence persistence = new MemoryPersistence();
    public void run() {

        try {
            MqttClient sampleClient = new MqttClient(broker, clientId, persistence);
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            System.out.println("Connecting to broker: "+broker);
            sampleClient.connect(connOpts);
            System.out.println("Sensore Connected");
            //sampleClient.setCallback(new OnMessageCallback());
            //System.out.println("Publishing message: "+content);
            boolean inviato=true;
            while(inviato){
                try {
                    Thread.sleep(1000);
                    content.put("id",id);
                    content.put("payload",payload);
                    content.put("portId", portId);
                    MqttMessage message = new MqttMessage(content.toString().getBytes());
                    message.setQos(qos);
                    sampleClient.publish(topic, message);
                    System.out.println("Message published");
                    
                    } catch (InterruptedException | JSONException e) {
                    e.printStackTrace();
                }
                inviato=false;
            }  
            //sampleClient.disconnect();
            //System.out.println("Disconnected");
            //System.exit(0);
        } catch(MqttException me) {
            System.out.println("reason "+me.getReasonCode());
            System.out.println("msg "+me.getMessage());
            System.out.println("loc "+me.getLocalizedMessage());
            System.out.println("cause "+me.getCause());
            System.out.println("excep "+me);
            me.printStackTrace();
        }
    }
}
