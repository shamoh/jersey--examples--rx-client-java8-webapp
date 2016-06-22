# jersey--examples--rx-client-java8-webapp
This is example of **Jersey's Observable** (**RxJava**) client extension using **Netflix Hystrix** _latency and fault tolerant_ library.

The example extends official <a href="https://github.com/jersey/jersey/">**Jersey**</a> <a href="https://github.com/jersey/jersey/tree/master/examples/rx-client-java8-webapp">examples/rx-client-java8-webapp</a> example.

For details see my <a href="http://yatel.kramolis.cz/2015/01/reactive-jersey-client-rxjava-hystrix.html">Reactive Jersey Client using Java 8, RxJava and Netflix Hystrix</a> blog post.

## Running the Example

Run the example as follows:

> mvn clean package jetty:run

This deploys current example using Jetty. You can access the application at:

* <a href="http://localhost:8080/rx/agent/sync">http://localhost:8080/rx/agent/sync</a>
* <a href="http://localhost:8080/rx/agent/async">http://localhost:8080/rx/agent/async</a><
* <a href="http://localhost:8080/rx/agent/listenable">http://localhost:8080/rx/agent/listenable</a>
* <a href="http://localhost:8080/rx/agent/observable">http://localhost:8080/rx/agent/observable</a>
* <a href="http://localhost:8080/rx/agent/hystrix">http://localhost:8080/rx/agent/hystrix</a> -- **this one is new !**
* <a href="http://localhost:8080/rx/agent/completion">http://localhost:8080/rx/agent/completion</a>

## Code

* <a href="https://github.com/shamoh/jersey--examples--rx-client-java8-webapp/blob/master/src/main/java/org/glassfish/jersey/examples/rx/agent/HystrixObservableAgentResource.java">HystrixObservableAgentResource.java</a> - Hystrix Observable JAX-RS Resource class
* <a href="https://github.com/shamoh/jersey--examples--rx-client-java8-webapp/blob/master/src/test/java/org/glassfish/jersey/examples/rx/RxClientsTest.java#L148">RxClientsTest.java</a> - unit test `testHystrixRxObservableClient` method. Focus on `ProcessingTime` assertion.

You can be sure the endpoint execution processing time is less than 950 milliseconds. This is because of using Hystrix with specified execution timeout. And that's the reason the response body is not sometimes complete, but on time.
