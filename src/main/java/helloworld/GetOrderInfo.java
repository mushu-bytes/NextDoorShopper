package helloworld;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GetOrderInfo implements RequestHandler <APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final AmazonDynamoDB ddb = AmazonDynamoDBClientBuilder.defaultClient();
    private static final String TABLE_NAME = "orderlog";

    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent apiGatewayProxyRequestEvent, Context context) {

        return GetLocation();

    }

    private APIGatewayProxyResponseEvent GetLocation() {
//        Map<String, String> expressionAttributesNames = new HashMap<>();
//        expressionAttributesNames.put("#OrderId", "OrderId");
//        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
//        expressionAttributeValues.put(":OrderIdValue", new AttributeValue().withS("OrderId"));
//
//        QueryRequest request = new QueryRequest()
//                .withTableName(TABLE_NAME)
//                .withKeyConditionExpression("#OrderId = :OrderIdValue")
//                .withExpressionAttributeNames(expressionAttributesNames)
//                .withExpressionAttributeValues(expressionAttributeValues);
//
//        //result will be a list of a map
//        QueryResult result = ddb.query(request);
//        //convert it to json body string
//        String json = new Gson().toJson(result.getItems());
//        //return it as API Gateway Proxy Event
//        return new APIGatewayProxyResponseEvent()
//                .withBody(json);
//Maybe use uuid
        //scan via addresses
        List<String> addresses = new ArrayList<>();
        //scan attribute names based on address
        Map<String, String> attributeNames = new HashMap<>();
        attributeNames.put("#address", "address");
        //get address values
        Map<String, AttributeValue> attributeValues = new HashMap<String, AttributeValue>();
        attributeValues.put(":addressVal", new AttributeValue().withS("address"));

        ScanRequest scanRequest = new ScanRequest()
                .withTableName(TABLE_NAME)
                .withExpressionAttributeNames(attributeNames)
                .withExpressionAttributeValues(attributeValues)
                .withProjectionExpression("address");

        Map<String, AttributeValue> lastKey = null;
        ScanResult scanResult = ddb.scan(scanRequest);
        List<Map<String, AttributeValue>> results = scanResult.getItems();
        do {
            results.forEach(r ->addresses.add(r.get("address").getS()));
            lastKey = scanResult.getLastEvaluatedKey();
            scanRequest.setExclusiveStartKey(lastKey);
        } while(lastKey!= null);

        String json = new Gson().toJson(results);
        return new APIGatewayProxyResponseEvent()
                .withBody(json);
    }

    }

