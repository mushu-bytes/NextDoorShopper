package helloworld;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.*;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
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

//maybe i can use this after the session expires.
public class SendOrder implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final AmazonDynamoDB ddb = AmazonDynamoDBClientBuilder
            .standard()
            .withRegion(Regions.US_WEST_1)
            .build();


    private final AmazonPinpoint client = AmazonPinpointClientBuilder.standard()
            .build();

    private HashMap<String, String> map = new HashMap<>();
    private HashMap<String, AttributeValue> attributeMap = new HashMap<>();
    private HashMap<String, String> smsMap = new HashMap<>();

    private static final String TABLE_NAME = "orderlog";
    private Map<String, String> jMap;



    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        HashMap<String, String> header = new HashMap<>();
        header.put("Access-Control-Allow-Origin", "*");
        header.put("Access-Control-Allow-Credentials", "true");
        //parse json body into a map. put the orderid into query for id
        try{
            ObjectMapper mapper  = new ObjectMapper();
            jMap = mapper.readValue(input.getBody(), Map.class);
            //get orderid of the customer
            String OrderId = jMap.get("OrderId");

            //get the data associated with that orderid
            smsMap = queryForOrderId(OrderId);
            //check if pending
            String availability = smsMap.get("availability");

            if (availability.equals("Pending")) {
                return new APIGatewayProxyResponseEvent()
                    .withBody("Order Taken")
                    .withHeaders(header)
                    .withStatusCode(400);


            }
            System.out.println("STILL GOING");
            //turn volunteer, phoneNumber, and verification to attribute values

            AttributeValue volunteer = new AttributeValue().withS(jMap.get("volunteer"));
            AttributeValue phoneNumber = new AttributeValue().withS(jMap.get("phoneNumber"));


            //put into dynamoDb

            attributeMap.put("volunteer", volunteer);
            attributeMap.put("PhoneNumber", phoneNumber);

            //put volunteer data into dynamodb
            putInfo(attributeMap, OrderId);

            //call the sms text function
            sms(smsMap, jMap.get("volunteer"), jMap.get("phoneNumber"));


        }catch(IOException e) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("NOT OK");
        }



        return new APIGatewayProxyResponseEvent()
                .withHeaders(header)
                .withStatusCode(200);
    }

    private HashMap queryForOrderId(String OrderId) {
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
        map.put("order", finalResults.get(0).get("order"));
        map.put("availability", finalResults.get(0).get("availability"));


        return map;
    }

    private void sms (HashMap smsMap, String name, String phone) {

        String customer = smsMap.get("name").toString();
        String order = smsMap.get("order").toString();

        //String address = smsMap.get("address").toString();

        String message = "Hello " + name + ", here is your request:" +
                "             Customer: " + customer + " | Order: " + order;
                //"           Text 'COMPLETE' in all caps once you have successfully delivered your order." +
                //"   Remember to always wear a mask and gloves at all times and beware of entering any dangerous areas."
                ;

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

    }
    private void putInfo(Map<String, AttributeValue> parameters, String OrderId) {
        //volunteer
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("OrderId", new AttributeValue().withS(OrderId));

        Map<String, AttributeValue> attributeValues = new HashMap<>();
        attributeValues.put(":volunteer", parameters.get("volunteer"));

        UpdateItemRequest updateItemRequest = new UpdateItemRequest()
                .withTableName(TABLE_NAME)
                .withKey(key)
                .withUpdateExpression("set volunteer = :volunteer")
                .withExpressionAttributeValues(attributeValues);

        UpdateItemResult result = ddb.updateItem(updateItemRequest);


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
}
