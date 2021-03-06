package com.sap.cloud.lm.sl.cf.core.cf.v1;

import static com.sap.cloud.lm.sl.mta.util.PropertiesUtil.getPropertiesList;
import static com.sap.cloud.lm.sl.mta.util.PropertiesUtil.mergeProperties;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.sap.cloud.lm.sl.cf.core.Constants;
import com.sap.cloud.lm.sl.cf.core.cf.HandlerFactory;
import com.sap.cloud.lm.sl.cf.core.helpers.MapToEnvironmentConverter;
import com.sap.cloud.lm.sl.cf.core.helpers.XsPlaceholderResolver;
import com.sap.cloud.lm.sl.cf.core.helpers.v1.PropertiesAccessor;
import com.sap.cloud.lm.sl.cf.core.model.SupportedParameters;
import com.sap.cloud.lm.sl.cf.core.util.CloudModelBuilderUtil;
import com.sap.cloud.lm.sl.common.util.MapUtil;
import com.sap.cloud.lm.sl.common.util.Pair;
import com.sap.cloud.lm.sl.mta.handlers.v1.DescriptorHandler;
import com.sap.cloud.lm.sl.mta.model.v1.DeploymentDescriptor;
import com.sap.cloud.lm.sl.mta.model.v1.Module;
import com.sap.cloud.lm.sl.mta.model.v1.ProvidedDependency;
import com.sap.cloud.lm.sl.mta.model.v1.Resource;

public class ApplicationEnvironmentCloudModelBuilder {

    private static final int MTA_MAJOR_VERSION = 1;

    protected CloudModelConfiguration configuration;
    protected DeploymentDescriptor deploymentDescriptor;
    protected XsPlaceholderResolver xsPlaceholderResolver;
    protected DescriptorHandler handler;
    protected PropertiesAccessor propertiesAccessor;
    protected String deployId;

    public ApplicationEnvironmentCloudModelBuilder(CloudModelConfiguration configuration, DeploymentDescriptor deploymentDescriptor,
        XsPlaceholderResolver xsPlaceholderResolver, DescriptorHandler handler, PropertiesAccessor propertiesAccessor, String deployId) {
        this.configuration = configuration;
        this.deploymentDescriptor = deploymentDescriptor;
        this.xsPlaceholderResolver = xsPlaceholderResolver;
        this.handler = handler;
        this.propertiesAccessor = propertiesAccessor;
        this.deployId = deployId;
    }

    public Map<Object, Object> build(Module module, List<String> services, List<String> sharedServices) {
        Set<String> specialModuleProperties = buildSpecialModulePropertiesSet();
        Map<String, Object> properties = propertiesAccessor.getProperties(module, specialModuleProperties);
        Map<String, Object> parameters = propertiesAccessor.getParameters(module, specialModuleProperties);

        Map<String, Object> env = new TreeMap<>();
        addMetadata(env, module);
        addServices(env, services);
        addSharedServices(env, sharedServices);
        addAttributes(env, parameters);
        addProperties(env, properties);
        addDependencies(env, module);
        return MapUtil.unmodifiable(new MapToEnvironmentConverter(configuration.isPrettyPrinting()).asEnv(env));
    }

    private Set<String> buildSpecialModulePropertiesSet() {
        Set<String> result = new HashSet<>();
        result.addAll(SupportedParameters.APP_PROPS);
        result.addAll(SupportedParameters.APP_ATTRIBUTES);
        result.addAll(SupportedParameters.SPECIAL_MT_PROPS);
        return result;
    }

    protected void addMetadata(Map<String, Object> env, Module module) {
        addMtaMetadata(env);
        addMtaModuleMetadata(env, module);
        addProvidedDependenciesMetadata(env, module);
    }

    protected void addMtaMetadata(Map<String, Object> env) {
        Map<String, Object> mtaMetadata = new TreeMap<>();
        MapUtil.addNonNull(mtaMetadata, Constants.ATTR_ID, deploymentDescriptor.getId());
        MapUtil.addNonNull(mtaMetadata, Constants.ATTR_VERSION, deploymentDescriptor.getVersion());
        MapUtil.addNonNull(mtaMetadata, Constants.ATTR_DESCRIPTION, deploymentDescriptor.getDescription());
        MapUtil.addNonNull(mtaMetadata, Constants.ATTR_PROVIDER, deploymentDescriptor.getProvider());
        MapUtil.addNonNull(mtaMetadata, Constants.ATTR_COPYRIGHT, deploymentDescriptor.getCopyright());
        env.put(Constants.ENV_MTA_METADATA, mtaMetadata);
    }

    protected void addMtaModuleMetadata(Map<String, Object> env, Module module) {
        Map<String, Object> mtaModuleMetadata = new TreeMap<>();
        MapUtil.addNonNull(mtaModuleMetadata, Constants.ATTR_NAME, module.getName());
        MapUtil.addNonNull(mtaModuleMetadata, Constants.ATTR_DESCRIPTION, module.getDescription());
        env.put(Constants.ENV_MTA_MODULE_METADATA, mtaModuleMetadata);
    }

    protected void addProvidedDependenciesMetadata(Map<String, Object> env, Module module) {
        List<String> mtaModuleProvidedDependencies = module.getProvidedDependencies1()
            .stream()
            .filter(CloudModelBuilderUtil::isPublic)
            .map(ProvidedDependency::getName)
            .collect(Collectors.toList());
        env.put(Constants.ENV_MTA_MODULE_PUBLIC_PROVIDED_DEPENDENCIES, mtaModuleProvidedDependencies);
    }

