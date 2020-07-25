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
import java.util.stream.Collectors;

public class GetOrderInfo implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final AmazonDynamoDB ddb = AmazonDynamoDBClientBuilder.defaultClient();
    private static final String TABLE_NAME = "orderlog";

    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent apiGatewayProxyRequestEvent, Context context) {

        return GetLocation();

    }

    private APIGatewayProxyResponseEvent GetLocation() {

        //scan via addresses
        List<String> addresses = new ArrayList<>();
        //returns EVERYTHING
        ScanRequest scanRequest = new ScanRequest()
                .withTableName(TABLE_NAME);

        //keeps track of the last key it was on before it had to reset the scan result/results
        //scanning can only send batches at a time
        Map<String, AttributeValue> lastKey;

        ScanResult scanResult;
        List<Map<String, AttributeValue>> results = new ArrayList<>();
        do {
            scanResult = ddb.scan(scanRequest);
            //adding a list to a list
            results.addAll(scanResult.getItems());
            //keeps track of the key
            lastKey = scanResult.getLastEvaluatedKey();
            //sets the key
            scanRequest.setExclusiveStartKey(lastKey);
        } while (lastKey != null);
        //convert map values from List<map<string, attributevalues>> to List<map<string, string>>
        List<Map<String, String>> finalResults = new ArrayList<>();

        for (int i = 0; i < results.size(); i++) {
            Map<String, String> newMap = results.get(i)
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().getS()));
            finalResults.add(newMap);
        }

        //converts everything to a json body.
        String json = new Gson().toJson(finalResults);
        //For Cors
        HashMap<String, String> header = new HashMap<>();
        header.put("Access-Control-Allow-Origin", "*");
        header.put("Access-Control-Allow-Credentials", "true");
        return new APIGatewayProxyResponseEvent()
                .withBody(json)
                .withHeaders(header)
                .withStatusCode(200);
    }

}

