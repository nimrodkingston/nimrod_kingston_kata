# CLAUDE.md
This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Technical context
This is an HTTP service written in Quarkus which is expected to handle large amounts of incoming requests as well as writes to persistent state.

The persistent state of choice for this service is PostgreSQL

## Business context
This is a payment service which is expected to receive requests for payment processing with high concurrency.
Retries for requests are expected to occur due to timeouts, network failures and upstream behaviour.

Each payment will arrive with a payment id which should be treated as a unique idempotent key per request, 
meaning that if a payment with a given payment id has already been processed then it should not be processed again.

The service must ensure that each payment with a given identifier is processed only once, even if duplicates are requested nearly at the same time.

## Payment request shape
This is the shape of an example payment message which can enter the system
``` json
{
    "paymentId": "PAY-123",
    "customerId": "CUST-001",
    "amount": 125.50,
    "currency": "GBP"
}
```
As part of handling payment requests, some basic checks and validation should be done on the contents of the request.
This can include:
- The paymentId and customerId must be included as part of the request
- The amount must be greater than 0
- The currency is required and a consistent format should be used e.g. ISO uppercase alpha codes

## Expected client responses
- If a payment with a unique payment id has been received for the first time, the payment should be stored once with a success reported (200)
- If a duplicate payment arrives where a payment with the same payment id has already been processed, the second payment should not be stored with a conflict reported (409)
- If any validation errors occur from the request then report a bad request (400)

## Testing method
There should be tests at different layers and part of the system in order to ensure correctness of behaviour and to aid implementation

The main areas of testing are:
-   API testing - Ensuring that incoming http requests with incorrectly shaped/missing data is rejected appropriately and successful messages are returned as expected
-   Service level business logic - Ensuring that the rules of how payments should be accepted and stored or ignored due to duplication are adhered to
-   Concurrency testing - Ensuring that a concurrent requester attempting to access the system under test does not cause duplication of payments or unexpected behaviour

## Testing style
Pointers to styling for consistent tests:
- There should be no member visibility defined on any test classes or methods, these should be left as package private if possible
- Prefix all test methods with test
- When producing test method names do not put any implementation or the outcome in the method name, only specify the state/behaviour which is occurring e.g. testDuplicatePaymentReceived.
- Comments and test methods should be implementation/technology agnostic and should only describe high level behaviour
- Write tests with a Given -> When -> Then structure where given is the state which is setup initially, when is the action/trigger which occurs and then is the assertions to test on
  e.g 
```
  //Given I have a payment which has been processed
  //When I send a request with the same idempotency key
  // Then the state should not be stored and I should receive a conflict response
  ```

## Additional prompting
If there is anything which is mentioned as part of prompting which seems incorrect or contradicts information that has been provided, mention this so that the details can be amended