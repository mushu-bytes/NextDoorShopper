package helloworld;


import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

import java.util.HashMap;
import java.util.Map;

public class LogCompletedOrders implements RequestHandler<DynamodbEvent, APIGatewayProxyResponseEvent> {

    private final AmazonS3 s3client = AmazonS3ClientBuilder
            .standard()
            .withRegion(Regions.US_WEST_1)
            .build();

    private Map<String, AttributeValue> isComplete = new HashMap<String, AttributeValue>();



    public APIGatewayProxyResponseEvent handleRequest(DynamodbEvent dynamodbEvent, Context context) {
        for (DynamodbEvent.DynamodbStreamRecord record : dynamodbEvent.getRecords()) {
            try{

                if (record.getEventName().equals("REMOVE")){
                    System.out.println(record.getDynamodb());
                    isComplete = record.getDynamodb().getOldImage();

                    String bucketName = "completedorderlist";
                    String objKeyName = "Customer Name: " + isComplete.get("name") + "Volunteer Name: " +
                    isComplete.get("volunteer");

                    String log = "Customer Name: " + isComplete.get("name") + "| Customer Phone: "
                            + isComplete.get("phone") + "| Customer Zipcode: " + isComplete.get("Zipcode") +
                            "| Customer Order: " + isComplete.get("order") + "| Volunteer: " + isComplete.get("volunteer") +
                            "| Volunteer Phone Number: " + isComplete.get("VolunteerPhoneNumber");
                    s3client.putObject(bucketName, objKeyName, log);

                }
            } catch (AmazonServiceException e) {
                // The call was transmitted successfully, but Amazon S3 couldn't process
                // it, so it returned an error response.
                e.printStackTrace();
            } catch (SdkClientException e) {
                // Amazon S3 couldn't be contacted for a response, or the client
                // couldn't parse the response from Amazon S3.
                e.printStackTrace();
            }
        }

        HashMap<String, String> header = new HashMap<>();
        header.put("Access-Control-Allow-Origin", "*");
        header.put("Access-Control-Allow-Credentials", "true");
        return new APIGatewayProxyResponseEvent()
                .withHeaders(header)
                .withStatusCode(200);
    }
}
