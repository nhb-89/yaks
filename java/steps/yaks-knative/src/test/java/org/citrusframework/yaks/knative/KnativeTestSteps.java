/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.citrusframework.yaks.knative;

import com.consol.citrus.Citrus;
import com.consol.citrus.TestCaseRunner;
import com.consol.citrus.actions.AbstractTestAction;
import com.consol.citrus.annotations.CitrusFramework;
import com.consol.citrus.annotations.CitrusResource;
import com.consol.citrus.context.TestContext;
import com.consol.citrus.http.message.HttpMessage;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.fabric8.knative.client.KnativeClient;
import io.fabric8.knative.eventing.v1.Broker;
import io.fabric8.knative.eventing.v1.BrokerStatus;
import io.fabric8.knative.eventing.v1.TriggerList;
import io.fabric8.knative.internal.pkg.apis.Condition;
import io.fabric8.knative.messaging.v1.SubscriptionList;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.assertj.core.api.Assertions;
import org.citrusframework.yaks.knative.actions.KnativeAction;
import org.citrusframework.yaks.knative.ce.CloudEventSupport;
import org.citrusframework.yaks.kubernetes.KubernetesSupport;
import org.springframework.http.HttpStatus;

import static com.consol.citrus.http.actions.HttpActionBuilder.http;

/**
 * @author Christoph Deppisch
 */
public class KnativeTestSteps {

    @CitrusResource
    private TestCaseRunner runner;

    @CitrusFramework
    private Citrus citrus;

    @Given("^create test event$")
    public void sendTestEvents(String json) {
        HttpMessage eventRequest = CloudEventSupport.createEventMessage("", CloudEventSupport.attributesFromJson(json));

        runner.run(http().client("http://localhost:${knativeServicePort}/")
                        .send()
                        .post()
                        .fork(true)
                        .message(eventRequest));
    }

    @Given("^activate Knative broker ([^\\s]+)$")
    public void activateBroker(String brokerName) {
        runner.run(new KnativeTestAction() {
            @Override
            public void doExecute(TestContext context) {
                Broker broker = getKnativeClient()
                        .brokers()
                        .inNamespace(namespace(context))
                        .withName(brokerName)
                        .get();

                Assertions.assertThat(broker).isNotNull();

                BrokerStatus status = new BrokerStatus();
                Condition ready = new Condition();
                ready.setType("Ready");
                ready.setStatus("True");
                status.getConditions().add(ready);
                broker.setStatus(status);

                getKnativeClient()
                        .brokers()
                        .inNamespace(namespace(context))
                        .updateStatus(broker);
            }
        });
    }

    @Then("^verify test event accepted$")
    public void verifyEventAccepted() {
        runner.run(http().client("http://localhost:${knativeServicePort}/")
                .receive()
                .response(HttpStatus.ACCEPTED));
    }

    @Then("^verify Knative trigger ([^\\s]+) exists$")
    public void verifyTrigger(String triggerName) {
        runner.run(new KnativeTestAction() {
            @Override
            public void doExecute(TestContext context) {
                TriggerList triggers = getKnativeClient()
                        .triggers()
                        .inNamespace(namespace(context))
                        .list();

                Assertions.assertThat(triggers).isNotNull();
                Assertions.assertThat(triggers.getItems()).isNotEmpty();
                Assertions.assertThat(triggers.getItems().size()).isEqualTo(1);
                Assertions.assertThat(triggers.getItems().get(0).getMetadata().getName()).isEqualTo(triggerName);
            }
        });
    }

    @Then("^verify Knative trigger ([^\\s]+) exists with filter$")
    public void verifyTrigger(String triggerName, DataTable filter) {
        runner.run(new KnativeTestAction() {
            @Override
            public void doExecute(TestContext context) {
                TriggerList triggers = getKnativeClient()
                        .triggers()
                        .inNamespace(namespace(context))
                        .list();

                Assertions.assertThat(triggers).isNotNull();
                Assertions.assertThat(triggers.getItems()).isNotEmpty();
                Assertions.assertThat(triggers.getItems().size()).isEqualTo(1);
                Assertions.assertThat(triggers.getItems().get(0).getMetadata().getName()).isEqualTo(triggerName);
                Assertions.assertThat(triggers.getItems().get(0).getSpec().getFilter().getAttributes().size()).isEqualTo(filter.height());
                filter.asMap(String.class, String.class).forEach((key, value) -> {
                    Assertions.assertThat(triggers.getItems().get(0).getSpec().getFilter().getAttributes()).containsKey(key.toString());
                    Assertions.assertThat(triggers.getItems().get(0).getSpec().getFilter().getAttributes().get(key.toString())).isEqualTo(value);
                });
            }
        });
    }

    @Then("^verify Knative channel ([^\\s]+) exists$")
    public void verifyChannel(String channelName) {
        runner.run(new KnativeTestAction() {
            @Override
            public void doExecute(TestContext context) {
                Assertions.assertThat(getKnativeClient()
                        .channels()
                        .inNamespace(namespace(context))
                        .withName(channelName)
                        .get()).isNotNull();
            }
        });
    }

    @Then("^verify service ([^\\s]+) subscribes to Knative channel ([^\\s]+)$")
    public void verifyChannel(String serviceName, String channelName) {
        runner.run(new KnativeTestAction() {
            @Override
            public void doExecute(TestContext context) {
                SubscriptionList subscriptions = getKnativeClient()
                        .subscriptions()
                        .inNamespace(namespace(context))
                        .list();

                Assertions.assertThat(subscriptions).isNotNull();
                Assertions.assertThat(subscriptions.getItems()).isNotEmpty();
                Assertions.assertThat(subscriptions.getItems().size()).isEqualTo(1);
                Assertions.assertThat(subscriptions.getItems().get(0).getMetadata().getName()).isEqualTo(serviceName + "-subscription");
                Assertions.assertThat(subscriptions.getItems().get(0).getSpec().getChannel().getName()).isEqualTo(channelName);
                Assertions.assertThat(subscriptions.getItems().get(0).getSpec().getSubscriber().getRef().getName()).isEqualTo(serviceName);
            }
        });
    }

    private abstract class KnativeTestAction extends AbstractTestAction implements KnativeAction {
        @Override
        public KubernetesClient getKubernetesClient() {
            return KubernetesSupport.getKubernetesClient(citrus);
        }

        @Override
        public KnativeClient getKnativeClient() {
            return KnativeSupport.getKnativeClient(citrus);
        }
    }
}
