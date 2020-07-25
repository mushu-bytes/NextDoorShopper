package helloworld;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import com.amazonaws.services.dynamodbv2.model.UpdateItemResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


public class PendingOrder implements RequestHandler <APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final AmazonDynamoDB ddb = AmazonDynamoDBClientBuilder.defaultClient();
    private static final String TABLE_NAME = "orderlog";
    private Map<String, String> jMap;

    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {


        try {
            //parses the json body into a map
            ObjectMapper mapper = new ObjectMapper();
            jMap = mapper.readValue(input.getBody(), Map.class);

        } catch (IOException e) {
            return new APIGatewayProxyResponseEvent()
                    .withBody("Not OK")
                    .withStatusCode(500);
        }
        String OrderId = jMap.get("OrderId");

        UpdateOrder(OrderId);

        //For Cors
        HashMap<String, String> header = new HashMap<>();
        header.put("Access-Control-Allow-Origin", "*");
        header.put("Access-Control-Allow-Credentials", "true");
        //return it as API Gateway Proxy Event
        return new APIGatewayProxyResponseEvent()
                .withBody("OK")
                .withHeaders(header)
                .withStatusCode(200);

    }

    private void UpdateOrder(String OrderId) {

        Map<String, AttributeValue> key = new HashMap<>();
        key.put("OrderId", new AttributeValue().withS(OrderId));

        Map<String, AttributeValue> attributeValues = new HashMap<>();
        attributeValues.put(":availability", new AttributeValue().withS("Pending"));

        UpdateItemRequest updateItemRequest = new UpdateItemRequest()
                .withTableName(TABLE_NAME)
                .withKey(key)
                .withUpdateExpression("set availability = :availability")
                .withExpressionAttributeValues(attributeValues);

        UpdateItemResult updateItemResult = ddb.updateItem(updateItemRequest);




    }
}