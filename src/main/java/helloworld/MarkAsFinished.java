package helloworld;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.*;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;


import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MarkAsFinished implements RequestHandler<SNSEvent, APIGatewayProxyResponseEvent> {
    private final AmazonDynamoDB ddb = AmazonDynamoDBClientBuilder
            .standard()
            .withRegion(Regions.US_WEST_1)
            .build();
//    private final GlobalSecondaryIndex gsi = GlobalSecondaryIndex;
    private static final String TABLE_NAME = "orderlog";
    private static final String INDEX_NAME = "PhoneNumber-index";
    HashMap<String, String> map = new HashMap<>();




    public APIGatewayProxyResponseEvent handleRequest(SNSEvent snsEvent, Context context) {
//  call the global secondary index during the query request
        try{
            //parse json to a map
            ObjectMapper mapper = new ObjectMapper();
            Map<String, String> jMap = mapper.readValue(snsEvent.getRecords().get(0).getSNS().getMessage(), Map.class);
            //get the original phoneNumber and message body
            String body = jMap.get("messageBody").toUpperCase();
            String numberSplit = jMap.get("originationNumber");
            //gotta take off the +1
            String phoneNumber = numberSplit.substring(2, 12);
            System.out.println(phoneNumber);
            //query the gsi for the orderkey
            String OrderId = queryForKey(phoneNumber);
            //once you found the orderkey you can update it
            UpdateOrder(OrderId, body);
        }catch (IOException e) {
            return new APIGatewayProxyResponseEvent()
                    .withBody("Not OK")
                    .withStatusCode(500);
        }

        HashMap<String, String> header = new HashMap<>();
        header.put("Access-Control-Allow-Origin", "*");
        header.put("Access-Control-Allow-Credentials", "true");
        return new APIGatewayProxyResponseEvent()
                .withHeaders(header)
                .withStatusCode(200);
    }

    private String queryForKey(String phoneNumber) {
        HashMap<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#PhoneNumber", "PhoneNumber");
        HashMap<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":PhoneNumberValue", new AttributeValue().withS(phoneNumber));

        QueryRequest request = new QueryRequest()
                .withTableName(TABLE_NAME)
                .withIndexName(INDEX_NAME)
                .withKeyConditionExpression("#PhoneNumber = :PhoneNumberValue")
                .withExpressionAttributeNames(expressionAttributeNames)
                .withExpressionAttributeValues(expressionAttributeValues);

        QueryResult result = ddb.query(request);

        List<Map<String, AttributeValue>> attributeValue = result.getItems();
        List<Map<String, String>> finalResults = new ArrayList<>();

        for(int i = 0; i < attributeValue.size(); i++) {
            Map<String, String> newMap = attributeValue.get(i)
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(e -> e.getKey(), e-> e.getValue().getS()));
            finalResults.add(newMap);
        }
        map.put("OrderId", finalResults.get(0).get("OrderId"));

        return map.get("OrderId");
    }


    private void UpdateOrder(String OrderId, String body) {

        if (body.equals("COMPLETE")) {
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("OrderId", new AttributeValue().withS(OrderId));

            Map<String, AttributeValue> attributeValues = new HashMap<>();
            attributeValues.put(":availability", new AttributeValue().withS("Completed"));

            UpdateItemRequest updateItemRequest = new UpdateItemRequest()
                    .withTableName(TABLE_NAME)
                    .withKey(key)
                    .withUpdateExpression("set availability = :availability")
                    .withExpressionAttributeValues(attributeValues);

            UpdateItemResult updateItemResult = ddb.updateItem(updateItemRequest);

        }

        else if (body.equals("INCOMPLETE")) {
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("OrderId", new AttributeValue().withS(OrderId));

            Map<String, AttributeValue> attributeValues = new HashMap<>();
            attributeValues.put(":availability", new AttributeValue().withS("Active"));

            UpdateItemRequest updateItemRequest = new UpdateItemRequest()
                    .withTableName(TABLE_NAME)
                    .withKey(key)
                    .withUpdateExpression("set availability = :availability")
                    .withExpressionAttributeValues(attributeValues);

            UpdateItemResult updateItemResult = ddb.updateItem(updateItemRequest);
        }
    }

}
