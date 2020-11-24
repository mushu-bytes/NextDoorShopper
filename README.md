# NextDoorShopper

This project will allow the elderly to request grocery orders from volunteers. As soon as you enter the order page, GetOrderInfo.java is automatically called from the front-end. GetOrderInfo.java scans DynamoDB, returning a json body of all order-related data from DynamoDB to the website in order to create markers and windowboxes displaying orders. In order to mask their location, the website only requests your zipcode and alters the coordinates so that the marker will land in a different location each time (within a 1 mile radius). Normally, it will take a couple moments before the order is displayed. 

After making an order, user-inputted information is sent over the wire to API Gateway where the request will be redirected to WriteToTable.java. WriteToTable.java will unwrap the json body into a HashMap, where its components will be converted into AttributeValues and written into DynamoDB. 

When a volunteer takes an order, PendingOrderInfo.java is called in order to check whether the order is already pending or not. If it is already pending, the order will not be taken, if not, PendingOrderInfo.java will update DynamoDB with the volunteer's name and contact info. Afterwards, SendInfo.java will be called, creating a proxy phone session via Amazon Chime. The proxy phone number will then be sent to both the volunteer and the customer who requested the order. 

If either the volunteer or the customer cancels the order, they can text back to AWS Pinpoint. If AWS Pinpoint spots a keyword, such as "COMPLETE", "INCOMPLETE", "NEW VOLUNTEER", and "REJECT ORDER", MarkAsFinished will be immediately called and will update DynamoDB and the users depending on the response. For example, if the volunteer or the user texts back "COMPLETE", MarkAsFinished will delete the order and send a text message saying thanks. If they text back "INCOMPLETE", the order will be reset to "available" instead of "pending" and boths users will be informed. 

If an order is completed, LogCompletedOrders.java will read the DynamoDB stream in order to see if an order is removed and will write the order info into a s3 file. 
