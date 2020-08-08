package helloworld;

import com.amazonaws.regions.Regions;

import com.amazonaws.services.chime.AmazonChime;
import com.amazonaws.services.chime.AmazonChimeClientBuilder;
import com.amazonaws.services.chime.model.CreateProxySessionRequest;
import com.amazonaws.services.chime.model.CreateProxySessionResult;
import com.amazonaws.services.chime.model.PutVoiceConnectorProxyRequest;
import com.amazonaws.services.chime.model.PutVoiceConnectorProxyResult;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;
import com.amazonaws.services.dynamodbv2.model.QueryResult;

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



public class SendInfo implements RequestHandler <APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final AmazonDynamoDB ddb = AmazonDynamoDBClientBuilder
            .standard()
            .withRegion(Regions.US_WEST_1)
            .build();
    private final AmazonPinpoint client = AmazonPinpointClientBuilder
            .standard()
            .build();
    private final AmazonChime chime = AmazonChimeClientBuilder
            .standard()
            .build();
    //names for queryig dynamodn
    private static final String TABLE_NAME = "orderlog";
    private static final String INDEX_NAME = "PhoneNumber-index";
    //map for holding the customerinfo after querying for it
    HashMap<String, String> map = new HashMap<>();
    HashMap<String, String> customerMap = new HashMap<>();
    Map<String, String> jMap;

    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        HashMap<String, String> header = new HashMap<>();
        header.put("Access-Control-Allow-Origin", "*");
        header.put("Access-Control-Allow-Credentials", "true");

        try {
            ObjectMapper mapper = new ObjectMapper();
            jMap = mapper.readValue(input.getBody(), Map.class);
        } catch (IOException e) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("NOT OK");
        }
        //volunteerinfo
        String Phone = jMap.get("phoneNumber");
        String Volunteer = jMap.get("volunteer");
        String OrderId = jMap.get("OrderId");
        //customerinfo
        map = queryForCustomer(OrderId);
        //createproxy
        String proxyPhone = createProxy(Phone, map.get("phone"));

        sendInfoVolunteer(Phone, Volunteer, proxyPhone);
        sendInfoCustomer(map.get("phone"), map.get("name"), proxyPhone);

        return new APIGatewayProxyResponseEvent()
                .withHeaders(header)
                .withBody("OK")
                .withStatusCode(200);

    }

    private HashMap queryForCustomer(String OrderId) {
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

        return customerMap;

    }

    private String createProxy(String volunteerPhone, String customerPhone) {
        String volunteerPhoneNumber = "+1" + volunteerPhone;
        String customerPhoneNumber = "+1" + customerPhone;

        PutVoiceConnectorProxyRequest proxyRequest = new PutVoiceConnectorProxyRequest()
                .withDefaultSessionExpiryMinutes(60)
                .withPhoneNumberPoolCountries("US")
                .withVoiceConnectorId("ddsnl94csjbhtmy7aume51");
        PutVoiceConnectorProxyResult proxyResult = chime.putVoiceConnectorProxy(proxyRequest);

        CreateProxySessionRequest sessionRequest = new CreateProxySessionRequest()
                .withCapabilities("Voice", "SMS")
                .withParticipantPhoneNumbers(volunteerPhoneNumber, customerPhoneNumber)
                .withVoiceConnectorId("ddsnl94csjbhtmy7aume51")
                .withExpiryMinutes(60);
        CreateProxySessionResult sessionResult = chime.createProxySession(sessionRequest);

        return sessionResult.getProxySession().getParticipants().get(0).getProxyPhoneNumber();

    }


    private void sendInfoVolunteer(String Phone, String Volunteer, String proxyPhoneNumber) {
        System.out.println("Inside the SMS block");
        String message = "Hello " + Volunteer + ", thank you for using NextDoorShopper. You may use " +
                proxyPhoneNumber + " to contact your client, get to know them," +
                " and schedule a delivery. The phone number will expire in 1 hour. Remember you may always opt" +
                " out if you do not feel comfortable disclosing any sensitive information to them. " +
                "Reply 'REJECT ORDER' if you do not wish to go through with the delivery, 'COMPLETE' when " +
                "you have finished your order, and 'INCOMPLETE' if you are unable to carry out" +
                "your delivery.";

        String phoneNumber = "+1" + Phone;

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

    private void sendInfoCustomer(String Phone, String Customer, String proxyPhoneNumber) {
        System.out.println("Inside the SMS block");
        String message = "Hello " + Customer + ", thank you for using NextDoorShopper. You may use " +
                proxyPhoneNumber + " to contact your volunteer, get to know them," +
                ", and schedule a delivery. The phone number will expire in 1 hour. Remember you may always opt" +
                " out and request a new volunteer if you do not feel comfortable disclosing any sensitive information to them. " +
                "Reply 'New Volunteer' if you wish to find another volunteer";

        String phoneNumber = "+1" + Phone;

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
}

