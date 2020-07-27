package helloworld;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.*;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;


import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
//move to us west 2

public class PendingOrder implements RequestHandler <APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final AmazonDynamoDB ddb = AmazonDynamoDBClientBuilder.defaultClient();
    private static final String TABLE_NAME = "orderlog";
    private Map<String, String> jMap;

    HashMap <String, String> map = new HashMap<>();
    private HashMap<String, AttributeValue> smsMap = new HashMap<>();

    private final AmazonSNS amazonSNS = AmazonSNSClientBuilder.defaultClient();

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
        //update dynanodb
        UpdateOrder(OrderId);
        //send txt message to customer
        smsMap = queryForNamePhone(OrderId);
        //updateCustomer(smsMap);

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
    private HashMap queryForNamePhone(String OrderId) {
        HashMap<String, String> expressionAttributesNames = new HashMap<>();
        expressionAttributesNames.put("#OrderId", "OrderId");
        HashMap<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":OrderIdValue", new AttributeValue().withS(OrderId));

        QueryRequest request = new QueryRequest()
                .withTableName(TABLE_NAME)
                .withKeyConditionExpression("#OrderId = :OrderIdValue")
                .withExpressionAttributeNames(expressionAttributesNames)
                .withExpressionAttributeValues(expressionAttributeValues);

        //result will be a list of maps
        QueryResult result = ddb.query(request);
        List<Map<String, AttributeValue>> attributeValue = result.getItems();
        List<Map<String, String>> finalResults = new ArrayList<>();

        for (int i = 0; i < attributeValue.size(); i++) {
            Map<String, String> newMap = attributeValue.get(i)
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().getS()));
            finalResults.add(newMap);
        }


        map.put("name", finalResults.get(0).get("name"));
        map.put("phone", finalResults.get(0).get("phone"));

        return map;
    }

    private void updateCustomer(HashMap smsMap) {
        String customer = smsMap.get("name").toString();
        String phoneNumber = smsMap.get("phone").toString();

        String message = "Hello " + customer + ", thank you for using NextDoorShopper! A kind person " +
                "has just volunteered to pick up your order and will arrive shortly.";

        String phone = "+1" + phoneNumber;

        PublishResult result = amazonSNS.publish(new PublishRequest()
            .withMessage(message)
            .withPhoneNumber(phone));

        System.out.println(result);




















    }

}