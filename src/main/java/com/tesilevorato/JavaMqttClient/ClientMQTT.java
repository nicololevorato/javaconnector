package com.tesilevorato.JavaMqttClient;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
//import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class ClientMQTT extends Thread{
        String topic        = "MQTT Examples";
        String content      = "Message from MqttPublishSample";
        int qos             = 2;
        String broker       = "tcp://localhost:1883";
        String clientId     = "Client";
        MemoryPersistence persistence = new MemoryPersistence();
    public void run() {

        try {
            MqttClient sampleClient = new MqttClient(broker, clientId, persistence);
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            System.out.println("Connecting to broker: "+broker);
            sampleClient.connect(connOpts);
            System.out.println("Client Connected");
            sampleClient.setCallback(new OnMessageCallback());
            sampleClient.subscribe(topic);
            // System.out.println("Publishing message: "+content);
            // MqttMessage message = new MqttMessage(content.getBytes());
            // message.setQos(qos);
            // sampleClient.publish(topic, message);
            // System.out.println("Message published");
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