    protected void addServices(Map<String, Object> env, List<String> services) {
        env.put(Constants.ENV_MTA_SERVICES, services);
    }

    protected void addSharedServices(Map<String, Object> env, List<String> sharedServices) {
        env.put(Constants.ENV_MTA_SHARED_SERVICES, sharedServices);
    }

    protected void addAttributes(Map<String, Object> env, Map<String, Object> properties) {
        Map<String, Object> attributes = new TreeMap<>(properties);
        attributes.keySet()
            .retainAll(SupportedParameters.APP_ATTRIBUTES);
        resolveUrlsInAppAttributes(attributes);
        if (!attributes.isEmpty()) {
            env.put(Constants.ENV_DEPLOY_ATTRIBUTES, attributes);
        }
        Boolean checkDeployId = (Boolean) attributes.get(SupportedParameters.CHECK_DEPLOY_ID);
        if (checkDeployId != null && checkDeployId) {
            env.put(Constants.ENV_DEPLOY_ID, deployId);
        }
    }

    private void resolveUrlsInAppAttributes(Map<String, Object> properties) {
        String serviceUrl = (String) properties.get(SupportedParameters.REGISTER_SERVICE_URL_SERVICE_URL);
        String serviceBrokerUrl = (String) properties.get(SupportedParameters.SERVICE_BROKER_URL);
        if (serviceUrl != null) {
            properties.put(SupportedParameters.REGISTER_SERVICE_URL_SERVICE_URL, xsPlaceholderResolver.resolve(serviceUrl));
        }
        if (serviceBrokerUrl != null) {
            properties.put(SupportedParameters.SERVICE_BROKER_URL, xsPlaceholderResolver.resolve(serviceBrokerUrl));
        }
    }

    protected void addProperties(Map<String, Object> env, Map<String, Object> properties) {
        env.putAll(properties);
    }

    protected void addDependencies(Map<String, Object> env, Module module) {
        Map<String, List<Object>> groupsMap = new TreeMap<>();
        for (String dependency : module.getRequiredDependencies1()) {
            addDependency(dependency, env, groupsMap);
        }
        env.putAll(groupsMap);
    }

    protected void addDependency(String dependency, Map<String, Object> env, Map<String, List<Object>> groupsMap) {
        Pair<Resource, ProvidedDependency> pair = handler.findDependency(deploymentDescriptor, dependency);
        addProvidedDependency(env, groupsMap, pair._2);
        addResource(env, groupsMap, pair._1);
    }

    protected void addProvidedDependency(Map<String, Object> env, Map<String, List<Object>> groupsMap,
        ProvidedDependency providedDependency) {
        if (providedDependency != null) {
            addToGroupsOrEnvironment(env, groupsMap, providedDependency.getGroups(), providedDependency.getName(),
                gatherProperties(providedDependency));
        }
    }

    protected Map<String, Object> gatherProperties(ProvidedDependency providedDependency) {
        return removeSpecialApplicationProperties(mergeProperties(getPropertiesList(providedDependency)));
    }

    protected void addResource(Map<String, Object> env, Map<String, List<Object>> groups, Resource resource) {
        if (resource != null && !CloudModelBuilderUtil.isService(resource, getHandlerFactory().getPropertiesAccessor())) {
            addToGroupsOrEnvironment(env, groups, resource.getGroups(), resource.getName(), gatherProperties(resource));
        }
    }

    protected Map<String, Object> gatherProperties(Resource resource) {
        return removeSpecialApplicationProperties(mergeProperties(getPropertiesList(resource)));
    }

    public Map<String, Object> removeSpecialApplicationProperties(Map<String, Object> properties) {
        properties.keySet()
            .removeAll(SupportedParameters.APP_ATTRIBUTES);
        properties.keySet()
            .removeAll(SupportedParameters.APP_PROPS);
        properties.keySet()
            .removeAll(SupportedParameters.SPECIAL_MT_PROPS);
        return properties;
    }

    public Map<String, Object> removeSpecialServiceProperties(Map<String, Object> properties) {
        properties.keySet()
            .removeAll(SupportedParameters.SPECIAL_RT_PROPS);
        properties.keySet()
            .removeAll(SupportedParameters.SERVICE_PROPS);
        return properties;
    }

    protected void addToGroupsOrEnvironment(Map<String, Object> env, Map<String, List<Object>> groups, List<String> destinationGroups,
        String subgroupName, Map<String, Object> properties) {
        if (!destinationGroups.isEmpty()) {
            addToGroups(groups, destinationGroups, subgroupName, properties);
        } else {
            properties.forEach(env::put);
        }
    }

    protected void addToGroups(Map<String, List<Object>> groups, List<String> destinationGroups, String subgroupName,
        Map<String, Object> properties) {
        for (String group : destinationGroups) {
            addToGroup(groups, group, subgroupName, properties);
        }
    }

    protected void addToGroup(Map<String, List<Object>> groups, String group, String name, Map<String, Object> properties) {
        groups.computeIfAbsent(group, key -> new ArrayList<>())
            .add(createExtendedProperties(name, properties));
    }

    protected static Map<String, Object> createExtendedProperties(String name, Map<String, Object> properties) {
        Map<String, Object> extendedProperties = new TreeMap<>();
        extendedProperties.put(Constants.ATTR_NAME, name);
        extendedProperties.putAll(properties);
        return extendedProperties;
    }

    protected HandlerFactory getHandlerFactory() {
        return new HandlerFactory(MTA_MAJOR_VERSION);
    }

}
