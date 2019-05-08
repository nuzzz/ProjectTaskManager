package com.conspec;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;


import com.conspec.models.Command;
import com.conspec.models.TodoistTempTask;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.io.CharStreams;

import okhttp3.*;

import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;

public class ProjectTaskManager {

    private final static Logger LOGGER = Logger.getLogger(ProjectTaskManager.class.getName());

    private String bucketName = "com.conspec";
    private String taskListLocation = "ScheduledTasks/scheduledTaskList.json";
    private AmazonS3 s3client;

    private String todoistAccessKey = "todoist.access.key";

    private String accesskey;

    public ProjectTaskManager() {
        accesskey = "";
        initAWS();
        initTodoist();
    }

    public void initAWS() {
        LOGGER.info("initializing AWS s3client variable");
        var pcp = new ProfileCredentialsProvider();
        var credentials = pcp.getCredentials();
        this.s3client = AmazonS3ClientBuilder
                .standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion(Regions.AP_SOUTHEAST_1)
                .build();
    } //initAWS

    private void initTodoist() {
        InputStream is = getClass().getClassLoader().getResourceAsStream("config.properties");
        Properties properties = new Properties();

        try {
            properties.load(is);
            this.accesskey = properties.getProperty(todoistAccessKey);
            if (this.accesskey.isEmpty()) {
                LOGGER.warning("Access key not found");
            } else {
                LOGGER.info("Access key found and saved into memory");
            }
        } catch (IOException error) {
            LOGGER.severe("IO exception occurred: " + error);
        }
    } //initTodoist

    public void run() {
        //Load tasks from s3 to String
        var jsonString = getJsonStringFromS3();

        //Put tasks into taskList
        List<TodoistTempTask> taskList = new ArrayList<>();
        var currentDate = LocalDate.now();
        try {
            taskList = jsonArrayToObjectList(jsonString, TodoistTempTask.class);
        } catch (IOException error) {
            LOGGER.severe("IO Exception occured: " + error);
        }

        //Collect tasks to be added.
        var addTheseTasks = new ArrayList<TodoistTempTask>();

        for (TodoistTempTask task : taskList) {
            if (!task.getStartDate().isAfter(currentDate)) {
                addTheseTasks.add(task);
            }
        }
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LOGGER.info("added " + taskList.size() + " tasks on " + currentDate.format(dtf));
        addTasksToTodoist(addTheseTasks);
    } //run

    public Response sendHTTPPost(String body) {

        //Client
        OkHttpClient client = new OkHttpClient();

        //Request
        String syncURLString = "https://todoist.com/api/v8/sync";
        Headers requestHeaders = new Headers.Builder()
                .add("Content-Type", "application/x-www-form-urlencoded")
                .add("Authorization", "Bearer " + this.accesskey)
                .add("Accept", "application/json")
                .build();


        FormBody.Builder formBuilder = new FormBody.Builder().add("commands", body);
        RequestBody requestBody = formBuilder.build();


        Request request = new Request.Builder()
                .url(syncURLString)
                .headers(requestHeaders)
                .post(requestBody)
                .build();
        //Response
        Response response = null;
        try {
            response = client.newCall(request).execute();
        } catch (IOException ioe) {
            LOGGER.severe("Failed to create response: " + ioe);
        }
        return response;
    }

    private void addTasksToTodoist(ArrayList<TodoistTempTask> addTheseTasks) {
        List<Command> commands = new ArrayList<>();

        for (TodoistTempTask task : addTheseTasks) {
            String commandID = UUID.randomUUID().toString();

            Map<String, Object> taskArguments = new HashMap<String, Object>();
            taskArguments.put("content", task.getContent());
            taskArguments.put("due", task.getDue());
            taskArguments.put("project_id", task.getProject_id());
            taskArguments.put("parent_id", task.getParent_id());
            taskArguments.put("child_order", task.getChild_order());


            Command addItemCommand = new Command("item_add", commandID, task.getTemp_id(), taskArguments);

            commands.add(addItemCommand);
        }

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String commandsToJSON = objectMapper.writeValueAsString(commands);

            Response response = sendHTTPPost(commandsToJSON);
            try {
                System.out.println(response.body().string());
            } catch (IOException e) {
                e.printStackTrace();
            }

        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }//addTasksToTodoist


    public String getJsonStringFromS3() {
        String jsonString = "";
        ObjectListing objectListing = s3client.listObjects(bucketName);
        for (S3ObjectSummary os : objectListing.getObjectSummaries()) {
            if (os.getKey().equals(taskListLocation)) {
                //get the file
                InputStream inputStream = s3client.getObject(bucketName, taskListLocation).getObjectContent();

                try (final Reader reader = new InputStreamReader(inputStream)) {
                    jsonString = CharStreams.toString(reader);
                } catch (IOException error) {
                    LOGGER.severe("IO Exception occured: " + error);
                }
            } else {
                LOGGER.fine("No task list found. Exiting loop...");
            }
        }
        return jsonString;
    } //getJsonDataFromS3

    //Credits: https://stackoverflow.com/a/46233446
    private <T> List<T> jsonArrayToObjectList(String json, Class<T> tClass) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        CollectionType listType = mapper.getTypeFactory().constructCollectionType(ArrayList.class, tClass);
        List<T> ts = mapper.readValue(json, listType);
        LOGGER.fine("class name: " + ts.get(0).getClass().getName());
        return ts;
    } //jsonArrayToObjectList

}
