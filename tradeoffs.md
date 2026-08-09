# Tradeoffs

A payment gateway for FicMart, a fictional e-commerce platform, built with Java and Spring Boot.

**The gateway supports four payment operations:**

Authorize
Capture
Void
Refund

The implementation also includes a payment state machine, idempotency protection, Resilience4j retries with exponential backoff, structured exception handling, Mockito unit tests, and WireMock integration tests.


## ARCHITECTURE:
I implemented this project with this architecture in mind: Controller -> PaymentService -> BankClient(Interface) -> BankClientHttp -> RestClient -> Merchant's Bank. The PaymentService also uses the PaymentRepository to access the database, PostgreSQL in this case, and abstracts idempotency checks to IdempotencyService. My Controller here, like a middleman receiving HTTP requests, validating input, extracting headers/path variables, calling PaymentService, and returning response to the client, here, via APIs. @RestController tells Spring that the controller handles HTTP requests and returns data directly rather than rendering server-side views.
The PaymentService contains the core payment business logic. It handles state transitions, performs idempotency checks, calls the bank, and updates payment state.
The BankClient abstraction separates this application from the HTTP Implementation.

**Benefit of this Architecture**: PaymentService depends on the BankClient abstraction rather than the HTTP implementation. This keeps the business logic independent of how the external bank is accessed and also makes the service easier to unit test because BankClient can be mocked. BankClient stays as an interface to aid in testing. IdempotencyService is abstracted from PaymentService to keep PaymentService clean and focused on the Payment state methods.

**Tradeoff:** RestClient has a modern synchronous API and cleaner request pattern than the older RestTemplate. The tradeoff is that it remains a synchronous HTTP client, so each request consumes application resources while waiting for the bank. For this Payment gateway, synchronous communication is appropriate because the caller needs the bank's result before receiving the Payment response. At greater scale, asynchronous processing could improve resource utilization but it would make the API and consistency model more complicated.


## STATE MANAGEMENT:
The application has these payment states: (i)Pending -> Authorized -> Captured -> Refunded and (ii) Authorized -> Voided. In the Payment model, i implemented a canTransitionTo() method to help in the transition of each state so that payments can not transition into invalid states: a PENDING payment can only be AUTHORIZED, or FAILED, an AUTHORIZED payment can be CAPTURED, or VOIDED, and a CAPTURED payment can only be REFUNDED, not VOIDED.

**Benefit:** Keeping the transition rules close to the Payment domain object centralizes them and prevents the same rules from being duplicated across service methods.

**Tradeoff:** Strict transitions protect the system from invalid operations, but they also mean recovery from external-system inconsistencies requires additional mechanisms. The state machine validates what transitions are allowed, but it does not by itself guarantee that the database and external bank are always synchronized.


## FAILURE HANDLING
The failures, are classified and handled as BankTransientException(retry) and BankRejectedException(do not retry). The BankTransientException represents transient failures such as 5xx responses or network failures. This means, the bank might be unavailable, overloaded, experiencing network problem, retrying may allow the operation to succeed. However, a BankRejectedException which is a 4xx status error represents a client-side or business rejection, such as invalid request, or invalid payment state. Retrying the same request is unlikely to resolve the issue, so these exceptions are not retried. Retrying here will also cause unnecessary traffic. Another failure possibility is a partial failure between the application and the bank. The bank may Authorize a payment, but then the application may fail before persisting the updated payment state. The bank here can show the payment as Authorized while the database still shows it as pending. The idempotency key reduces the risk of duplicate processing when the operation is retried.

**Tradeoff:** Retries improves reliability but increases traffic, which is why exponential backoff is necessary.


## IDEMPOTENCY:
An idempotent application is one that produces the same side-effect on the server whether you make a request once or multiple times. Here, FicMart  makes a request, sends Idempotency-Key, say "abcde", through PaymentService, through IdempotencyService. If the key already exists in the server, the cached response is returned. If not, payment is processed, and response is saved. The database here stores idempotency_key, payment_id, operation, response_body, and created_at. This matters because the same request can make the bank charge twice without idempotency, but with it, the initial response will be saved, including the key, so when the same key is found in another request again, the previous response will be returned.

**Edge cases:** Same key, same request should return the cached response. a case where Request succeeds but saving idempotency record fails will need stronger transactional/concurrency handling around this.

**Tradeoff:** Concurrency. If two requests with same idempotency-key arrive at the same time, both requests could check the database for the key before either one creates the key. Without additional database-level protection, both requests can process the payment. The unique primary key on the idempotency key helps prevent duplicate records, but additional transactional handling, locking, or an explicit processing state would be needed to make the operation fully safe under concurrent requests.

## WHAT I WILL DO DIFFERENTLY:
This current design is appropriate for this scope, but in production I would

**i) Concurrency and idempotency:** Implement database concurrency to handle a case where 2 identical requests arrive simultaneously. Here, unique constraints and appropriate locking would be of help.

**ii) Observability:** I would implement Observability and add structured logging, metrics, distributed tracing and retry metrics.

**iii) Security:** This project stores card details directly just for demonstration. In production, I would avoid storing CVV and would use a payment-tokenization provider, encryption, strict access controls, and PCI-DSS-compliant handling of payment data.

**iv) Secrets:** The current implementation uses application-local.yml for secrets, which doesn't scale. In production I'd use a secrets manager like AWS Secrets Manager or HashiCorp Vault.

**v) Resilience:** I would complement retries with a circuit breaker. Retries help recover from transient failures, but if the bank is completely unavailable, continuing to retry every request can increase load on the systems. A circuit breaker would temporarily stop calls to a bank that is failing to allow the system to recover.

**vi) Timeouts:** I would review timeout values independently for connection and read operations and ensure they align with the retry policy.
