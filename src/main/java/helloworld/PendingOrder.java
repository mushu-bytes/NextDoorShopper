package helloworld;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.*;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
//move to us west 2

public class PendingOrder implements RequestHandler <APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final AmazonDynamoDB ddb = AmazonDynamoDBClientBuilder
            .standard()
            .withRegion(Regions.US_WEST_1)
            .build();

    private static final String TABLE_NAME = "orderlog";
    private Map<String, String> jMap;

    HashMap <String, String> map = new HashMap<>();
    private HashMap<String, String> customerMap = new HashMap<>();
    private HashMap<String, AttributeValue> attributeMap = new HashMap<>();



    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {

        HashMap<String, String> header = new HashMap<>();
        header.put("Access-Control-Allow-Origin", "*");
        header.put("Access-Control-Allow-Credentials", "true");

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
        customerMap = queryForNamePhone(OrderId);

        String availability = customerMap.get("availability");

        if (availability.equals("Pending")){
            return new APIGatewayProxyResponseEvent()
                    .withBody("Order Taken")
                    .withHeaders(header)
                    .withStatusCode(400);
        }
        else{
            AttributeValue volunteer = new AttributeValue().withS(jMap.get("volunteer"));
            AttributeValue phoneNumber = new AttributeValue().withS(jMap.get("phoneNumber"));

            attributeMap.put("volunteer", volunteer);
            attributeMap.put("PhoneNumber", phoneNumber);

            UpdateOrder(attributeMap, OrderId);
        }
        //For Cors
        System.out.println(header);
        //return it as API Gateway Proxy Event
        return new APIGatewayProxyResponseEvent()
                .withBody("OK")
                .withHeaders(header)
                .withStatusCode(200);

    }

    private void UpdateOrder(Map<String, AttributeValue> parameters, String OrderId) {

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

        //volunteer
        Map<String, AttributeValue> keyVolunteer = new HashMap<>();
        keyVolunteer.put("OrderId", new AttributeValue().withS(OrderId));

        Map<String, AttributeValue> attributeValuesVolunteer = new HashMap<>();
        attributeValuesVolunteer.put(":volunteer", parameters.get("volunteer"));

        UpdateItemRequest updateItemRequestVolunteer = new UpdateItemRequest()
                .withTableName(TABLE_NAME)
                .withKey(key)
                .withUpdateExpression("set volunteer = :volunteer")
                .withExpressionAttributeValues(attributeValuesVolunteer);

        UpdateItemResult resultVolunteer = ddb.updateItem(updateItemRequestVolunteer);


        //phonenumber
        Map<String, AttributeValue> keyPhone = new HashMap<>();
        keyPhone.put("OrderId", new AttributeValue().withS(OrderId));

        Map<String, AttributeValue> attributeValuesPhone = new HashMap<>();
        attributeValuesPhone.put(":VolunteerPhoneNumber", parameters.get("PhoneNumber"));

        UpdateItemRequest updateItemRequestPhone = new UpdateItemRequest()
                .withTableName(TABLE_NAME)
                .withKey(keyPhone)
                .withUpdateExpression("set VolunteerPhoneNumber = :VolunteerPhoneNumber")
                .withExpressionAttributeValues(attributeValuesPhone);

        UpdateItemResult resultPhone = ddb.updateItem(updateItemRequestPhone);




    }
    private HashMap<String, String> queryForNamePhone(String OrderId) {
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
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getS()));
            finalResults.add(newMap);
        }


        map.put("name", finalResults.get(0).get("name"));
        map.put("phone", finalResults.get(0).get("phone"));
        map.put("availability", finalResults.get(0).get("availability"));


        return map;
    }


























}