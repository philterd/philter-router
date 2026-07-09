/*
 * Copyright 2026 Philterd, LLC @ https://www.philterd.ai
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.philterd.router.audit;

import ai.philterd.router.model.RoutingDecision;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

/**
 * Writes the per-file routing-decision audit trail as structured JSON on a dedicated logger, separate
 * from operational logging. It records the content hash and routing facts only. It never records the
 * filename, path, extracted text, prompt, or response, so no un-redacted content reaches the log.
 */
public class AuditLogger {

    private static final Logger AUDIT = LogManager.getLogger("audit");

    private final ObjectMapper mapper = new ObjectMapper();

    public void routed(final String hash, final RoutingDecision decision,
                       final String language, final Map<String, String> classifications) {
        final ObjectNode node = mapper.createObjectNode();
        node.put("event", "routed");
        node.put("hash", hash);
        node.put("matchedRoute", decision.matchedRoute());
        node.put("engine", decision.engine());
        node.put("policy", decision.policy());
        node.put("isDefault", decision.isDefault());
        node.put("language", language);
        final ObjectNode labels = node.putObject("classifications");
        if (classifications != null) {
            classifications.forEach(labels::put);
        }
        emit(node);
    }

    public void failed(final String hash, final String reason) {
        final ObjectNode node = mapper.createObjectNode();
        node.put("event", "failed");
        node.put("hash", hash);
        node.put("reason", reason);
        emit(node);
    }

    private void emit(final ObjectNode node) {
        AUDIT.info(node.toString());
    }

}
