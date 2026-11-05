package io.tokentrim.route;

import io.tokentrim.api.dto.ChatMessage;
import io.tokentrim.config.TokenTrimProperties;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ModelRouter {

    private final TokenTrimProperties properties;
    private final ComplexityScorer scorer;

    public ModelRouter(TokenTrimProperties properties, ComplexityScorer scorer) {
        this.properties = properties;
        this.scorer = scorer;
    }

    public RoutingDecision route(List<ChatMessage> messages, String requestedModel, String forceModel) {
        double complexity = scorer.score(messages);
        if (forceModel != null && !forceModel.isBlank()) {
            return new RoutingDecision(forceModel.strip(), "forced", complexity);
        }
        String requested = (requestedModel == null || requestedModel.isBlank())
                ? properties.getRouting().getFallbackModel()
                : requestedModel;
        TokenTrimProperties.Routing routing = properties.getRouting();
        if (!routing.isEnabled() || routing.isHonorRequestedModel()) {
            return new RoutingDecision(requested, "requested", complexity);
        }
        List<TokenTrimProperties.RouteRule> rules = new ArrayList<>(routing.getRoutes());
        rules.sort(Comparator.comparingDouble(TokenTrimProperties.RouteRule::getMaxComplexity));
        for (TokenTrimProperties.RouteRule rule : rules) {
            if (rule.getModel() == null || rule.getModel().isBlank()) {
                continue;
            }
            if (complexity <= rule.getMaxComplexity() + 1e-9) {
                String name = rule.getName() == null || rule.getName().isBlank() ? rule.getModel() : rule.getName();
                return new RoutingDecision(rule.getModel(), name, complexity);
            }
        }
        if (!rules.isEmpty() && rules.get(rules.size() - 1).getModel() != null) {
            TokenTrimProperties.RouteRule last = rules.get(rules.size() - 1);
            return new RoutingDecision(last.getModel(), last.getName() == null ? "fallback" : last.getName(), complexity);
        }
        return new RoutingDecision(requested, "requested", complexity);
    }
}
