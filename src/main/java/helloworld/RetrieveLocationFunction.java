package helloworld;


import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.Attribute;
import com.amazonaws.services.dynamodbv2.document.Index;
import com.amazonaws.services.dynamodbv2.document.ItemCollection;
import com.amazonaws.services.dynamodbv2.document.QueryOutcome;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.GlobalSecondaryIndex;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;
import com.amazonaws.services.dynamodbv2.model.QueryResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RetrieveLocationFunction implements RequestHandler <APIGatewayV2HTTPEvent, Void> {
    private final AmazonDynamoDB ddb = AmazonDynamoDBClientBuilder.defaultClient();
    private static final String TABLE_NAME = "orderlog";

    public Void handleRequest(APIGatewayV2HTTPEvent apiGatewayV2HTTPEvent, Context context) {

        GetLocation();
        return null;
    }
    private List GetLocation() {
        QueryRequest request = new QueryRequest()
                .withTableName(TABLE_NAME)
                .withKeyConditionExpression("location");


        //ddb.query(request);
        QueryResult result = ddb.query(request);

        List<Map<String, AttributeValue>> locations = result.getItems();

        if (locations.size() != 0) {
            return locations;
        }
        else{
            return null;
        }

    }

}
