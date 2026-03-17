import org.eclipse.paho.client.mqttv3.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

public class RemoteWorker implements Runnable, MqttCallback {

    private volatile boolean running = true;
    private boolean isBusy = false;

    private final int id;

    private final TspBlackboard blackboard;

    private MqttClient client;
    private String clientId;

    private final ObjectMapper mapper = new ObjectMapper();

    public RemoteWorker(int id, TspBlackboard blackboard) {
        this.id = id;
        this.clientId = "system-remote-worker-" + id;
        this.blackboard = blackboard;
    }

    @Override
    public void run() {
        try {
            client = new MqttClient(TspBlackboard.BROKER, clientId);
            client.setCallback(this);
            client.connect();
            client.subscribe(TspBlackboard.ASSIGN_TOPIC + "/" + id);
            client.subscribe(TspBlackboard.SHUTDOWN_TOPIC);

            while (running) {
                // Obtain a job if free
                if (!isBusy) {
                    requestJob();
                    Thread.sleep(3000); // Sleep for more time if remote workers are getting stuck
                } else {
                    // Keep thread running while worker requests and solves jobs
                    Thread.sleep(1000);
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (MqttException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
        // Receive a job payload
        byte[] payload = mqttMessage.getPayload();
        TspJob tspJob = mapper.readValue(payload, TspJob.class);

        // Shut down worker on sentinel
        if (tspJob.equals(tspJob.STOP)) {
            System.out.println("[RemoteWorker " + id + "] Shutting down.");

            // Prevent more requests from being made during shutdown
            isBusy = true;
            running = false;
            return;
        }

        // Solve the job
        isBusy = true;
        List<City> cities = blackboard.getCities(tspJob.mapUrl);
        List<Integer> tour = NearestNeighborSolver.solve(cities, tspJob.startIndex);
        System.out.println("[RemoteWorker" + id + "] Job " + tspJob.id + " solved.");

        // Send the result back
        SolvedTspJob solvedTspJob = new SolvedTspJob(tspJob, tour, id);
        try {
            byte[] jsonBytes = mapper.writeValueAsBytes(solvedTspJob);
            MqttMessage message = new MqttMessage(jsonBytes);
            message.setQos(1);
            if (client != null && client.isConnected()) {
                client.publish(TspBlackboard.RESPONSE_TOPIC, message);
                System.out.println("[RemoteWorker" + id + "] Result sent.");
            }
        } catch (MqttException ex) {
            ex.printStackTrace();
        }

        // Mark as not busy so the next job can be requested
        isBusy = false;
    }

    @Override
    public void connectionLost(Throwable throwable) {

    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {

    }

    private void requestJob() {
        try {
            String content = Integer.toString(id);
            MqttMessage message = new MqttMessage(content.getBytes());
            message.setQos(0);
            if (client != null && client.isConnected()) {
                client.publish(TspBlackboard.REQUEST_TOPIC, message);
                System.out.println("[RemoteWorker" + id + "] Job requested.");
            }
        } catch (MqttException ex) {
            ex.printStackTrace();
        }
    }
}
