package helloworld;

import com.amazonaws.regions.Regions;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.*;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;

import com.amazonaws.services.pinpoint.AmazonPinpoint;
import com.amazonaws.services.pinpoint.AmazonPinpointClientBuilder;
import com.amazonaws.services.pinpoint.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

//since this function is based off an sns event, p sure it will be still useful since i will need to know if its accepted or not

public class MarkAsFinished implements RequestHandler<SNSEvent, APIGatewayProxyResponseEvent> {
    private final AmazonDynamoDB ddb = AmazonDynamoDBClientBuilder
            .standard()
            .withRegion(Regions.US_WEST_1)
            .build();
    private final AmazonPinpoint client = AmazonPinpointClientBuilder.standard()
            .build();

    private static final String TABLE_NAME = "orderlog";
    private static final String CUSTOMER_INDEX_NAME = "phone-index";
    private static final String VOLUNTEER_INDEX_NAME = "VolunteerPhoneNumber-index";



    HashMap<String, String> customerMap = new HashMap();
    HashMap<String, String> namePhoneMap = new HashMap();

    String OrderId;


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
            //big prob because there are separate categories in dynamodb for customer phone and
            //volunteer phone. Had to create two methods and i think the only way to differentiate
            //the two is by having the customer say something else
            if (body.equals("NEW VOLUNTEER")) {
                OrderId = queryForKeyCustomer(phoneNumber);
            }
            else {
                OrderId = queryForKeyVolunteer(phoneNumber);
            }

            //once you found the orderkey you can update it
            updateOrder(OrderId, body);


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

    private String queryForKeyCustomer(String phoneNumber) {
        HashMap<String, String> customerKeyMap = new HashMap<>();

        HashMap<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#phone", "phone");
        HashMap<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":phoneValue", new AttributeValue().withS(phoneNumber));

        QueryRequest request = new QueryRequest()
                .withTableName(TABLE_NAME)
                .withIndexName(CUSTOMER_INDEX_NAME)
                .withKeyConditionExpression("#phone = :phoneValue")
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
        customerKeyMap.put("OrderId", finalResults.get(0).get("OrderId"));

