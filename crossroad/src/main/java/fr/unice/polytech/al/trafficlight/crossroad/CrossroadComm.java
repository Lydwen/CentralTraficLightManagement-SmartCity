package fr.unice.polytech.al.trafficlight.crossroad;

import com.google.gson.*;
import fr.unice.polytech.al.trafficlight.crossroad.utils.EmergencyLogger;
import fr.unice.polytech.al.trafficlight.utils.Emergency;
import fr.unice.polytech.al.trafficlight.utils.Scenario;
import fr.unice.polytech.al.trafficlight.utils.SynchronizeMessage;
import org.apache.log4j.Logger;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by nathael on 16/11/16.
 */
@Path("crossroad")
public class CrossroadComm {
    private final static Logger LOG = Logger.getLogger(CrossroadComm.class);
    private static CrossroadModuleCore CORE;

    static {
        try {
            CORE = new CrossroadModuleCore();

            // ask for Central to get a scenario to run
            for(int tries=0; tries<1000; tries++) {
                try { // wait some time if no response
                    LOG.warn("Connecting to central failed "+tries+" times. Retrying...");
                    Thread.sleep(100 * tries);
                } catch(InterruptedException ignored){}

                URL url = new URL("https://central-traffic-light.herokuapp.com/crossroad/"+CORE.getCrossRoadId()+"/scenario");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                if(connection.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(
                        (connection.getInputStream())));

                    String response="";
                    String output;
                    LOG.debug("Output from Server .... \n");
                    while ((output= br.readLine()) != null) {
                        response+=output;
                    }

                    connection.disconnect();

                    LOG.debug("######## We have a response!");
                    LOG.debug("######## "+response);
                    Gson gson = new GsonBuilder().create();
                    Scenario newScenario = gson.fromJson(response, Scenario.class);
                    CORE.changeScenario(newScenario);
                    break;
                }
            }

        } catch (IOException e) {
            LOG.fatal("Impossible to initialise CrossroadModuleCore", e);
        }
    }

    private EmergencyLogger emergencyLogger = EmergencyLogger.getInstance();
    private Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @PUT
    @Path("/starter")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response changeScenario(String newScenarioStr) {
        LOG.debug("######## Starter called !");
        Scenario newScenario = gson.fromJson(newScenarioStr, Scenario.class);
        CORE.changeScenario(newScenario);

        LOG.debug("Starter call finished > Response OK");
        return Response.ok().entity(gson.toJson(newScenario)).build();
    }

    @GET
    @Path("/scenario")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getScenario() {
        return Response.ok().entity(gson.toJson(
                CORE.getActiveScenario())
        ).build();
    }

    @PUT
    @Path("/stopper")
    @Produces(MediaType.APPLICATION_JSON)
    public Response stopTrafficLight() {
        LOG.debug("######## Stopper called !");

        if(!CORE.isRunning())
            LOG.info("Wasn't running, but calling stop anyway");

        CORE.stopRunning();

        return Response.ok().build();
    }

    @PUT
    @Path("/emergency")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response callEmergency(String emergencyCallStr) {
        LOG.debug("######## Emergency called !");

        Emergency emergency = gson.fromJson(emergencyCallStr, Emergency.class);
        emergencyLogger.log(emergency);

        CORE.callEmergency(emergency);

        return Response.ok().build();
    }

    @PUT
    @Path("/synchronize")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response callSynchronize(String synchronizeStr) {
        LOG.debug("######## Synchronize called !");

        Gson gson = new GsonBuilder().create();
        CORE.synchronize(gson.fromJson(synchronizeStr, SynchronizeMessage.class));

        return Response.ok().build();
    }

    @POST
    @Path("/trafficlight/{trafficlightId}/vehicle/{vehicleId}")
    public Response addVehicle(@PathParam("trafficlightId") String trafficlightId, @PathParam("vehicleId") String vehicleId ) {
        LOG.debug("######## Add vehicle called !");

        for(TrafficLight trafficLight: CORE.getTrafficLights()) {
            if(trafficLight.getId().getId().equals(trafficlightId)) {
                LOG.debug("Electric vehicle before add: " + trafficLight.getElectricVehicle());
                trafficLight.addElectricVehicle();
                LOG.debug("Electric vehicle after add: " + trafficLight.getElectricVehicle());
                return Response.ok().build();
            }
        }
        LOG.error("TrafficLightId: " + trafficlightId +" do not exist");
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    @DELETE
    @Path("/trafficlight/{trafficlightId}/vehicle/{vehicleId}")
    public Response removeVehicle(@PathParam("trafficlightId") String trafficlightId, @PathParam("vehicleId") String vehicleId ) {
        LOG.debug("######## Remove vehicle called !");

        for(TrafficLight trafficLight: CORE.getTrafficLights()) {
            if (trafficLight.getId().getId().equals(trafficlightId))
                try {
                    LOG.debug("Electric vehicle before remove: " + trafficLight.getElectricVehicle());
                    trafficLight.removeElectricVehicle();
                    LOG.debug("Electric vehicle after remove: " + trafficLight.getElectricVehicle());
                    return Response.ok().build();
                } catch (NoVehiclesToRemove noVehiclesToRemove) {
                    LOG.error("Trying to remove vehicle where there are no vehicles waiting");
                    return Response.status(Response.Status.NOT_ACCEPTABLE).build();
                }
        }

        LOG.error("TrafficLightId: " + trafficlightId +" do not exists");
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    @GET
    @Path("/emergency/logs")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getEmergencyLogs() {
        return Response.ok().entity(
                gson.toJson(emergencyLogger.getLogs())
        ).build();
    }

    @GET
    @Path("/status")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStatus() {
        LOG.debug("######## Get status called !");

        JsonArray jsonArray = new JsonArray();

        for(TrafficLight trafficLight: CORE.getTrafficLights()) {
            JsonObject jsonTrafficLight = new JsonObject();
            jsonTrafficLight.addProperty("id", trafficLight.getId().toString());
            jsonTrafficLight.addProperty("state",
                    (trafficLight.isDisabled()?"disabled"
                            :trafficLight.isGreen()?"green"
                            :"red"));
            jsonTrafficLight.addProperty("last_state_change", new SimpleDateFormat("dd MMM HH:mm:ss")
                    .format(new Date(trafficLight.getLastStateChangeDate())));

            jsonTrafficLight.addProperty("electric_vehicles", trafficLight.getElectricVehicle());

            jsonArray.add(jsonTrafficLight);
        }

        String message = gson.toJson(jsonArray);
        message = message.length()>0?"[" + message.substring(1) + "]":"[]";

        return Response.ok().entity(message).build();
    }
}
