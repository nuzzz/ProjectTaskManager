import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.io.CharStreams;
import com.conspec.model.TodoistTempTask;



import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class ProjectTaskManager {

    private final static Logger LOGGER = Logger.getLogger(ProjectTaskManager.class.getName());

    private String bucketName = "com.conspec";
    private String taskListLocation = "ScheduledTasks/scheduledTaskList.json";
    private AmazonS3 s3client;

    public ProjectTaskManager(){
        initAWS();
    }

    public void initAWS(){
        LOGGER.info("initializing AWS s3client variable");
        var pcp = new ProfileCredentialsProvider();
        var credentials = pcp.getCredentials();
        this.s3client = AmazonS3ClientBuilder
                .standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion(Regions.AP_SOUTHEAST_1)
                .build();
    } //initAWS

    public <T> List<T> jsonArrayToObjectList(String json, Class<T> tClass) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        CollectionType listType = mapper.getTypeFactory().constructCollectionType(ArrayList.class, tClass);
        List<T> ts = mapper.readValue(json, listType);
        LOGGER.fine("class name: " +  ts.get(0).getClass().getName());
        return ts;
    }


    public void run(){
        var jsonString = getJsonDataFromS3();
        ObjectMapper objectMapper = new ObjectMapper();
        List<TodoistTempTask> taskList = new ArrayList<>();
        try {
            taskList = jsonArrayToObjectList(jsonString, TodoistTempTask.class);
        }catch (IOException error){
            LOGGER.severe("IO Exception occured: " + error);
        }

        if(taskList.size()!=0) {
            for (TodoistTempTask task : taskList) {
                System.out.println(task.getEndDate());
            }
        }else{
            System.out.println("Task list is empty");
        }
    } //run

    //Credits: https://stackoverflow.com/a/46233446
    public String getJsonDataFromS3(){
        String jsonString = "";
        ObjectListing objectListing = s3client.listObjects(bucketName);
        for(S3ObjectSummary os : objectListing.getObjectSummaries()) {
            if(os.getKey().equals(taskListLocation)) {
                //get the file
                InputStream inputStream = s3client.getObject(bucketName, taskListLocation).getObjectContent();

                try (final Reader reader = new InputStreamReader(inputStream)) {
                    jsonString = CharStreams.toString(reader);
                } catch (IOException error) {
                    LOGGER.severe("IO Exception occured: " + error);
                }
            }else{
                LOGGER.fine("No task list found. Exiting loop...");
            }
        }
        return jsonString;
    } //getJsonDataFromS3
}
