package com.sap.cloud.lm.sl.cf.core.helpers.v1;

import static com.sap.cloud.lm.sl.cf.core.util.ConfigurationEntriesUtil.PROVIDER_NID;
import static com.sap.cloud.lm.sl.mta.util.PropertiesUtil.getOptionalParameter;
import static com.sap.cloud.lm.sl.mta.util.PropertiesUtil.getRequiredParameter;
import static com.sap.cloud.lm.sl.mta.util.PropertiesUtil.mergeProperties;

import java.util.Map;

import com.sap.cloud.lm.sl.cf.core.dao.filters.ConfigurationFilter;
import com.sap.cloud.lm.sl.cf.core.model.CloudTarget;
import com.sap.cloud.lm.sl.cf.core.model.SupportedParameters;
import com.sap.cloud.lm.sl.cf.core.util.ConfigurationEntriesUtil;
import com.sap.cloud.lm.sl.common.util.Pair;
import com.sap.cloud.lm.sl.mta.builders.v1.PropertiesChainBuilder;
import com.sap.cloud.lm.sl.mta.model.v1.Platform;
import com.sap.cloud.lm.sl.mta.model.v1.Resource;
import com.sap.cloud.lm.sl.mta.model.v1.Target;

public class ConfigurationFilterParser {

    private static final String NEW_SYNTAX_FILTER = "configuration";
    private static final String OLD_SYNTAX_FILTER = "mta-provides-dependency";

    protected Platform platform;
    protected Target target;
    protected PropertiesChainBuilder chainBuilder;

    public ConfigurationFilterParser(Platform platform, Target target, PropertiesChainBuilder chainBuilder) {
        this.platform = platform;
        this.target = target;
        this.chainBuilder = chainBuilder;
    }

    public ConfigurationFilter parse(Resource resource) {
        String type = getType(resource);
        if (OLD_SYNTAX_FILTER.equals(type)) {
            return parseOldSyntaxFilter(resource);
        }
        if (NEW_SYNTAX_FILTER.equals(type)) {
            return parseNewSyntaxFilter(resource);
        }
        return null;
    }

    private String getType(Resource resource) {
        Map<String, Object> mergedParameters = mergeProperties(chainBuilder.buildResourceChain(resource.getName()));
        return (String) mergedParameters.get(SupportedParameters.TYPE);
    }

    private ConfigurationFilter parseOldSyntaxFilter(Resource resource) {
        Map<String, Object> parameters = getParameters(resource);
        String mtaId = getRequiredParameter(parameters, SupportedParameters.MTA_ID);
        CloudTarget cloudTarget = getCurrentOrgAndSpace();
        String mtaProvidesDependency = getRequiredParameter(parameters, SupportedParameters.MTA_PROVIDES_DEPENDENCY);
        String mtaVersion = getRequiredParameter(parameters, SupportedParameters.MTA_VERSION);
        String providerId = ConfigurationEntriesUtil.computeProviderId(mtaId, mtaProvidesDependency);
        return new ConfigurationFilter(PROVIDER_NID, providerId, mtaVersion, cloudTarget, null);
    }

    private ConfigurationFilter parseNewSyntaxFilter(Resource resource) {
        Map<String, Object> parameters = getParameters(resource);
        String version = getOptionalParameter(parameters, SupportedParameters.VERSION);
        String namespaceId = getOptionalParameter(parameters, SupportedParameters.PROVIDER_NID);
        String pid = getOptionalParameter(parameters, SupportedParameters.PROVIDER_ID);
        Map<String, Object> filter = getOptionalParameter(parameters, SupportedParameters.FILTER);
        Map<String, Object> target = getOptionalParameter(parameters, SupportedParameters.TARGET);
        boolean hasExplicitTarget = target != null;
        CloudTarget cloudTarget = hasExplicitTarget ? parseSpaceTarget(target) : getCurrentOrgAndSpace();
        return new ConfigurationFilter(namespaceId, pid, version, cloudTarget, filter, hasExplicitTarget);

    }

    private CloudTarget parseSpaceTarget(Map<String, Object> target) {
        String org = getRequiredParameter(target, SupportedParameters.ORG);
        String space = getRequiredParameter(target, SupportedParameters.SPACE);
        return new CloudTarget(org, space);
    }

    protected Map<String, Object> getParameters(Resource resource) {
        return resource.getProperties();
    }

    protected CloudTarget getCurrentOrgAndSpace() {
        Pair<String, String> currentOrgSpace = new OrgAndSpaceHelper(target, platform).getOrgAndSpace();
        return new CloudTarget(currentOrgSpace._1, currentOrgSpace._2);
    }
}
