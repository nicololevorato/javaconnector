package com.tesilevorato.JavaMqttClient;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttMessage;
//import org.json.JSONObject;

public class OnMessageCallback implements MqttCallback {
    ConnettoreDatasense connettore= new ConnettoreDatasense();
    public void connectionLost(Throwable cause) {
        // After the connection is lost, it usually reconnects here
        System.out.println("disconnect，you can reconnect");
    }

    public void messageArrived(String topic, MqttMessage message) throws Exception {
        // The messages obtained after subscribe will be executed here
        //System.out.println("Received message topic:" + topic);
        //System.out.println("Received message Qos:" + message.getQos());
        //System.out.println("Received message content:" + new String(message.getPayload()));
        //connettore.translate(new String(message.getPayload()));
        System.out.println(new String(message.getPayload()));
        connettore.translate(new String(message.getPayload()));
       
    }

    public void deliveryComplete(IMqttDeliveryToken token) {
        System.out.println("deliveryComplete---------" + token.isComplete());
    
    }
}