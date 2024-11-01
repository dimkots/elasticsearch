/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.action.index;

import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.common.xcontent.support.XContentMapValues;
import org.elasticsearch.core.FixForMultiProject;
import org.elasticsearch.core.Strings;
import org.elasticsearch.test.cluster.ElasticsearchCluster;
import org.elasticsearch.test.cluster.local.LocalClusterSpecBuilder;
import org.elasticsearch.test.cluster.local.distribution.DistributionType;
import org.elasticsearch.test.rest.ESRestTestCase;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.TestName;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import static org.hamcrest.Matchers.is;

public class IndexDocumentMultiProjectIT extends ESRestTestCase {

    protected static final int NODE_NUM = 3;

    @ClassRule
    public static ElasticsearchCluster cluster = createCluster();

    @Rule
    public final TestName testNameRule = new TestName();

    @FixForMultiProject
    private static ElasticsearchCluster createCluster() {
        LocalClusterSpecBuilder<ElasticsearchCluster> clusterBuilder = ElasticsearchCluster.local()
            .nodes(NODE_NUM)
            .distribution(DistributionType.INTEG_TEST) // TODO multi-project: make this test suite work under the default distrib
            .module("multi-project")
            .setting("xpack.security.enabled", "false") // TODO multi-project: make this test suite work with Security enabled
            .setting("xpack.ml.enabled", "false"); // TODO multi-project: make this test suite work with ML enabled
        return clusterBuilder.build();
    }

    @Override
    protected String getTestRestCluster() {
        return cluster.getHttpAddresses();
    }

    public void testIndexDocumentMultiProject() throws Exception {
        List<String> projects = List.of(
            "projectid1" + testNameRule.getMethodName().toLowerCase(Locale.ROOT),
            "projectid2" + testNameRule.getMethodName().toLowerCase(Locale.ROOT)
        );

        for (String p : projects) {
            createProject(p);
        }

        String indexName = "testindex" + testNameRule.getMethodName().toLowerCase(Locale.ROOT);

        for (String p : projects) {
            Request putIndexRequest = new Request("PUT", "/" + indexName + "?wait_for_active_shards=all&master_timeout=999s&timeout=999s");
            putIndexRequest.setJsonEntity(Strings.format("""
                {
                    "settings": {
                      "number_of_shards": %d,
                      "number_of_replicas": %d
                    }
                }
                """, randomIntBetween(1, 3), randomIntBetween(0, NODE_NUM - 1)));
            setRequestProjectId(putIndexRequest, p);
            Response putIndexResponse = client().performRequest(putIndexRequest);
            assertOK(putIndexResponse);
            var putIndexResponseBodyMap = entityAsMap(putIndexResponse);
            assertTrue((boolean) XContentMapValues.extractValue("acknowledged", putIndexResponseBodyMap));
            assertTrue((boolean) XContentMapValues.extractValue("shards_acknowledged", putIndexResponseBodyMap));
            assertThat((String) XContentMapValues.extractValue("index", putIndexResponseBodyMap), is(indexName));
        }

        for (String p : projects) {
            Request indexDocumentRequest = new Request("POST", "/" + indexName + "/_doc");
            setRequestProjectId(indexDocumentRequest, p);
            indexDocumentRequest.setJsonEntity(Strings.format("""
                {
                    "index-field": "%s-doc"
                }
                """, p));
            Response indexDocumentResponse = client().performRequest(indexDocumentRequest);
            assertOK(indexDocumentResponse);
        }
    }

    private void createProject(String projectId) throws IOException {
        Request putProjectRequest = new Request("PUT", "/_project/" + projectId);
        Response putProjectResponse = adminClient().performRequest(putProjectRequest);
        assertOK(putProjectResponse);
    }

    private static void setRequestProjectId(Request request, String projectId) {
        RequestOptions.Builder options = RequestOptions.DEFAULT.toBuilder();
        options.addHeader("X-Elastic-Project-Id", projectId);
        request.setOptions(options);
    }
}
