/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.glassfish.jersey.examples.rx.agent;

import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixObservableCommand;
import org.glassfish.jersey.client.rx.rxjava.RxObservable;
import org.glassfish.jersey.examples.rx.domain.AgentResponse;
import org.glassfish.jersey.examples.rx.domain.Calculation;
import org.glassfish.jersey.examples.rx.domain.Destination;
import org.glassfish.jersey.examples.rx.domain.Forecast;
import org.glassfish.jersey.examples.rx.domain.Recommendation;
import org.glassfish.jersey.server.Uri;
import rx.Observable;
import rx.schedulers.Schedulers;

import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.GenericType;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Obtain information about visited (destination) and recommended (destination, forecast, price) places for "RxJava" user. Uses
 * RxJava Observable and Jersey Client to obtain the data.
 *
 * @author Libor Kramolis (libor.kramolis at oracle.com)
 */
@Singleton
@Path("agent/hystrix")
@Produces("application/json")
public class HystrixObservableAgentResource {

    private static final String RX_USER = "Hystrix/RxJava";
    private static final int TIMEOUT_OVERALL = 900;
    private static final int TIMEOUT_DESTINATION_VISITED = 600;
    private static final int TIMEOUT_DESTINATION_RECOMMENDED = 300;
    private static final int TIMEOUT_RECOMMENDATION = 900;
    private static final int TIMEOUT_FORECAST = 400;

    private static Logger LOGGER = Logger.getLogger(HystrixObservableAgentResource.class.getName());

    @Uri("remote/destination")
    private WebTarget destinationWebTarget;

    @Uri("remote/calculation/from/{from}/to/{to}")
    private WebTarget calculationWebTarget;

    @Uri("remote/forecast/{destination}")
    private WebTarget forecastWebTarget;

    @GET
    public void observable(@Suspended final AsyncResponse async) {
        new AgentResponseCommand().observe()
                .observeOn(Schedulers.io())
                .subscribe(async::resume, async::resume);
    }

    //
    // class AgentResponseCommand
    //
    private class AgentResponseCommand extends CommonCommand<AgentResponse> {
        public AgentResponseCommand() {
            super("--", TIMEOUT_OVERALL);
        }

        @Override
        protected Observable<AgentResponse> run() {
            final long time = System.nanoTime();
            Observable<List<Destination>> visited = new DestinationCommand("visited", TIMEOUT_DESTINATION_VISITED).observe();
            Observable<List<Recommendation>> recommendations = new RecommendationCommand(TIMEOUT_RECOMMENDATION).observe();
            return Observable.zip(visited, recommendations, (List<Destination> v, List<Recommendation> r) -> {
                AgentResponse agentResponse = new AgentResponse(v, r);
                agentResponse.setProcessingTime((System.nanoTime() - time) / 1000000);
                return agentResponse;
            });
        }

        @Override
        protected Observable<AgentResponse> getFallback() {
            handleErrors();
            return Observable.just(new AgentResponse());
        }
    } //class AgentResponseCommand

    //
    // class RecommendedCommand
    //
    private class RecommendationCommand extends CommonCommand<List<Recommendation>> {
        public RecommendationCommand(int timeout) {
            super("--", timeout);
        }

        @Override
        protected Observable<List<Recommendation>> run() {
            return new DestinationCommand("recommended", TIMEOUT_DESTINATION_RECOMMENDED).observe()
                    .flatMap(destinationList -> Observable.from(destinationList)
                            .flatMap(destination -> {
                                Observable<Forecast> forecasts = new ForecastCommand(destination).observe();
                                Observable<Calculation> calculations = new CalculationCommand(destination).observe();

                                return Observable.zip(forecasts, calculations, (Forecast f, Calculation c) ->
                                        new Recommendation(destination, f, c));
                            }))
                    .toList();
        }

        @Override
        protected Observable<List<Recommendation>> getFallback() {
            handleErrors();
            return Observable.just(Collections.emptyList());
        }
    } //class RecommendationCommand

    //
    // class DestinationCommand
    //
    private class DestinationCommand extends CommonCommand<List<Destination>> {
        private final String path;

        public DestinationCommand(String path, int timeout) {
            super("path=" + path, timeout);
            this.path = path;
        }

        @Override
        protected Observable<List<Destination>> run() {
            return RxObservable.from(destinationWebTarget).path(path)
                    .request().header("Rx-User", RX_USER).rx()
                    .get(new GenericType<List<Destination>>() {
                    });
        }

        @Override
        protected Observable<List<Destination>> getFallback() {
            handleErrors();
            return Observable.just(Collections.emptyList());
        }
    } //class DestinationCommand

    //
    // class ForecastCommand
    //
    private class ForecastCommand extends CommonCommand<Forecast> {
        private final Destination destination;

        public ForecastCommand(Destination destination) {
            super("destination=" + destination.getDestination(), TIMEOUT_FORECAST);
            this.destination = destination;
        }

        @Override
        protected Observable<Forecast> run() {
            return RxObservable.from(forecastWebTarget)
                    .resolveTemplate("destination", destination.getDestination())
                    .request().rx()
                    .get(Forecast.class);
        }

        @Override
        protected Observable<Forecast> getFallback() {
            handleErrors();
            return Observable.just(new Forecast(destination.getDestination(), "N/A"));
        }
    } //class ForecastCommand

    //
    // class CalculationCommand
    //
    private class CalculationCommand extends CommonCommand<Calculation> {
        private final Destination destination;

        public CalculationCommand(Destination destination) {
            super("destination=" + destination.getDestination(), TIMEOUT_FORECAST);
            this.destination = destination;
        }

        @Override
        protected Observable<Calculation> run() {
            return RxObservable.from(calculationWebTarget)
                    .resolveTemplate("from", "Moon").resolveTemplate("to", destination.getDestination())
                    .request().rx()
                    .get(Calculation.class);
        }

        @Override
        protected Observable<Calculation> getFallback() {
            handleErrors();
            return Observable.just(new Calculation("Moon", destination.getDestination(), -1));
        }
    } //class CalculationCommand

    //
    // class CommonCommand
    //
    private abstract class CommonCommand<R> extends HystrixObservableCommand<R> {
        private final String debugMessage;

        public CommonCommand(String debugMessage, int timeout) {
            super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("jersey-examples-rx-client-java8-webapp"))
                    .andCommandPropertiesDefaults(HystrixCommandProperties.Setter()
                            .withExecutionIsolationThreadTimeoutInMilliseconds(timeout)));
            this.debugMessage = debugMessage;
        }

        protected void handleErrors() {
            final String message;
            if (isFailedExecution()) {
                message = getMessagePrefix() + "FAILED: " + getFailedExecutionException().getMessage();
            } else if (isResponseTimedOut()) {
                message = getMessagePrefix() + "TIMED OUT";
            } else {
                message = getMessagePrefix() + "SOME OTHER FAILURE";
            }
            LOGGER.warning(message);
        }

        private String getMessagePrefix() {
            return this.getClass().getSimpleName() + " [" + debugMessage + "]: ";
        }
    } //class CommonCommand

}