        return customerKeyMap.get("OrderId");
    }

    private String queryForKeyVolunteer(String phoneNumber) {
        HashMap<String, String> volunteerKeyMap = new HashMap<>();
        HashMap<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#VolunteerPhoneNumber", "VolunteerPhoneNumber");
        HashMap<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":VolunteerPhoneNumberValue", new AttributeValue().withS(phoneNumber));

        QueryRequest request = new QueryRequest()
                .withTableName(TABLE_NAME)
                .withIndexName(VOLUNTEER_INDEX_NAME)
                .withKeyConditionExpression("#VolunteerPhoneNumber = :VolunteerPhoneNumberValue")
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
        volunteerKeyMap.put("OrderId", finalResults.get(0).get("OrderId"));

        return volunteerKeyMap.get("OrderId");
    }


    private void updateOrder(String OrderId, String body) {

        if (body.equals("COMPLETE")) {

            namePhoneMap = queryForCustomerVolunteer(OrderId);
            completeOrder(namePhoneMap);

            Map<String, AttributeValue> key = new HashMap<>();
            key.put("OrderId", new AttributeValue().withS(OrderId));

            DeleteItemRequest deleteOrder = new DeleteItemRequest()
                    .withTableName(TABLE_NAME)
                    .withKey(key);
            ddb.deleteItem(deleteOrder);


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

            namePhoneMap = queryForCustomerVolunteer(OrderId);
            smsCustomerVolunteer(namePhoneMap);
        }

        else if (body.equals("REJECT ORDER")) {
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

            namePhoneMap = queryForCustomerVolunteer(OrderId);
            smsCustomerVolunteer(namePhoneMap);

        }
        else if (body.equals("NEW VOLUNTEER")) {
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

            namePhoneMap = queryForCustomerVolunteer(OrderId);
            smsCustomerVolunteer(namePhoneMap);

        }
    }

    private HashMap queryForCustomerVolunteer(String OrderId) {
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


        customerMap.put("name", finalResults.get(0).get("name"));
        customerMap.put("phone", finalResults.get(0).get("phone"));
        customerMap.put("volunteer", finalResults.get(0).get("volunteer"));
        customerMap.put("VolunteerPhoneNumber", finalResults.get(0).get("VolunteerPhoneNumber"));

        return customerMap;

    }
    private void smsCustomerVolunteer(HashMap smsMap) {
        String customer = smsMap.get("name").toString();
        String phone = smsMap.get("phone").toString();
        String volunteer = smsMap.get("volunteer").toString();
        String volunteerPhoneNumber = smsMap.get("VolunteerPhoneNumber").toString();

        String message = "Hello " + customer + ", unfortunately the person that volunteered to " +
                "pick up your order is currently unavailable,but We will find a replacement " +
                "as soon as possible";

        String phoneNumber = "+1" + phone;

        String originationNumber = "+19095314210";
        String appId = "ac3bd51844c644b9affc6df79c3434ea";
        String messageType = "TRANSACTIONAL";

        Map<String, AddressConfiguration> addressMap =
                new HashMap<>();

        addressMap.put(phoneNumber, new AddressConfiguration()
                .withChannelType(ChannelType.SMS));

        SendMessagesRequest request = new SendMessagesRequest()
                .withApplicationId(appId)
                .withMessageRequest(new MessageRequest()
                        .withAddresses(addressMap)
                        .withMessageConfiguration(new DirectMessageConfiguration()
                                .withSMSMessage(new SMSMessage()
                                        .withBody(message)
                                        .withMessageType(messageType)
                                        .withOriginationNumber(originationNumber)
                                )));
        client.sendMessages(request);

        String volunteerMessage = "Hello " + volunteer + ", unfortunately the person that volunteered to " +
                "pick up your order is currently unavailable, but we will find a replacement " +
                "as soon as possible";

        String phoneNumberVolunteer = "+1" + volunteerPhoneNumber;

        Map<String, AddressConfiguration> addressMapVolunteer =
                new HashMap<>();

        addressMapVolunteer.put(phoneNumberVolunteer, new AddressConfiguration()
                .withChannelType(ChannelType.SMS));

        SendMessagesRequest requestVolunteer = new SendMessagesRequest()
                .withApplicationId(appId)
                .withMessageRequest(new MessageRequest()
                        .withAddresses(addressMapVolunteer)
                        .withMessageConfiguration(new DirectMessageConfiguration()
                                .withSMSMessage(new SMSMessage()
                                        .withBody(volunteerMessage)
                                        .withMessageType(messageType)
                                        .withOriginationNumber(originationNumber)
                                )));
        client.sendMessages(requestVolunteer);
    }
    private void completeOrder(HashMap smsMap) {
        String customer = smsMap.get("name").toString();
        String phone = smsMap.get("phone").toString();
        String volunteer = smsMap.get("volunteer").toString();
        String volunteerPhoneNumber = smsMap.get("VolunteerPhoneNumber").toString();

        String message = "Hello " + customer + ", thank you for using NextDoorShopper. Please contact us via" +
                "email if you have any remaining questions, concerns, or complaints.";

        String phoneNumber = "+1" + phone;

        String originationNumber = "+19095314210";
        String appId = "ac3bd51844c644b9affc6df79c3434ea";
        String messageType = "TRANSACTIONAL";

        Map<String, AddressConfiguration> addressMap =
                new HashMap<>();

        addressMap.put(phoneNumber, new AddressConfiguration()
                .withChannelType(ChannelType.SMS));

        SendMessagesRequest request = new SendMessagesRequest()
                .withApplicationId(appId)
                .withMessageRequest(new MessageRequest()
                        .withAddresses(addressMap)
                        .withMessageConfiguration(new DirectMessageConfiguration()
                                .withSMSMessage(new SMSMessage()
                                        .withBody(message)
                                        .withMessageType(messageType)
                                        .withOriginationNumber(originationNumber)
                                )));
        client.sendMessages(request);

        String volunteerMessage = "Hello " + volunteer + ", thank you for using NextDoorShopper. Your service is " +
                "invaluable";

        String phoneNumberVolunteer = "+1" + volunteerPhoneNumber;

        String originationNumberVolunteer = "+19095314210";
        String appIdVolunteer = "ac3bd51844c644b9affc6df79c3434ea";
        String messageTypeVolunteer = "TRANSACTIONAL";

        Map<String, AddressConfiguration> addressMapVolunteer =
                new HashMap<>();

        addressMapVolunteer.put(phoneNumberVolunteer, new AddressConfiguration()
                .withChannelType(ChannelType.SMS));

        SendMessagesRequest requestVolunteer = new SendMessagesRequest()
                .withApplicationId(appIdVolunteer)
                .withMessageRequest(new MessageRequest()
                        .withAddresses(addressMapVolunteer)
                        .withMessageConfiguration(new DirectMessageConfiguration()
                                .withSMSMessage(new SMSMessage()
                                        .withBody(volunteerMessage)
                                        .withMessageType(messageTypeVolunteer)
                                        .withOriginationNumber(originationNumberVolunteer)
                                )));
        client.sendMessages(requestVolunteer);
    }




}
