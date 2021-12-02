package com.tesilevorato.JavaMqttClient;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.json.JSONObject;

public class Connettore {
    int id;
    JSONObject payload;
    String content;
    public Connettore(){
        Connection conn = null;
        try {
            // db parameters
            String url = "jdbc:sqlite:C:/Universita/Materiale Tesi/workspace/Connettorejava/javaconnector/db/idsensor.db";
            // create a connection to the database
            conn = DriverManager.getConnection(url);
            
            System.out.println("Connection to SQLite has been established.");
            
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        } finally {
            try {
                if (conn != null) {
                    conn.close();
                }
            } catch (SQLException ex) {
                System.out.println(ex.getMessage());
            }
        }
    }
    
    public void translate(String payload){
        this.payload= new JSONObject(payload);
        this.id=this.payload.getInt("id");
        this.content=this.payload.getString("payload");
        print();
    }
    public void print(){
        System.out.println("Ricevuto payload con id :"+id);
        System.out.println("Ricevuto payload con contenuto :"+content);
    }
}
