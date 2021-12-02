package com.tesilevorato.JavaMqttClient;

//import org.eclipse.paho.client.mqttv3.MqttMessage;

/**
 * Hello world!
 *
 */
public class App 
{
    
    public static void main(String[] args) {

       ClientMQTT client = new ClientMQTT();
       SimulatoreSensore sensore=new SimulatoreSensore();
       client.run();
       try {
        Thread.sleep(1000);
        } catch (InterruptedException e) {
        e.printStackTrace();
    }
       sensore.run();
    }
}

    


