package helloworld;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;


import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handler for requests to Lambda function.
 */


public class WriteToTable implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final HashMap<String, AttributeValue> map = new HashMap<>();
    private static final String TABLE_NAME = "orderlog";
    private final AmazonDynamoDB ddb = AmazonDynamoDBClientBuilder.defaultClient();


    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
        String id;

        UUID uid = new UUID(32, 32).randomUUID();
        id = uid.toString();

        try {
            //parses the deserialized json body into a map
            ObjectMapper mapper = new ObjectMapper();
            Map<String, String> jMap = mapper.readValue(input.getBody(), Map.class);


            //use the map in order to get the value(string)
            AttributeValue OrderId = new AttributeValue().withS(id);
            AttributeValue name = new AttributeValue().withS(jMap.get("name"));
            AttributeValue address = new AttributeValue().withS(jMap.get("address"));
            AttributeValue cost = new AttributeValue().withS(jMap.get("cost"));
            AttributeValue phone = new AttributeValue().withS(jMap.get("phone"));
            AttributeValue email = new AttributeValue().withS(jMap.get("email"));
            AttributeValue order = new AttributeValue().withS(jMap.get("order"));

            //put all the attribute values in another map<String, AttributeValue>
            map.put("OrderId", OrderId);
            map.put("name", name);
            map.put("address", address);
            map.put("cost", cost);
            map.put("phone", phone);
            map.put("email", email);
            map.put("order", order);
            //call the function putrequest
            putRequest(map);
            //return the JSON status stuff
            HashMap<String, String> header = new HashMap<>();
            header.put("Access-Control-Allow-Origin", "*");
            header.put("Access-Control-Allow-Credentials", "true");
            return new APIGatewayProxyResponseEvent()
                .withBody("OK")
                .withHeaders(header)
                .withStatusCode(200);

        } catch (IOException e) {
            return new APIGatewayProxyResponseEvent()
                .withBody("Not OK")
                .withStatusCode(500);
        }
    }

    private void putRequest(Map<String, AttributeValue> parameters) {

        PutItemRequest request = new PutItemRequest()
                .withTableName(TABLE_NAME)
                .withItem(parameters);
        PutItemResult result = ddb.putItem(request);

    }


}
