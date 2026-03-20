import org.eclipse.paho.client.mqttv3.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.*;

public class Outsourcer implements Runnable, MqttCallback {

    private boolean stopReceived = false;

    private final TspBlackboard blackboard;

    // Map jobs to workers and track jobs internally
    private final BlockingQueue<TspJob> jobQueue = new LinkedBlockingQueue<>();
    private final Map<Integer, TspJob> inFlightJobs = new ConcurrentHashMap<>();
    private final Map<Integer, Long> startTimes = new ConcurrentHashMap<>();
    private final long timeoutMs;


    private MqttClient client;

    private final ObjectMapper mapper = new ObjectMapper();

    private int jobsSolved = 0;

    public Outsourcer(TspBlackboard blackboard) {
        this.blackboard = blackboard;
        // potential to-do: adaptive timeouts based on map size and/or average job completion time
        this.timeoutMs = 15000; // Wait for more time if remote workers aren't finishing jobs

        try {
            client = new MqttClient(TspBlackboard.BROKER, "system-outsourcer");
            client.setCallback(this);
        } catch (MqttException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            // Options for better MqttConnection reliability
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setKeepAliveInterval(60);
            options.setConnectionTimeout(30);

            client.connect(options);
            client.subscribe(TspBlackboard.REQUEST_TOPIC);
            client.subscribe(TspBlackboard.RESPONSE_TOPIC);

            // Keep thread running while outsourcer waits for requests and distributes jobs
            while (true) {
                // Track distributed jobs
                System.out.println("[Outsourcer] Tracking jobs...");
                long now = System.currentTimeMillis();

                for (Integer jobId : inFlightJobs.keySet()) {
                    if (now - startTimes.get(jobId) > timeoutMs) {
                        // Remove failed jobs from flight
                        TspJob job = inFlightJobs.remove(jobId);
                        startTimes.remove(jobId);

                        // Reinsert failed jobs to the job queue
                        if (job != null) {
                            jobQueue.put(job);
                        }
                    }
                }

                // Once STOP received, wait for jobs in flight to complete
                if (stopReceived && jobQueue.isEmpty() && inFlightJobs.isEmpty()) {
                    sendShutdown();
                    System.out.println("[Outsourcer] Outsourcer stopped.");
                    System.out.println("[Outsourcer] " + jobsSolved + " solved remotely.");
                    client.disconnect();
                    break;
                }

                // If there are no jobs left in the global queue, wait for jobs in flight to complete
                // (The above condition does not trigger if no remote workers are making requests)
                if (blackboard.allJobsDone() && jobQueue.isEmpty() && inFlightJobs.isEmpty()) {
                    sendShutdown();
                    System.out.println("[Outsourcer] Outsourcer stopped.");
                    System.out.println("[Outsourcer] " + jobsSolved + " jobs solved remotely.");
                    client.disconnect();
                    break;
                }

                Thread.sleep(1000);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
        System.out.println("[Outsourcer] " + topic);
        byte[] payload = mqttMessage.getPayload();

        try {
            if (topic.equals(TspBlackboard.REQUEST_TOPIC)) {
                handleRequest(payload);
            } else if (topic.equals(TspBlackboard.RESPONSE_TOPIC)) {
                handleResponse(payload);
            }
        } catch (MqttException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void connectionLost(Throwable throwable) {
        System.err.println("[Outsourcer] CONNECTION LOST: " + throwable.getMessage());
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {

    }

    private void handleRequest(byte[] payload) throws Exception {
        // Receive a request payload
        String request = new String(payload);
        int workerId = Integer.parseInt(request);

        // Try to take a job from the retry queue first, then the global queue
        TspJob tspJob;
        if (!jobQueue.isEmpty()) {
            tspJob = jobQueue.poll();
        } else {
            tspJob = blackboard.pollJob();
        }

        if (tspJob == null) {
            System.out.println("[Outsourcer] Failed to get job.");
            return;
        }
        System.out.println("[Outsourcer] Got job: " + tspJob);

        if (tspJob.equals(TspJob.STOP)) {
            stopReceived = true;
            return;
        }

        byte[] jsonBytes = mapper.writeValueAsBytes(tspJob);
        MqttMessage message = new MqttMessage(jsonBytes);
        message.setQos(1);

        if (client != null && client.isConnected()) {
            client.publish(TspBlackboard.ASSIGN_TOPIC + "/" + workerId, message);
            inFlightJobs.put(tspJob.id, tspJob);
            startTimes.put(tspJob.id, System.currentTimeMillis());

            System.out.println("[Outsourcer] Job " + tspJob.id + " assigned to " + workerId);
        }
    }

    private void handleResponse(byte[] payload) throws Exception {
        // Receive a solved job payload
        SolvedTspJob solvedTspJob = mapper.readValue(payload, SolvedTspJob.class);
        System.out.println("[Outsourcer] Job " + solvedTspJob.job.id + " received from " + solvedTspJob.workerId);

        // Update internal job tracking
        inFlightJobs.remove(solvedTspJob.job.id);
        startTimes.remove(solvedTspJob.job.id);

        // Submit job result
        blackboard.submitJobResult(solvedTspJob.tour);
        jobsSolved++;
    }

    private void sendShutdown() throws Exception {
        byte[] jsonBytes = mapper.writeValueAsBytes(TspJob.STOP);
        MqttMessage message = new MqttMessage(jsonBytes);
        message.setQos(1);

        if (client != null && client.isConnected()) {
            client.publish(TspBlackboard.SHUTDOWN_TOPIC, message);
            System.out.println("[Outsourcer] Shutdown broadcasted.");

            // Give time for message to be sent
            Thread.sleep(3000);
        }
    }
}
