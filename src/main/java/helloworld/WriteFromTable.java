package helloworld;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.*;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.simpledb.model.Attribute;
import com.amazonaws.services.simpledb.model.Item;

/**
 * Handler for requests to Lambda function.
 */




public class WriteFromTable implements RequestHandler<APIGatewayV2HTTPEvent, Void> {
    private final HashMap<String, AttributeValue> map = new HashMap<String, AttributeValue>();

    private final AmazonDynamoDB ddb = AmazonDynamoDBClientBuilder.defaultClient();
    private static final String TABLE_NAME = "orderlog";

    public Void handleRequest(final APIGatewayV2HTTPEvent input, final Context context) {
        String id;

        UUID uid= new UUID(32, 32).randomUUID();
        id = uid.toString();


        //converts all of the input into attribute values for the map
        AttributeValue orderid = new AttributeValue().withS(input.getQueryStringParameters().get(id));
        AttributeValue user = new AttributeValue().withS(input.getQueryStringParameters().get("user"));
        AttributeValue address = new AttributeValue().withS(input.getQueryStringParameters().get("address"));
        AttributeValue cost = new AttributeValue().withS(input.getQueryStringParameters().get("cost"));
        AttributeValue number = new AttributeValue().withS(input.getQueryStringParameters().get("number"));
        AttributeValue email = new AttributeValue().withS(input.getQueryStringParameters().get("email"));
        AttributeValue order = new AttributeValue().withS(input.getQueryStringParameters().get("order"));

        //checks to see if the user inputted something useful, if not returns null. I need to add what happens after that
        //j call a different function that sends to API Gateway that stuff is incorrect
        map.put("id", orderid);

        map.put(input.getQueryStringParameters().get("user"), user);
        if (map.get(input.getQueryStringParameters().get("user")) == null) {
            return null;
        }
        map.put(input.getQueryStringParameters().get("cost"), cost);
        if (map.get(input.getQueryStringParameters().get("cost")) == null) {
            return null;
        }
        map.put(input.getQueryStringParameters().get("number"), number);
        if (map.get(input.getQueryStringParameters().get("number")) == null) {
            return null;
        }
        map.put(input.getQueryStringParameters().get("email"), email);
        if (map.get(input.getQueryStringParameters().get("email")) == null) {
            return null;
        }
        map.put(input.getQueryStringParameters().get("order"), order);
        if (map.get(input.getQueryStringParameters().get("order")) == null) {
            return null;
        }
        map.put(input.getQueryStringParameters().get("address"), address);
        if (map.get(input.getQueryStringParameters().get("address")) == null) {
            return null;
        }

        return null;


    }

    private String getPageContents(String address) throws IOException{
        URL url = new URL(address);
        try(BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()))) {
            return br.lines().collect(Collectors.joining(System.lineSeparator()));
        }
    }

    private void putRequest(Map<String, AttributeValue> parameters) {

        PutItemRequest request = new PutItemRequest().withItem(parameters);
        PutItemResult result = ddb.putItem(request);

    }


}
