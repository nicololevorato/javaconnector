// package com.tesilevorato.JavaMqttClient;

// public class ConnettoreLibellium {
//     private void libellium(String payload) throws SQLException, ServiceFailureException {
//         // String sql = "SELECT * FROM libelliumOBS WHERE id=" + thingId;        
//         // PreparedStatement pstmt  = conn.prepareStatement(sql);         
//         // ResultSet rs  = pstmt.executeQuery();
//         //nell'header in posizione del terzo byte è presente l'informazione sulla tipologia di frame
//         Charset charset = Charset.forName("ASCII");
//         byte[] byteArrray = payload.getBytes(charset);
//         if (byteArrray.length > 2) {
//           int temp = (int) byteArrray[3] & 128;
//           if (temp == 128) {
//             libelliumASCII(payload);
//           } else {
//             libelliumBINARY(payload);
//           }
//         }
//         private void libelliumBINARY(String payload) {
//             String serialIDs = payload.substring(10, 13);
//             int serialID = Integer.parseInt(serialIDs, 16);
//             String[] temp_fields = payload.split("(?<=\\G.{2})");
        
//           }
//           //#region parser dataframe ASCII libellium
//           private void libelliumASCII(String payload) throws SQLException, ServiceFailureException {
//             String[] temp_fields = payload.split("#");
//             int serial_id = Integer.parseInt(temp_fields[1]);
//             String sql_thing = "SELECT THINGID from libelliumID2THING WHERE SERIALID=" + serial_id;
//             PreparedStatement pstmt_thing = conn.prepareStatement(sql_thing);
//             ResultSet rs_thing = pstmt_thing.executeQuery();
//             int thingID = rs_thing.getInt("THINGID");
//             Thing temp_thing = service.things().find(thingID);
//             String[] fields = Arrays.copyOfRange(temp_fields, 4, temp_fields.length - 1);
//             for (String field: fields) {
//               String[] splitted_field = field.split(":");
//               if (splitted_field[0].toUpperCase() == "GPS") {
//                 String[] temp = splitted_field[1].split(";");
//                 Double lat = Double.parseDouble(temp[0]);
//                 Double lon = Double.parseDouble(temp[1]);
//                 CreateLocation(temp_thing, lat, lon);
//               } else {
//                 String sql_um = "SELECT sensor tag FROM libelliumUM WHERE ASCII=" + splitted_field[0].toUpperCase();
//                 PreparedStatement pstmt_um = conn.prepareStatement(sql_um);
//                 ResultSet rs_um = pstmt_um.executeQuery();
//                 String sensor_tag = rs_um.getString("sensor tag");
//                 String sql_obs = "SELECT " + sensor_tag.toUpperCase() + " FROM libelliumOBS WHERE THINGID=" + thingID;
//                 PreparedStatement pstmt_obs = conn.prepareStatement(sql_obs);
//                 ResultSet rs_obs = pstmt_obs.executeQuery();
//                 int obs_num = rs_obs.getInt(sensor_tag.toUpperCase());
//                 Datastream temp_datastream = temp_thing.datastreams().find(obs_num);
//                 String data_type = temp_datastream.getObservationType();
//                 Observation temp_observation = new Observation();
//                 temp_observation.setDatastream(temp_datastream);
//                 switch (data_type.toUpperCase()) {
//                 case "OM_CATEGORYOBSERVATION":
//                   temp_observation.setResult(splitted_field[1]);
//                   break;
//                 case "OM_COUNTOBSERVATION":
//                   temp_observation.setResult(Integer.parseInt(splitted_field[1]));
//                   break;
//                 case "OM_MEASUREMENT":
//                   temp_observation.setResult(Double.parseDouble(splitted_field[1]));
//                   break;
//                 case "OM_OBSERVATION":
//                   temp_observation.setResult(splitted_field[1]);
//                   break;
//                 case "OM_TRUTHOBSERVATION":
//                   temp_observation.setResult(Boolean.parseBoolean(splitted_field[1]));
//                   break;
//                 default:
//                   break;
//                 }
//                 service.create(temp_observation);
//               }
//             }
        
//           }
//       }
// }
