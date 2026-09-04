## Technical prerequisites
All local development was done in a Debian instance using java 26-temurin. However, this service has also been tested with Java21-temurin and is still viable

Minimum requirements:
- Apache Maven 3.90
- Java21 +
- A working container runtime (Docker 20.10+)

### Useful commands
Whilst in the root directory run this to build and run the service with the dev profile 
```bash
./mvnw quarkus:dev
```
The dev profile uses test containers for PostgreSQL instances so will spin up the database as part of running.

To run all tests use
```bash
./mvnw test
```
Filters can be added for specific test classes such as
```bash
./mvnw test -Dtest=FooBarTest
```
NOTE: The application.properties file has been explicitly defined to not mount or save data across runs so stopping the service and bringing it back up.
This is primarily due to using the hibernate create strategy as a shorthand for creating persistent resources upon startup. For this to be improved 
some form of database migration could be introduced to separate database generation e.g. Liquibase, flyway etc

## Request and Rest layer
### Processing new payments
This service supports processing of new payments which includes persisting payment records into a PostgreSQL database 
along with sending messages out to an in-memory channel.

*Example request*
``` bash
curl -i -X POST http://localhost/payments -H "Content-Type: application/json" -d '{"paymentId": "some-paymentId3", "customerId": "someCustomerId", "amount": 12.5, "currency": "GBP"}'
```

If the payment has not been processed already a 201 response will be returned along with a timestamp at the time of the response being returned.

If payment has already been persisted into the PostgreSQL table, a conflict will be recognised and a 409 will be returned.

If any other unhandled exception occurs, then a 500 response code will be returned to the client as a server error.

## Overall Package structure
To allow for a clear separation of responsibilities and a clear picture of what the business logic of th system is,
I have chosen to split my source code into the following structure
- dto
- exception
- model
- resource
- service
- repository

As a personal choice I have created a separate repository package to separate my business logic from the data access layer.
Although this makes the separation of concerns clear for the service, I have chosen my repository exceptions to be slightly
more aware of business logic e.g. catching a constraint exception which is a specific database detail and re-throwing a 
DuplicatePaymentException, meaning that the repository is now changing the assertion of what a constraint exception is

### Retrieving existing payments
This service also supports retrieving already processed payments via the unique payment id which returns a payload 
consisting of the paymentId, customerId, amount and currency
*Example request*
``` bash
http://localhost:8080/payments/some-paymentId
```

*Example response*
```json
{
  "payment": {
    "paymentId":"some-paymentId4",
    "customerId":"someCustomerId",
    "amount":12.5,
    "currency":"GBP"
  },
  "timeCreated":"2026-09-04T18:07:00.185195452Z"
}

```

*Example response*
``` json 
{
    "paymentId":
    "some-paymentId3",
    "customerId":"someCustomerId",
    "amount":12.50,
    "currency":"GBP"
}
```
### Rest Testing
In order to test the client facing behaviour from the resource, I have chosen to use the RestEasy api as a way of 
simulating rest requests coming into the system. In this layer I have chosen to mock the service layer as I want to test
against the validation and the behaviour of error codes and statuses which are returned rather than service behaviour.

## Data persistence 
### The service layer
Given that a layer of persistence was required for the payments which were attempted to be processed, 
I have chosen hibernate and JPA with repositories as being able to define entities to interact with in an 
object-oriented way felt like the right level of abstraction and provides an easy way of defining schemas in a local development environment.

Although I have had more experience with JOOQ as far as ORMs are concerned, JOOQ works best with an existing 
schema and data migration tools such as Liquibase/flyaway.

To keep this service production minded I have included the production application.properties options for the datasource 
connection which can simply be passed in as envars from whichever deployed environment this code is run in.

### Metrics
Metrics are also available as to how many times the payment service has received a conflict or processed a new payment.
These can be reached via
```http request
http://localhost:8080/q/metrics
```
### Testing with the database
Rather than using in-memory or mocked databases, I have chosen to use test containers as a method of testing my data 
persistence as I feel the database layer, specifically the unique constraints are an implementation detail which is 
worth testing in CI have used devservices within the test context in order to spin up a PostgreSQL instance while 
running tests.

## Idempotency and concurrency
The main sticking point and challenge of this task was to design this payment service with the consideration that payment 
requests arrive at the service multiple times for multiple reasons such as network errors, attempted retires or upstream 
attempts coming in more than once.

The approach I have gone for with limited time is that I have added a uniqueness constraint on the paymentId within 
the definition of the database entity.

This constraint prevents multiple payments with the same idempotency key from being processed as the isolation rules 
of the database would prevent both rows to fall within the same constraint check.

### Testing concurrency
To add some assurance that receiving multiple requests for the same payment would be handled properly, 
I have set up concurrency tests which will attempt to request payments across multiple threads however, 
these threads are held behind a barrier meaning that when the barrier is released all messages will 
be sent at the same time to resemble concurrency of multiple requests with the same payment-ids occurring in close proximity

## Limitations and improvements
### Database migration
Although hibernate setting up resources upon service start is convenient, allowing this strategy in production is
dangerous and inconsistent so a manual data migration tool such as Liquibase would still be needed prior to production
deployment. Quarkus also has direct support for these migration tools so would be worth investigation prior.
https://quarkus.io/guides/Liquibase/

### Concurrency
This implementation does work for simple cases but falls apart if in future this service has to
perform any other side effects before saving the payment to the database. A potential solution for this is to use
a separate table as the idempotency key validation, with the idea being that data is saved to this store before any
other processing occurs, meaning that all other payments with the same key are locked out.
https://stripe.com/blog/idempotency#:~:text=Guaranteeing%20%E2%80%9Cexactly%20once%E2%80%9D%20semantics

### Outbound messaging
As part of this kata I have also added messaging capabilities to an outbound channel. In this instance I have made it 
so that it would send messages to an in-memory channel however, this would obviously not be appropriate 
for a production environment so connector details for an actual implementation would have to be defined 
e.g. smallrye-reactive-messaging-aws-sqs

It should also be noted that due to the in-memory connector having to be an implementation detail, the pom.xml 
dependencies include the in-memory channel as a non-test dependency which is not ideal and should be changed

### Payments with different bodies
Although the unique index would prevent payments with the same payment-id from being added to the system, it should be
noted that two which have the same idempotency key with different bodies e.g. payment amount, customerId etc would still
be accepted. A potential solution would be to hash the request bodies and compare this at processing time however I did
not have time to implement this.

### AI declaration
As part of this kata I have used AI as a tool to generate test code as well as a consult for technical choices.
In order to review and control what was being generated, I have created a CLAUDE.MD file which I have added business 
and behaviour context for this task along with styling guides for tests etc.

In order to understand the resulting code I wrote, I followed a red green structure where I had Claude generate tests,
I wrote the simplest code manually until my code went green and then add changes to another layer to make tests go red again etc.

With the added context, prompts that I have used are to the effect of "I have implemented the service layer, 
can you add test implementation along with analysis of any edge cases?" And general querying of technical documents 
such as "I want to integrate dev containers into my tests, will my application.properties file need to updated?"