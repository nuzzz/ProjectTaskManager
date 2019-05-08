import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.common.io.CharStreams;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
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
    }

    public void run(){
        runStuff();
    }

    public void runStuff(){
        String text = "";
        ObjectListing objectListing = s3client.listObjects(bucketName);
        for(S3ObjectSummary os : objectListing.getObjectSummaries()) {
            if(os.getKey().equals(taskListLocation)) {
                //get the file
                InputStream inputStream = s3client.getObject(bucketName, taskListLocation).getObjectContent();

                try (final Reader reader = new InputStreamReader(inputStream)) {
                    text = CharStreams.toString(reader);
                } catch (IOException e) {
                    LOGGER.severe("IO Exception occured: " + e);
                }
            }else{
                LOGGER.fine("No task list found. Exiting loop...");
            }
        }
        System.out.println(text);

        List<Bucket> buckets = s3client.listBuckets();
        for(Bucket bucket : buckets) {
            System.out.println(bucket.getName());
        }

    } //run stuff
}
