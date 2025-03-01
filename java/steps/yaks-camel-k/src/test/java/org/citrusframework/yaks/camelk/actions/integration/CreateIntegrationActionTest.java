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

package org.citrusframework.yaks.camelk.actions.integration;

import java.util.HashMap;

import com.consol.citrus.context.TestContext;
import com.consol.citrus.context.TestContextFactory;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesCrudDispatcher;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.mockwebserver.Context;
import okhttp3.mockwebserver.MockWebServer;
import org.citrusframework.yaks.camelk.model.Integration;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Christoph Deppisch
 */
public class CreateIntegrationActionTest {

    private final KubernetesMockServer k8sServer = new KubernetesMockServer(new Context(), new MockWebServer(),
            new HashMap<>(), new KubernetesCrudDispatcher(), false);

    private final KubernetesClient kubernetesClient = k8sServer.createClient();

    private final TestContext context = TestContextFactory.newInstance().getObject();

    @Test
    public void shouldCreateIntegrationWithTraits() {
        CreateIntegrationAction action = new CreateIntegrationAction.Builder()
                .client(kubernetesClient)
                .integration("helloworld")
                .source("from('timer:tick?period=1000').setBody().constant('Hello world from Camel K!').to('log:info')")
                .traits("quarkus.enabled=true,quarkus.native=true,route.enabled=true")
                .build();

        action.execute(context);

        Integration integration = kubernetesClient.resources(Integration.class).withName("helloworld").get();
        Assert.assertTrue(integration.getSpec().getTraits().containsKey("quarkus"));
        Assert.assertEquals(2, integration.getSpec().getTraits().get("quarkus").getConfiguration().size());
        Assert.assertEquals(true, integration.getSpec().getTraits().get("quarkus").getConfiguration().get("enabled"));
        Assert.assertEquals("true", integration.getSpec().getTraits().get("quarkus").getConfiguration().get("native"));
        Assert.assertTrue(integration.getSpec().getTraits().containsKey("route"));
        Assert.assertEquals(1, integration.getSpec().getTraits().get("route").getConfiguration().size());
        Assert.assertEquals(true, integration.getSpec().getTraits().get("route").getConfiguration().get("enabled"));
    }

    @Test
    public void shouldCreateIntegrationWithTraitModeline() {
        CreateIntegrationAction action = new CreateIntegrationAction.Builder()
                .client(kubernetesClient)
                .integration("foo")
                .source("// camel-k: trait=quarkus.enabled=true\n" +
                        "// camel-k: trait=quarkus.native=true\n" +
                        "// camel-k: trait=route.enabled=true\n" +
                        "from('timer:tick?period=1000').setBody().constant('Hello world from Camel K!').to('log:info')")
                .build();

        action.execute(context);

        Integration integration = kubernetesClient.resources(Integration.class).withName("foo").get();
        Assert.assertTrue(integration.getSpec().getTraits().containsKey("quarkus"));
        Assert.assertEquals(2, integration.getSpec().getTraits().get("quarkus").getConfiguration().size());
        Assert.assertEquals(true, integration.getSpec().getTraits().get("quarkus").getConfiguration().get("enabled"));
        Assert.assertEquals("true", integration.getSpec().getTraits().get("quarkus").getConfiguration().get("native"));
        Assert.assertTrue(integration.getSpec().getTraits().containsKey("route"));
        Assert.assertEquals(1, integration.getSpec().getTraits().get("route").getConfiguration().size());
        Assert.assertEquals(true, integration.getSpec().getTraits().get("route").getConfiguration().get("enabled"));
    }
}
